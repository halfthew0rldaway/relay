/**
 * server.js — LocalLink Desktop transfer server.
 *
 * Responsibilities:
 *   1. Express HTTP server for file transfer protocol
 *   2. mDNS service publication + discovery
 *   3. Subnet scan fallback for device discovery
 *   4. REST API for the web UI frontend
 *   5. JSON-persisted settings, history, and pinned devices
 *
 * Architecture:
 *   - Android peers POST /handshake to notify us of an incoming transfer
 *   - Android peers GET  /file/:idx to pull staged files
 *   - The browser frontend polls /api/* for state and triggers uploads
 */

import express from 'express';
import { Bonjour } from 'bonjour-service';
import os from 'os';
import multer from 'multer';
import fs from 'fs';
import path from 'path';
import { exec } from 'child_process';
import { fileURLToPath } from 'url';
import { Readable } from 'stream';
import {
    loadHistory, saveHistoryEntry,
    loadSettings, saveSettings,
    loadPinned, savePinned
} from './server/storage.js';
import {
    parseHandshakePayload, sanitizeFileName,
    checkRateLimit, MAX_FILES_PER_TRANSFER
} from './server/validation.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/* ── Constants ── */

const PORT = 49152;
const DISCOVERY_SCAN_INTERVAL_MS = 8000;
const DISCOVERY_SCAN_TIMEOUT_MS = 500;
const DISCOVERY_SCAN_BATCH_SIZE = 48;
const IGNORED_INTERFACE_PREFIXES = [
    'docker', 'vbox', 'vmnet', 'br-', 'lxc',
    'virbr', 'tailscale', 'wg', 'tun', 'tap',
    'zt', 'ham', 'veth', 'lo'
];

/* ── Network Utilities ── */

function isIgnoredInterface(name = '') {
    const lower = name.toLowerCase();
    return IGNORED_INTERFACE_PREFIXES.some(prefix => lower.startsWith(prefix));
}

function isPrivateIpv4(address = '') {
    const parts = address.split('.').map(Number);
    if (parts.length !== 4 || parts.some(p => Number.isNaN(p) || p < 0 || p > 255)) return false;
    return parts[0] === 10
        || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31)
        || (parts[0] === 192 && parts[1] === 168);
}

function interfacePriority(name = '') {
    const lower = name.toLowerCase();
    if (lower.startsWith('wl') || lower.includes('wifi')) return 0;
    if (lower.startsWith('en') || lower.startsWith('eth')) return 1;
    return 2;
}

function getLanInterfaces() {
    let nets = {};
    try { nets = os.networkInterfaces(); } catch { return []; }

    const candidates = [];
    for (const [name, ifaces] of Object.entries(nets)) {
        if (isIgnoredInterface(name)) continue;
        for (const iface of ifaces || []) {
            if (iface.family !== 'IPv4' || iface.internal || !isPrivateIpv4(iface.address)) continue;
            candidates.push({ name, address: iface.address });
        }
    }
    candidates.sort((a, b) => {
        const diff = interfacePriority(a.name) - interfacePriority(b.name);
        return diff !== 0 ? diff : a.name.localeCompare(b.name);
    });
    return candidates;
}

function getLanIps() { return getLanInterfaces().map(i => i.address); }
function getLanIp() { return getLanIps()[0] || '127.0.0.1'; }
function getLanSubnets() {
    return [...new Set(getLanIps().map(ip => ip.split('.').slice(0, 3).join('.')).filter(Boolean))];
}

function normalizeRemoteIp(address = '') {
    if (!address) return 'unknown';
    if (address.startsWith('::ffff:')) return address.slice(7);
    const zoneIdx = address.indexOf('%');
    return zoneIdx === -1 ? address : address.slice(0, zoneIdx);
}

function inferDeviceType(name = '', platform = '') {
    const value = `${name} ${platform}`.toLowerCase();
    if (value.includes('mac') || value.includes('laptop')) return 'LAPTOP';
    if (value.includes('tablet') || value.includes('pad')) return 'TABLET';
    if (value.includes('desktop') || value.includes('pc') || value.includes('linux') || value.includes('windows')) return 'DESKTOP';
    return 'PHONE';
}

/* ── Application State ── */

const appSettings = loadSettings();
const bonjourState = { available: true, lastError: null };

let publishedService = null;
let discoveryBrowser = null;
let discoveryScanTimer = null;
let transferHistory = loadHistory();
let pinnedDevices = loadPinned();

const HOSTNAME = `LocalLink-${appSettings.deviceName.split(' ').slice(1).join(' ') || 'PC'} Desktop`;

const transferState = {
    phase: 'idle', direction: null,
    title: 'Ready to transfer',
    detail: 'Pick a nearby device, stage a file, or wait for an incoming transfer.',
    fileName: '', peerName: '',
    totalBytes: 0, transferredBytes: 0,
    startedAt: null, updatedAt: Date.now(), error: null
};

const discoveredDevices = new Map();
const mdnsDiscoveredDevices = new Map();
const probedDiscoveredDevices = new Map();
const learnedDevices = new Map();

let activeHostedFiles = [];
let pendingUploadRequests = [];
let activeDownloads = {};
let pendingIncoming = null;

/* ── Helper Functions ── */

function setTransferState(patch = {}) {
    Object.assign(transferState, patch, { updatedAt: Date.now() });
}

function clearTransferState() {
    setTransferState({
        phase: 'idle', direction: null,
        title: 'Ready to transfer',
        detail: 'Pick a nearby device, stage a file, or wait for an incoming transfer.',
        fileName: '', peerName: '',
        totalBytes: 0, transferredBytes: 0,
        startedAt: null, error: null
    });
}

function rebuildDiscoveredDevices() {
    discoveredDevices.clear();
    for (const [id, dev] of mdnsDiscoveredDevices) discoveredDevices.set(id, dev);
    for (const [id, dev] of probedDiscoveredDevices) if (!discoveredDevices.has(id)) discoveredDevices.set(id, dev);
    for (const [id, dev] of learnedDevices) if (!discoveredDevices.has(id)) discoveredDevices.set(id, dev);
}

function rememberDevice({ ipAddress, name, port = PORT, type = null }) {
    if (!ipAddress || ipAddress === 'unknown' || ipAddress.startsWith('127.')) return;
    learnedDevices.set(ipAddress, {
        id: ipAddress, name: name || ipAddress,
        ipAddress, port, type: type || inferDeviceType(name || ipAddress)
    });
    rebuildDiscoveredDevices();
}

function serviceToDevice(service) {
    if (!service?.name || service.name === appSettings.deviceName) return null;

    const addrs = service.addresses || [];
    const ipv4 = addrs.find(a => a && a.includes('.') && !a.startsWith('169.'))
        || (service.referer?.address?.includes('.') ? service.referer.address : null)
        || addrs[0] || service.txt?.ip || 'unknown';

    if (!ipv4 || ipv4 === 'unknown' || ipv4.startsWith('127.')) return null;

    return {
        id: ipv4, name: service.name,
        ipAddress: ipv4, port: service.port,
        type: inferDeviceType(service.name)
    };
}

function buildHealthPayload() {
    return {
        ok: true, service: 'locallink', platform: 'desktop',
        port: PORT, deviceName: appSettings.deviceName,
        ipAddress: getLanIp(),
        bonjourAvailable: bonjourState.available,
        bonjourError: bonjourState.lastError
    };
}

function handleMdnsError(error, context = 'mDNS') {
    bonjourState.available = false;
    bonjourState.lastError = error?.message || String(error) || 'Unknown error';
    console.error(`[${context}] ${bonjourState.lastError}`);
}

/* ── Discovery: mDNS + Subnet Scan ── */

async function fetchJsonWithTimeout(url, timeoutMs = DISCOVERY_SCAN_TIMEOUT_MS) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const response = await fetch(url, { signal: controller.signal, cache: 'no-store' });
        if (!response.ok) return null;
        return await response.json();
    } catch { return null; } finally { clearTimeout(timer); }
}

async function probeHealth(ip, ownIps) {
    if (!ip || ownIps.has(ip)) return null;
    const health = await fetchJsonWithTimeout(`http://${ip}:${PORT}/health`);
    if (!health?.ok) return null;
    if (health.service && health.service !== 'locallink') return null;
    const deviceName = String(health.deviceName || health.name || '').trim();
    if (!deviceName || deviceName === appSettings.deviceName) return null;
    return { id: ip, name: deviceName, ipAddress: ip, port: Number(health.port) || PORT, type: inferDeviceType(deviceName, health.platform) };
}

async function scanSubnet(subnetPrefix, ownIps) {
    const results = new Map();
    const candidates = [];
    for (let host = 1; host <= 254; host++) {
        const ip = `${subnetPrefix}.${host}`;
        if (!ownIps.has(ip)) candidates.push(ip);
    }
    for (let i = 0; i < candidates.length; i += DISCOVERY_SCAN_BATCH_SIZE) {
        const batch = candidates.slice(i, i + DISCOVERY_SCAN_BATCH_SIZE);
        const devices = await Promise.all(batch.map(ip => probeHealth(ip, ownIps)));
        for (const device of devices) { if (device) results.set(device.id, device); }
    }
    return results;
}

async function scanLocalNetwork() {
    const ownIps = new Set(getLanIps());
    const nextDevices = new Map();
    for (const subnet of getLanSubnets()) {
        const devices = await scanSubnet(subnet, ownIps);
        for (const [id, dev] of devices) nextDevices.set(id, dev);
    }
    probedDiscoveredDevices.clear();
    for (const [id, dev] of nextDevices) probedDiscoveredDevices.set(id, dev);
    rebuildDiscoveredDevices();
}

function startFallbackScanning() {
    stopFallbackScanning();
    scanLocalNetwork().catch(e => console.warn('[scan]', e?.message || e));
    discoveryScanTimer = setInterval(() => {
        scanLocalNetwork().catch(e => console.warn('[scan]', e?.message || e));
    }, DISCOVERY_SCAN_INTERVAL_MS);
}

function stopFallbackScanning() {
    if (!discoveryScanTimer) return;
    clearInterval(discoveryScanTimer);
    discoveryScanTimer = null;
}

function publishPresence() {
    if (!bonjourState.available) return;
    try {
        publishedService?.stop(() => { });
        publishedService = bonjour.publish({
            name: appSettings.deviceName, type: 'locallink',
            port: PORT, txt: { ip: getLanIp() }
        });
        publishedService.on('error', e => handleMdnsError(e, 'mDNS publish'));
    } catch (e) { handleMdnsError(e, 'mDNS publish'); }
}

function startDiscovery() {
    if (!bonjourState.available) return;
    try {
        discoveryBrowser?.stop();
        discoveryBrowser = bonjour.find({ type: 'locallink' });
        discoveryBrowser.on('up', service => {
            const dev = serviceToDevice(service);
            if (!dev) return;
            mdnsDiscoveredDevices.set(dev.id, dev);
            rebuildDiscoveredDevices();
            console.log(`[mDNS] Found: ${dev.name} @ ${dev.ipAddress}:${dev.port}`);
        });
        discoveryBrowser.on('down', service => {
            const dev = serviceToDevice(service);
            if (!dev) return;
            mdnsDiscoveredDevices.delete(dev.id);
            rebuildDiscoveredDevices();
            console.log(`[mDNS] Lost: ${dev.name}`);
        });
    } catch (e) { handleMdnsError(e, 'mDNS browse'); }
}

function shutdownBonjour() {
    stopFallbackScanning();
    try { discoveryBrowser?.stop(); } catch { }
    try { publishedService?.stop(() => { }); } catch { }
    try { bonjour.destroy(); } catch { }
}

/* ── Express Application ── */

const app = express();
const upload = multer({ dest: path.join(os.tmpdir(), 'locallink-tmp') });

const downloadsDir = appSettings.downloadPath;
if (!fs.existsSync(downloadsDir)) fs.mkdirSync(downloadsDir, { recursive: true });

app.use(express.static(path.join(__dirname, 'public')));

/* ── Health ── */

app.get('/api/health', (_req, res) => res.json(buildHealthPayload()));
app.get('/health', (_req, res) => res.json(buildHealthPayload()));

/* ── Settings ── */

app.get('/api/settings', (_req, res) => res.json(appSettings));

app.post('/api/settings', express.json(), (req, res) => {
    if (req.body.deviceName !== undefined) {
        appSettings.deviceName = String(req.body.deviceName).slice(0, 100);
        publishPresence();
    }
    if (req.body.downloadPath !== undefined) {
        appSettings.downloadPath = String(req.body.downloadPath);
    }
    if (req.body.autoAccept !== undefined) {
        appSettings.autoAccept = !!req.body.autoAccept;
    }
    saveSettings(appSettings);
    res.json({ success: true });
});

/* ── History ── */

app.get('/api/history', (_req, res) => res.json(transferHistory));

app.post('/api/openFile', express.json(), (req, res) => {
    const filePath = req.body.filePath;
    if (!filePath || typeof filePath !== 'string') {
        return res.json({ success: false, error: 'No file path provided' });
    }
    if (!fs.existsSync(filePath)) {
        return res.json({ success: false, error: 'File not found' });
    }
    const platform = os.platform();
    const cmd = platform === 'darwin' ? `open "${filePath}"`
        : platform === 'win32' ? `start "" "${filePath}"`
            : `xdg-open "${filePath}"`;
    exec(cmd, (err) => {
        if (err) return res.json({ success: false, error: err.message });
        res.json({ success: true });
    });
});

/* ── Pinned Devices ── */

app.get('/api/pinned', (_req, res) => res.json(pinnedDevices));

app.post('/api/pinned', express.json(), (req, res) => {
    if (Array.isArray(req.body.pinned)) {
        pinnedDevices = req.body.pinned;
        savePinned(pinnedDevices);
    }
    res.json({ success: true });
});

/* ── Devices ── */

app.get('/api/devices', (_req, res) => {
    res.json(Array.from(discoveredDevices.values()));
});

/* ── Transfer Status ── */

app.get('/api/status', (_req, res) => res.json(transferState));

/* ── File Hosting (Desktop as Sender) ── */

app.post('/api/host', express.json(), (req, res) => {
    const files = req.body.files;
    if (!Array.isArray(files) || files.length === 0) {
        return res.status(400).json({ error: 'No files provided' });
    }
    if (files.length > MAX_FILES_PER_TRANSFER) {
        return res.status(400).json({ error: `Too many files (max ${MAX_FILES_PER_TRANSFER})` });
    }

    activeHostedFiles = files.map((f, i) => ({
        name: sanitizeFileName(f.name),
        sizeBytes: f.size || 0,
        idx: i
    }));
    pendingUploadRequests = [];
    activeDownloads = {};

    const firstFile = activeHostedFiles[0];
    setTransferState({
        phase: 'staging', direction: 'send',
        title: 'File staged',
        detail: firstFile ? `Ready to send ${firstFile.name}.` : 'Files ready to send.',
        fileName: firstFile?.name || '',
        totalBytes: firstFile?.sizeBytes || 0,
        transferredBytes: 0, startedAt: null, error: null
    });
    res.json({ success: true, count: activeHostedFiles.length });
});

/* ── Handshake: Desktop → Android ── */

app.post('/api/sendHandshake', express.json(), async (req, res) => {
    const { peerIp } = req.body;
    if (!peerIp) return res.status(400).json({ error: 'Missing peerIp' });

    const filesStr = activeHostedFiles.map(f => `${f.name}:${f.sizeBytes}`).join(';;;');
    const payload = `${appSettings.deviceName}|||${filesStr}`;
    const peerName = Array.from(discoveredDevices.values()).find(d => d.ipAddress === peerIp)?.name || peerIp;

    setTransferState({
        phase: 'connecting', direction: 'send',
        title: 'Contacting receiver',
        detail: `Sending a handshake to ${peerName}.`,
        fileName: activeHostedFiles[0]?.name || '',
        peerName, totalBytes: activeHostedFiles[0]?.sizeBytes || 0,
        transferredBytes: 0, startedAt: Date.now(), error: null
    });

    try {
        const resp = await fetch(`http://${peerIp}:${PORT}/handshake`, {
            method: 'POST', body: payload,
            headers: { 'Content-Length': Buffer.byteLength(payload) }
        });
        const success = resp.status === 200;
        setTransferState(success
            ? { phase: 'waiting', direction: 'send', title: 'Waiting for receiver', detail: `${peerName} can accept the transfer now.`, peerName }
            : { phase: 'failed', direction: 'send', title: 'Handshake failed', detail: `${peerName} did not accept the handshake.`, peerName, error: `HTTP ${resp.status}` }
        );
        res.json({ success });
    } catch (e) {
        setTransferState({ phase: 'failed', direction: 'send', title: 'Unable to reach receiver', detail: e.message || 'Connection failed.', peerName, error: e.message });
        res.json({ success: false, error: e.message });
    }
});

/* ── Upload Pipeline (Desktop → Browser → Android) ── */

app.get('/api/poll_uploads', (_req, res) => {
    if (pendingUploadRequests.length > 0) {
        res.json({ triggerUploadIdx: pendingUploadRequests.shift() });
    } else {
        res.json({ triggerUploadIdx: null });
    }
});

app.post('/api/upload_stream/:idx', (req, res) => {
    const idx = req.params.idx;
    const ctx = activeDownloads[idx];

    if (!ctx) return res.status(404).send('Not waiting for this file.');

    const pf = activeHostedFiles[idx];
    let transferred = 0;

    setTransferState({
        phase: 'sending', direction: 'send', title: 'Sending file',
        detail: `Streaming ${pf?.name || 'file'} to ${ctx.peerName}.`,
        fileName: pf?.name || '', peerName: ctx.peerName,
        totalBytes: pf?.sizeBytes || 0, transferredBytes: 0,
        startedAt: transferState.startedAt || Date.now(), error: null
    });

    req.on('data', chunk => {
        transferred += chunk.length;
        setTransferState({
            phase: 'sending', direction: 'send', title: 'Sending file',
            detail: `Streaming ${pf?.name || 'file'} to ${ctx.peerName}.`,
            fileName: pf?.name || '', peerName: ctx.peerName,
            totalBytes: pf?.sizeBytes || 0, transferredBytes: transferred
        });
    });

    req.pipe(ctx.res);

    req.on('end', () => {
        res.json({ success: true });
        delete activeDownloads[idx];
        if (pf) {
            const entry = { fileName: pf.name, sizeBytes: pf.sizeBytes, peerName: ctx.peerName, direction: 'SENT', status: 'SUCCESS', timestamp: Date.now() };
            transferHistory.push(entry);
            saveHistoryEntry(entry);
        }
        setTransferState({
            phase: 'done', direction: 'send', title: 'Transfer complete',
            detail: `${pf?.name || 'File'} was sent to ${ctx.peerName}.`,
            fileName: pf?.name || '', peerName: ctx.peerName,
            totalBytes: pf?.sizeBytes || 0, transferredBytes: pf?.sizeBytes || transferred
        });
    });

    req.on('error', error => {
        setTransferState({
            phase: 'failed', direction: 'send', title: 'Send failed',
            detail: error.message || 'Upload stream interrupted.',
            fileName: pf?.name || '', peerName: ctx.peerName,
            totalBytes: pf?.sizeBytes || 0, transferredBytes: transferred,
            error: error.message || 'Upload stream error'
        });
    });
});

/* ── Incoming Transfers (Android → Desktop) ── */

app.get('/api/incoming', (_req, res) => res.json(pendingIncoming));

app.post('/api/accept', async (_req, res) => {
    if (!pendingIncoming) return res.json({ success: false });
    const inc = pendingIncoming;
    pendingIncoming = null;

    setTransferState({
        phase: 'receiving', direction: 'receive', title: 'Starting download',
        detail: `Preparing to receive ${inc.files[0]?.name || inc.files.length + ' file(s)'} from ${inc.senderName}.`,
        fileName: inc.files[0]?.name || '', peerName: inc.senderName,
        totalBytes: inc.files[0]?.sizeBytes || 0, transferredBytes: 0,
        startedAt: Date.now(), error: null
    });
    res.json({ success: true });
    executeDownload(inc);
});

app.post('/api/reject', (_req, res) => {
    pendingIncoming = null;
    clearTransferState();
    res.json({ success: true });
});

async function executeDownload(incoming) {
    const { senderIp, files, senderName } = incoming;
    for (let i = 0; i < files.length; i++) {
        try {
            const resp = await fetch(`http://${senderIp}:${PORT}/file/${i}`);
            if (!resp.ok || !resp.body) throw new Error(`Peer responded with ${resp.status}`);

            if (!fs.existsSync(appSettings.downloadPath)) {
                fs.mkdirSync(appSettings.downloadPath, { recursive: true });
            }

            const safeName = sanitizeFileName(files[i].name);
            const dest = fs.createWriteStream(path.join(appSettings.downloadPath, safeName));
            let transferred = 0;

            setTransferState({
                phase: 'receiving', direction: 'receive', title: 'Downloading file',
                detail: `Receiving ${safeName} from ${senderName}.`,
                fileName: safeName, peerName: senderName,
                totalBytes: files[i].sizeBytes || Number(resp.headers.get('content-length')) || 0,
                transferredBytes: 0, startedAt: Date.now(), error: null
            });

            await new Promise((resolve, reject) => {
                const nodeStream = Readable.fromWeb(resp.body);
                nodeStream.on('data', chunk => {
                    transferred += chunk.length;
                    setTransferState({
                        phase: 'receiving', direction: 'receive', title: 'Downloading file',
                        detail: `Receiving ${safeName} from ${senderName}.`,
                        fileName: safeName, peerName: senderName,
                        totalBytes: files[i].sizeBytes || Number(resp.headers.get('content-length')) || 0,
                        transferredBytes: transferred
                    });
                });
                nodeStream.pipe(dest);
                dest.on('finish', resolve);
                nodeStream.on('error', reject);
                dest.on('error', reject);
            });

            const entry = { fileName: safeName, sizeBytes: files[i].sizeBytes, peerName: senderName, direction: 'RECEIVED', status: 'SUCCESS', timestamp: Date.now(), filePath: path.join(appSettings.downloadPath, safeName) };
            transferHistory.push(entry);
            saveHistoryEntry(entry);

            setTransferState({
                phase: 'done', direction: 'receive', title: 'Download complete',
                detail: `${safeName} was saved to ${appSettings.downloadPath}.`,
                fileName: safeName, peerName: senderName,
                totalBytes: files[i].sizeBytes, transferredBytes: files[i].sizeBytes
            });
        } catch (e) {
            console.error('Failed to fetch file', e);
            setTransferState({
                phase: 'failed', direction: 'receive', title: 'Download failed',
                detail: e.message || 'Unable to fetch the incoming file.',
                fileName: files[i]?.name || '', peerName: senderName,
                totalBytes: files[i]?.sizeBytes || 0, error: e.message || 'Download failed'
            });
        }
    }
}

/* ── Protocol Endpoint: Android Handshake ── */

app.post('/handshake', express.text({ type: '*/*' }), (req, res) => {
    try {
        const peerIp = normalizeRemoteIp(req.socket.remoteAddress);

        // Rate limit per IP
        if (!checkRateLimit(peerIp)) {
            return res.status(429).send('Rate limited');
        }

        const parsed = parseHandshakePayload(req.body);
        if (parsed.error) {
            return res.status(400).send(parsed.error);
        }

        const incoming = {
            senderName: parsed.senderName,
            senderIp: peerIp,
            files: parsed.files
        };

        rememberDevice({ ipAddress: incoming.senderIp, name: incoming.senderName, port: PORT });

        if (appSettings.autoAccept) {
            setTransferState({
                phase: 'receiving', direction: 'receive', title: 'Incoming transfer',
                detail: `${incoming.senderName} started sending ${parsed.files[0]?.name || parsed.files.length + ' file(s)'}.`,
                fileName: parsed.files[0]?.name || '', peerName: incoming.senderName,
                totalBytes: parsed.files[0]?.sizeBytes || 0, transferredBytes: 0,
                startedAt: Date.now(), error: null
            });
            executeDownload(incoming);
        } else {
            pendingIncoming = incoming;
            setTransferState({
                phase: 'incoming', direction: 'receive', title: 'Incoming transfer request',
                detail: `${incoming.senderName} wants to send ${parsed.files.map(f => f.name).join(', ')}.`,
                fileName: parsed.files[0]?.name || '', peerName: incoming.senderName,
                totalBytes: parsed.files[0]?.sizeBytes || 0, transferredBytes: 0,
                startedAt: Date.now(), error: null
            });
        }

        res.sendStatus(200);
    } catch (e) {
        console.error('[handshake] Error:', e);
        res.sendStatus(500);
    }
});

/* ── Protocol Endpoint: File Serving ── */

app.get('/file/:idx', (req, res) => {
    const idx = parseInt(req.params.idx, 10);
    const pf = activeHostedFiles[idx];
    if (!pf) return res.sendStatus(404);

    res.setHeader('Content-Length', pf.sizeBytes);
    res.setHeader('Content-Type', 'application/octet-stream');

    const peerIp = normalizeRemoteIp(req.socket.remoteAddress);
    let matchedName = peerIp;
    for (const dev of discoveredDevices.values()) {
        if (dev.ipAddress === peerIp) matchedName = dev.name;
    }

    activeDownloads[idx] = { res, peerName: matchedName };
    setTransferState({
        phase: 'sending', direction: 'send', title: 'Receiver connected',
        detail: `${matchedName} is pulling ${pf.name}.`,
        fileName: pf.name, peerName: matchedName,
        totalBytes: pf.sizeBytes, transferredBytes: 0,
        startedAt: transferState.startedAt || Date.now(), error: null
    });
    pendingUploadRequests.push(idx);
});

/* ── Start Server ── */

const bonjour = new Bonjour({ multicast: true, loopback: true }, e => handleMdnsError(e, 'mDNS'));
bonjour.server.mdns.on('error', e => handleMdnsError(e, 'mDNS socket'));
bonjour.server.mdns.on('warning', w => console.warn('[mDNS warning]', w?.message || w));

const server = app.listen(PORT, '0.0.0.0', () => {
    console.log(`LocalLink Desktop running at http://localhost:${PORT}`);
    console.log(`[mDNS] Advertising on LAN IP: ${getLanIp()}`);
    publishPresence();
    startDiscovery();
    startFallbackScanning();
});

server.on('error', e => {
    if (e.code === 'EADDRINUSE') {
        console.log(`Port ${PORT} already in use. LocalLink may already be running.`);
    } else { console.error(e); }
});

server.on('close', shutdownBonjour);
process.on('SIGINT', shutdownBonjour);
process.on('SIGTERM', shutdownBonjour);

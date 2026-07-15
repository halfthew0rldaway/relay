/**
 * storage.js — Lightweight JSON file persistence for Relay.
 *
 * Data directory: ~/.cache/relay/
 * Stores: history, settings, pinned devices as JSON files.
 * All operations are synchronous (small files, local disk).
 */

import fs from 'fs';
import path from 'path';
import os from 'os';

const DATA_DIR = path.join(os.homedir(), '.cache', 'relay');

function ensureDir() {
    if (!fs.existsSync(DATA_DIR)) {
        fs.mkdirSync(DATA_DIR, { recursive: true, mode: 0o700 });
    }
}

function readJson(filename) {
    const filePath = path.join(DATA_DIR, filename);
    try {
        if (!fs.existsSync(filePath)) return null;
        return JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch {
        return null;
    }
}

function writeJson(filename, data) {
    ensureDir();
    const filePath = path.join(DATA_DIR, filename);
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf8');
}

/* ── Transfer History ── */

const HISTORY_FILE = 'history.json';
const MAX_HISTORY = 200;

export function loadHistory() {
    return readJson(HISTORY_FILE) || [];
}

export function saveHistoryEntry(entry) {
    const history = loadHistory();
    history.push({
        ...entry,
        timestamp: entry.timestamp || Date.now()
    });
    // Keep only last MAX_HISTORY entries
    if (history.length > MAX_HISTORY) {
        history.splice(0, history.length - MAX_HISTORY);
    }
    writeJson(HISTORY_FILE, history);
    return history;
}

/* ── Settings ── */

const SETTINGS_FILE = 'settings.json';
const DEFAULT_SETTINGS = {
    deviceName: `Relay-${os.hostname().split('.')[0] || 'PC'} Desktop`,
    downloadPath: path.join(os.homedir(), 'Downloads', 'Relay'),
    autoAccept: false
};

export function loadSettings() {
    const saved = readJson(SETTINGS_FILE);
    return { ...DEFAULT_SETTINGS, ...saved };
}

export function saveSettings(settings) {
    const current = loadSettings();
    const merged = { ...current, ...settings };
    writeJson(SETTINGS_FILE, merged);
    return merged;
}

/* ── Pinned Devices ── */

const PINNED_FILE = 'pinned.json';

export function loadPinned() {
    return readJson(PINNED_FILE) || [];
}

export function savePinned(pinned) {
    writeJson(PINNED_FILE, pinned);
    return pinned;
}

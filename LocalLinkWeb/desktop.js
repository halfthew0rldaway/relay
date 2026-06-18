import { app, BrowserWindow } from 'electron';
import path from 'path';
import { fileURLToPath } from 'url';
import { spawn } from 'child_process';
import fs from 'fs';
import os from 'os';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SERVER_PORT = 49152;
const SERVER_URL = `http://127.0.0.1:${SERVER_PORT}`;
const SERVER_HEALTH_URL = `${SERVER_URL}/api/health`;
const SERVER_BOOT_TIMEOUT_MS = 15000;
const SERVER_LOG_LIMIT = 80;

const serverScript = path.join(__dirname, 'server.js');
const recentServerLogs = [];
const runtimeRoot = path.join(os.homedir(), '.cache', 'locallink-runtime');
const tempRoot = path.join(runtimeRoot, 'tmp');

fs.mkdirSync(tempRoot, { recursive: true, mode: 0o700 });
process.env.TMPDIR = tempRoot;
process.env.TMP = tempRoot;
process.env.TEMP = tempRoot;

let win = null;
let serverProcess = null;
let serverExited = false;

if (process.env.XDG_SESSION_TYPE === 'wayland' || process.env.WAYLAND_DISPLAY) {
  app.commandLine.appendSwitch('enable-features', 'UseOzonePlatform');
  app.commandLine.appendSwitch('ozone-platform-hint', 'auto');
}
app.commandLine.appendSwitch('no-sandbox');
app.commandLine.appendSwitch('no-zygote');
app.commandLine.appendSwitch('disable-dev-shm-usage');
app.disableHardwareAcceleration();

function pushServerLog(line) {
  if (!line) return;
  recentServerLogs.push(line);
  if (recentServerLogs.length > SERVER_LOG_LIMIT) recentServerLogs.shift();
  console.log(`[server] ${line}`);
}

function attachStream(stream, prefix) {
  if (!stream) return;
  let buffer = '';

  stream.setEncoding('utf8');
  stream.on('data', chunk => {
    buffer += chunk;
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() || '';
    lines.map(line => line.trim()).filter(Boolean).forEach(line => pushServerLog(`${prefix}${line}`));
  });
  stream.on('end', () => {
    const line = buffer.trim();
    if (line) pushServerLog(`${prefix}${line}`);
  });
}

function htmlEscape(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function renderBootError(details) {
  const logs = recentServerLogs.length
    ? recentServerLogs.map(line => htmlEscape(line)).join('\n')
    : 'No server logs captured.';

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>LocalLink Desktop Error</title>
  <style>
    :root {
      --bg: #eef2ff;
      --surface: rgba(255,255,255,0.92);
      --line: #dbe3ff;
      --text: #0f172a;
      --muted: #475569;
      --accent: #2563eb;
      --danger: #dc2626;
      --shadow: 0 18px 55px rgba(37, 99, 235, 0.12);
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: system-ui, sans-serif;
      background:
        radial-gradient(circle at top left, rgba(96, 165, 250, 0.22), transparent 30%),
        linear-gradient(180deg, #f8faff 0%, var(--bg) 100%);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }
    .card {
      width: min(900px, 100%);
      background: var(--surface);
      backdrop-filter: blur(18px);
      border: 1px solid rgba(255,255,255,0.7);
      border-radius: 24px;
      box-shadow: var(--shadow);
      padding: 28px;
    }
    .eyebrow {
      color: var(--accent);
      font-size: 12px;
      font-weight: 800;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    h1 {
      margin: 10px 0 8px;
      font-size: 30px;
      line-height: 1.1;
    }
    p {
      margin: 0 0 18px;
      color: var(--muted);
      line-height: 1.55;
    }
    .error {
      margin: 18px 0;
      padding: 14px 16px;
      border-radius: 16px;
      background: rgba(220, 38, 38, 0.08);
      color: var(--danger);
      font-weight: 600;
      white-space: pre-wrap;
    }
    .grid {
      display: grid;
      gap: 18px;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      margin: 22px 0;
    }
    .tile {
      padding: 18px;
      background: rgba(255,255,255,0.72);
      border: 1px solid var(--line);
      border-radius: 18px;
    }
    .tile strong {
      display: block;
      margin-bottom: 6px;
      font-size: 15px;
    }
    .tile span {
      color: var(--muted);
      font-size: 14px;
      line-height: 1.5;
    }
    pre {
      margin: 0;
      padding: 18px;
      border-radius: 18px;
      background: #0f172a;
      color: #dbeafe;
      overflow: auto;
      min-height: 200px;
      font-size: 12px;
      line-height: 1.5;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .footer {
      margin-top: 18px;
      color: var(--muted);
      font-size: 13px;
    }
  </style>
</head>
<body>
  <main class="card">
    <div class="eyebrow">Desktop Startup Failed</div>
    <h1>LocalLink could not start its local server</h1>
    <p>The window stayed blank because the Electron shell was waiting for the local transfer server at <code>${htmlEscape(SERVER_URL)}</code>, but it never became ready.</p>
    <div class="error">${htmlEscape(details)}</div>
    <section class="grid">
      <div class="tile">
        <strong>Most likely causes</strong>
        <span>Another process is already using port ${SERVER_PORT}, or the previously installed broken version is still running in the background.</span>
      </div>
      <div class="tile">
        <strong>Next checks</strong>
        <span>Close all LocalLink windows, kill any old <code>locallink-web</code> process, then reinstall the fresh build from this repo.</span>
      </div>
      <div class="tile">
        <strong>Useful command</strong>
        <span><code>ss -ltnp | grep ${SERVER_PORT}</code> will show what is holding the port.</span>
      </div>
    </section>
    <pre>${logs}</pre>
    <div class="footer">After reinstalling, this page should disappear and the normal LocalLink UI should load instead.</div>
  </main>
</body>
</html>`;
}

function stopServer() {
  if (!serverProcess || serverProcess.killed) return;
  serverProcess.kill('SIGTERM');
}

function startServer() {
  if (serverProcess && !serverExited) return;

  serverExited = false;
  recentServerLogs.length = 0;

  serverProcess = spawn(process.execPath, [serverScript], {
    cwd: __dirname,
    env: {
      ...process.env,
      ELECTRON_RUN_AS_NODE: '1'
    },
    stdio: ['ignore', 'pipe', 'pipe']
  });

  attachStream(serverProcess.stdout, '');
  attachStream(serverProcess.stderr, 'ERR: ');

  serverProcess.on('exit', (code, signal) => {
    serverExited = true;
    pushServerLog(`Server exited with code ${code ?? 'null'} signal ${signal ?? 'none'}`);
  });

  serverProcess.on('error', error => {
    serverExited = true;
    pushServerLog(`ERR: Failed to spawn server: ${error.message}`);
  });
}

async function waitForServer(timeoutMs = SERVER_BOOT_TIMEOUT_MS) {
  const startedAt = Date.now();
  let lastError = 'Server did not respond.';

  while (Date.now() - startedAt < timeoutMs) {
    try {
      const response = await fetch(SERVER_HEALTH_URL, { cache: 'no-store' });
      if (response.ok) return true;
      lastError = `Health check returned ${response.status}`;
    } catch (error) {
      lastError = error.message;
    }

    if (serverExited) {
      const portInUse = recentServerLogs.some(line => line.includes(`Port ${SERVER_PORT} is already in use`));
      if (!portInUse) {
        lastError = recentServerLogs.at(-1) || 'Server process exited before becoming ready.';
        break;
      }
    }

    await new Promise(resolve => setTimeout(resolve, 350));
  }

  throw new Error(lastError);
}

async function loadAppWindow() {
  startServer();

  try {
    await waitForServer();
    if (!win || win.isDestroyed()) return;
    await win.loadURL(SERVER_URL);
  } catch (error) {
    if (!win || win.isDestroyed()) return;
    try {
      await win.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(renderBootError(error.message))}`);
    } catch (loadError) {
      const details = [
        `Startup error: ${error.message}`,
        `Error page load failed: ${loadError.message}`
      ].join('\n');
      await win.loadURL(`data:text/plain;charset=utf-8,${encodeURIComponent(details)}`);
    }
  }
}

function createWindow() {
  win = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 980,
    minHeight: 680,
    backgroundColor: '#F0F2FF',
    autoHideMenuBar: true,
    titleBarStyle: 'hiddenInset',
    icon: path.join(__dirname, 'public/favicon.ico'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  win.on('closed', () => {
    win = null;
  });

  loadAppWindow();
}

const gotTheLock = app.requestSingleInstanceLock();

if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      if (win.isMinimized()) win.restore();
      win.focus();
    }
  });

  app.whenReady().then(createWindow);

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });

  app.on('before-quit', stopServer);
  process.on('SIGINT', stopServer);
  process.on('SIGTERM', stopServer);

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
}

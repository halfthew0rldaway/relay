/**
* app.js — Relay Web frontend.
*
* Handles:
*   - Device discovery display
*   - File staging (drag/drop, browse)
*   - Send flow (host → handshake → upload)
*   - Receive flow (poll incoming → accept → progress)
*   - Transfer status display
*   - History and settings
*   - Device pinning
*/

'use strict';

document.addEventListener('DOMContentLoaded', () => {

    /* ── Helpers ── */

    function q(sel, ctx = document) { return ctx.querySelector(sel); }

    function fmt(b) {
        if (!Number.isFinite(b) || b <= 0) return '0 B';
        if (b >= 1e9) return (b / 1e9).toFixed(1) + ' GB';
        if (b >= 1e6) return (b / 1e6).toFixed(1) + ' MB';
        if (b >= 1024) return Math.round(b / 1024) + ' KB';
        return b + ' B';
    }

    let _toastTimer;
    function toast(msg, ms = 3000) {
        const el = q('#toast');
        el.textContent = msg;
        el.classList.remove('hidden');
        clearTimeout(_toastTimer);
        _toastTimer = setTimeout(() => el.classList.add('hidden'), ms);
    }

    /* ── Welcome Modal ── */

    const welcomeEl = q('#welcome-modal');

    // Global Lottie Error Handler: removes broken animations from the DOM flow to prevent blank gaps
    window.lottiesBroken = false;
    document.querySelectorAll('lottie-player').forEach(player => {
        const wrap = player.parentElement && player.parentElement.id.includes('wrap') ? player.parentElement : player;

        const collapse = () => {
            window.lottiesBroken = true;
            wrap.classList.add('hidden');
            wrap.style.display = 'none'; // Overrides any inline dimensions and flex gaps
        };

        player.addEventListener('error', collapse);

        let loaded = false;
        player.addEventListener('ready', () => { loaded = true; });
        player.addEventListener('load', () => { loaded = true; });

        // If animation fails to start within 1.5s, assume broken and collapse naturally
        setTimeout(() => {
            if (!loaded) collapse();
        }, 1500);
    });

    welcomeEl.classList.remove('hidden');

    q('#btn-start').onclick = () => {
        welcomeEl.classList.add('modal-hidden');
        welcomeEl.style.opacity = '0';
        setTimeout(() => {
            welcomeEl.classList.add('hidden');
            welcomeEl.classList.remove('modal-hidden');
            welcomeEl.style.opacity = '';
        }, 300);
    };

    q('#btn-welcome-how').onclick = () => {
        // Hide welcome, open how-it-works
        welcomeEl.classList.add('modal-hidden');
        welcomeEl.style.opacity = '0';
        setTimeout(() => {
            welcomeEl.classList.add('hidden');
            welcomeEl.classList.remove('modal-hidden');
            welcomeEl.style.opacity = '';
            q('#how-modal').classList.remove('hidden');
        }, 280);
    };

    /* ── How it Works Modal ── */
    const howModal = q('#how-modal');
    if (q('#btn-how')) {
        q('#btn-how').onclick = () => {
            howModal.classList.remove('hidden');
        };
    }
    if (q('#btn-close-how')) {
        q('#btn-close-how').onclick = () => {
            howModal.classList.add('modal-hidden');
            howModal.style.opacity = '0';
            setTimeout(() => {
                howModal.classList.add('hidden');
                howModal.classList.remove('modal-hidden');
                howModal.style.opacity = '';
            }, 300);
        };
    }

    /* ── Tab Navigation ── */

    const navBtns = document.querySelectorAll('.nav-btn');
    const tabs = document.querySelectorAll('.tab');

    navBtns.forEach(btn => btn.addEventListener('click', () => {
        navBtns.forEach(b => b.classList.remove('active'));
        tabs.forEach(t => t.classList.add('hidden'));
        btn.classList.add('active');
        q('#' + btn.dataset.tab).classList.remove('hidden');
        if (btn.dataset.tab === 'tab-history') loadHistory();
        if (btn.dataset.tab === 'tab-settings') loadSettings();
    }));

    /* ── Transfer Status Display ── */

    const statusNodes = {
        kicker: q('#status-kicker'),
        badge: q('#status-badge'),
        title: q('#status-title'),
        detail: q('#status-detail'),
        peer: q('#status-peer'),
        file: q('#status-file'),
        size: q('#status-size'),
        phaseLabel: q('#status-phase-label'),
        progressLabel: q('#status-progress-label'),
        progressFill: q('#status-progress-fill'),
        progressWrap: q('.status-progress-wrap'),
        heroTitle: q('#files-hero-title'),
        heroSub: q('#files-hero-sub'),
        lottieWrap: q('#status-lottie-wrap'),
        lottiePlayer: q('#status-lottie')
    };

    let clientTransferState = null;
    let lastPhase = 'idle';

    function formatPercent(transferred, total) {
        if (!total) return '0%';
        return `${Math.max(0, Math.min(100, Math.round((transferred / total) * 100)))}%`;
    }

    function statusVisualPhase(phase = 'idle', direction = null) {
        if (phase === 'done' || phase === 'failed' || phase === 'idle') return phase;
        if (phase === 'incoming' || phase === 'receiving') return 'receive';
        if (direction === 'receive') return 'receive';
        if (phase === 'connecting') return 'connecting';
        if (phase === 'waiting') return 'waiting';
        return 'send';
    }

    function renderTransferState(state) {
        const vp = statusVisualPhase(state.phase, state.direction);
        const total = Number(state.totalBytes || 0);
        const transferred = Math.max(0, Number(state.transferredBytes || 0));
        const progressText = state.phase === 'idle' ? '0%' : state.phase === 'done' ? '100%' : formatPercent(transferred, total);
        const progressWidth = state.phase === 'done' ? 100 : total ? Math.max(3, Math.round((transferred / total) * 100)) : 8;

        statusNodes.kicker.textContent = state.direction === 'receive' ? 'INCOMING FLOW' : state.direction === 'send' ? 'OUTGOING FLOW' : 'SYSTEM READY';
        statusNodes.badge.className = `status-badge ${vp}`;
        statusNodes.badge.textContent = {
            idle: 'Idle', incoming: 'Incoming', connecting: 'Connecting',
            waiting: 'Waiting', receiving: 'Receiving', sending: 'Sending',
            done: 'Done', failed: 'Failed'
        }[state.phase] || 'Active';

        statusNodes.title.textContent = state.title || 'Ready to transfer';
        statusNodes.detail.textContent = (state.phase === 'failed' && state.error)
            ? (state.detail ? `${state.detail} (${state.error})` : state.error)
            : (state.detail || 'Pick a nearby device, stage a file, or wait for an incoming transfer.');
        statusNodes.phaseLabel.textContent = state.phase === 'idle' ? 'Standing by' : state.phase.replace(/_/g, ' ');
        statusNodes.progressLabel.textContent = progressText;
        statusNodes.progressFill.style.width = `${progressWidth}%`;
        statusNodes.progressFill.style.background = state.phase === 'failed'
            ? 'linear-gradient(90deg, #BA1A1A, #DC2626)'
            : state.direction === 'receive'
                ? 'linear-gradient(90deg, #059669, #10B981)'
                : 'linear-gradient(90deg, #00488D, #005FB8)';

        statusNodes.peer.textContent = state.peerName ? `Peer: ${state.peerName}` : '';
        statusNodes.file.textContent = state.fileName ? `File: ${state.fileName}` : '';
        statusNodes.size.textContent = total ? `Size: ${fmt(total)}` : '';
        statusNodes.peer.classList.toggle('hidden', !state.peerName);
        statusNodes.file.classList.toggle('hidden', !state.fileName);
        statusNodes.size.classList.toggle('hidden', !total);

        statusNodes.heroTitle.textContent = state.phase === 'idle'
            ? 'Stage a file, then send with confidence'
            : state.title || 'Transfer in progress';
        statusNodes.heroSub.textContent = state.phase === 'idle'
            ? 'You\'ll see every step here: staged, contacting receiver, sending, downloading, and finished.'
            : state.detail || 'Live transfer updates will appear here.';

        if (state.phase === 'idle') {
            statusNodes.progressWrap.style.display = 'none';
        } else {
            statusNodes.progressWrap.style.display = 'flex';
        }

        const LOTTIE_URLS = {
            connecting: 'lottie_loading.json',
            waiting: 'lottie_loading.json',
            staging: 'lottie_loading.json',
            sending: 'lottie_transfer.json',
            receiving: 'lottie_transfer.json',
            done: 'lottie_success.json',
            failed: 'lottie_error.json'
        };

        if (state.phase === 'idle' || window.lottiesBroken) {
            statusNodes.lottieWrap.style.display = 'none';
        } else {
            statusNodes.lottieWrap.classList.remove('hidden');
            statusNodes.lottieWrap.style.display = 'flex';
            const targetUrl = LOTTIE_URLS[state.phase] || LOTTIE_URLS.sending;
            if (statusNodes.lottiePlayer.getAttribute('src') !== targetUrl) {
                statusNodes.lottiePlayer.setAttribute('src', targetUrl);
                if (state.phase === 'done' || state.phase === 'failed') {
                    statusNodes.lottiePlayer.removeAttribute('loop');
                } else {
                    statusNodes.lottiePlayer.setAttribute('loop', '');
                }
            }
        }

        // Fire celebration overlay exactly once when phase transitions to 'done'
        const prevPhase = lastPhase;
        lastPhase = state.phase || 'idle';

        if (state.phase === 'done' && prevPhase !== 'done') {
            // Don't pop up while welcome modal is still visible
            const welcomeVisible = !q('#welcome-modal').classList.contains('hidden');
            if (welcomeVisible) return;

            const overlay = q('#done-overlay');
            const msg = q('#done-card-msg');
            if (msg) {
                msg.textContent = state.fileName
                    ? `"${state.fileName}" landed safely. Zero clouds harmed.`
                    : 'File delivered. Your router finally did something useful.';
            }
            if (overlay) {
                overlay.classList.remove('hidden');
                const confetti = q('#done-confetti');
                if (confetti) confetti.setAttribute('src', 'lottie_done_celebration.json');
                // Auto-dismiss after 6s
                const autoTimer = setTimeout(() => overlay.classList.add('hidden'), 6000);
                const dismissBtn = q('#btn-dismiss-done');
                if (dismissBtn) {
                    dismissBtn.onclick = () => {
                        clearTimeout(autoTimer);
                        overlay.classList.add('hidden');
                    };
                }
            }
        }
    }

    /* ── Status Polling ── */

    async function pollTransferStatus() {
        try {
            const serverState = await fetch('/api/status', { cache: 'no-store' }).then(r => r.json());
            const effective = clientTransferState && clientTransferState.phase !== 'done' && clientTransferState.phase !== 'failed' && clientTransferState.phase !== 'idle'
                ? { ...serverState, ...clientTransferState }
                : serverState;
            if ((serverState.phase === 'done' || serverState.phase === 'failed' || serverState.phase === 'idle') && clientTransferState) {
                clientTransferState = null;
            }
            renderTransferState(effective);

            if (!recModal.classList.contains('hidden') && receiveModalMode === 'progress') {
                renderReceiveModalProgress(effective);
                if (effective.phase === 'done') {
                    q('#btn-accept').textContent = 'Done';
                    q('#btn-accept').disabled = false;
                    q('#btn-reject').classList.add('hidden');
                } else if (effective.phase === 'failed') {
                    q('#btn-accept').textContent = 'Close';
                    q('#btn-accept').disabled = false;
                    q('#btn-reject').classList.add('hidden');
                }
            }
        } catch { }
    }

    let pollTimer = null;
    async function schedulePoll() {
        try {
            await pollTransferStatus();
        } catch { }

        let interval = 1500; // default for idle
        if (clientTransferState && clientTransferState.phase !== 'done' && clientTransferState.phase !== 'failed' && clientTransferState.phase !== 'idle') {
            interval = 300;
        } else if (lastPhase !== 'idle' && lastPhase !== 'done' && lastPhase !== 'failed') {
            interval = 300;
        }
        pollTimer = setTimeout(schedulePoll, interval);
    }
    schedulePoll();

    function triggerImmediatePoll() {
        if (pollTimer) clearTimeout(pollTimer);
        schedulePoll();
    }

    renderTransferState({
        phase: 'idle', direction: null,
        title: 'Ready to transfer',
        detail: 'Pick a nearby device, stage a file, or wait for an incoming transfer.',
        fileName: '', peerName: '', totalBytes: 0, transferredBytes: 0
    });

    /* ── Device Discovery ── */

    let lastDevices = [];
    let pinned = JSON.parse(localStorage.getItem('ll_pin') || 'null');
    let manualTarget = null;

    function isSameTarget(a, b) {
        if (!a || !b) return false;
        return a.id === b.id || (a.ipAddress && a.ipAddress === b.ipAddress) || a.name === b.name;
    }

    function resolveTarget(target, devs = lastDevices) {
        if (!target) return null;
        const match = devs.find(dev => isSameTarget(dev, target));
        return match ? { ...target, ...match } : target;
    }

    function getActiveTarget() {
        pinned = resolveTarget(pinned);
        manualTarget = resolveTarget(manualTarget);
        return pinned || manualTarget;
    }

    function setPin(dev) {
        const prev = pinned;
        pinned = dev;
        if (dev && manualTarget && isSameTarget(manualTarget, dev)) manualTarget = null;
        if (!dev && prev && manualTarget && isSameTarget(manualTarget, prev)) manualTarget = null;

        dev ? localStorage.setItem('ll_pin', JSON.stringify(dev))
            : localStorage.removeItem('ll_pin');

        syncPinUI();
        renderDevices(lastDevices);
        toast(dev ? `Pinned "${dev.name}" as target` : 'Unpinned device');
    }

    function syncPinUI() {
        const chip = q('#pinned-chip');
        const target = getActiveTarget();
        if (target) {
            chip.classList.remove('hidden');
            q('#pinned-chip-name').textContent = target.name;
            q('#btn-chip-unpin').title = pinned ? 'Unpin' : 'Clear target';
        } else {
            chip.classList.add('hidden');
        }
        const hasTarget = !!target;
        q('#btn-quick-send').classList.toggle('hidden', !hasTarget);
    }

    q('#btn-chip-unpin').onclick = () => {
        if (pinned) setPin(null);
        else { manualTarget = null; syncPinUI(); renderDevices(lastDevices); toast('Cleared target'); }
    };

    syncPinUI();

    function devicesChanged(oldDevs, newDevs) {
        if (!oldDevs || oldDevs.length !== newDevs.length) return true;
        for (let i = 0; i < oldDevs.length; i++) {
            const o = oldDevs[i];
            const n = newDevs[i];
            if (o.id !== n.id || o.name !== n.name || o.ipAddress !== n.ipAddress || o.port !== n.port) return true;
        }
        return false;
    }

    setInterval(async () => {
        try {
            const newDevices = await fetch('/api/devices').then(r => r.json());
            if (devicesChanged(lastDevices, newDevices)) {
                lastDevices = newDevices;
                renderDevices(lastDevices);
            }
        } catch { }
    }, 2000);

    function renderDevices(devs) {
        const list = q('#device-list');
        const empty = q('#empty-state');
        pinned = resolveTarget(pinned, devs);
        manualTarget = resolveTarget(manualTarget, devs);
        syncPinUI();
        q('#scan-text').textContent = devs.length ? `${devs.length} device(s) found` : 'Scanning…';
        empty.style.display = devs.length ? 'none' : '';
        list.innerHTML = '';

        devs.forEach(dev => {
            const isPinned = pinned?.id === dev.id;
            const isSelected = !isPinned && isSameTarget(manualTarget, dev);
            const icon = /mac|laptop|desktop|pc|linux|windows/i.test(dev.name) ? 'computer' : 'phone_android';

            const card = document.createElement('div');
            card.className = `device-card${isPinned ? ' pinned' : ''}${isSelected ? ' selected' : ''}`;
            card.innerHTML = `
                <div class="dev-icon"><span class="material-icons-round">${icon}</span></div>
                <div class="dev-info">
                    <div class="dev-name">
                        ${dev.name}
                        ${isPinned ? '<span class="pin-label">PINNED</span>' : isSelected ? '<span class="pin-label selected-target">SELECTED</span>' : ''}
                    </div>
                    <div class="dev-ip">${dev.ipAddress}:${dev.port}</div>
                </div>
                <button class="pin-btn${isPinned ? ' on' : ''}" title="${isPinned ? 'Unpin' : 'Pin as target'}">
                    <span class="material-icons-round">push_pin</span>
                </button>`;

            q('.pin-btn', card).onclick = e => { e.stopPropagation(); setPin(isPinned ? null : dev); };
            card.addEventListener('click', () => {
                manualTarget = dev;
                syncPinUI();
                renderDevices(lastDevices);
                openFilesTab();
                if (!pinned) toast(`Target: ${dev.name} — drop or browse a file`);
            });
            list.appendChild(card);
        });
    }

    function openFilesTab() {
        navBtns.forEach(b => b.classList.remove('active'));
        tabs.forEach(t => t.classList.add('hidden'));
        q('[data-tab="tab-files"]').classList.add('active');
        q('#tab-files').classList.remove('hidden');
    }

    /* ── File Staging ── */

    let stagedFile = null;
    let lastSent = null;
    const dz = q('#drop-zone');
    const fi = q('#file-input');

    dz.addEventListener('click', () => fi.click());
    dz.addEventListener('dragover', e => { e.preventDefault(); dz.classList.add('drag-over'); });
    dz.addEventListener('dragleave', () => dz.classList.remove('drag-over'));
    dz.addEventListener('drop', e => {
        e.preventDefault(); dz.classList.remove('drag-over');
        if (e.dataTransfer.files[0]) stage(e.dataTransfer.files[0]);
    });
    fi.addEventListener('change', () => { if (fi.files[0]) stage(fi.files[0]); });

    function stage(file) {
        if (!file || file.size <= 0) return toast('Invalid file');
        stagedFile = file;
        q('#staged-name').textContent = file.name;
        q('#staged-size').textContent = fmt(file.size);
        const prev = q('#staged-preview');
        prev.innerHTML = '';
        if (file.type.startsWith('image/')) {
            const img = document.createElement('img');
            img.src = URL.createObjectURL(file);
            prev.appendChild(img);
        } else if (file.type.startsWith('video/')) {
            const vid = document.createElement('video');
            vid.src = URL.createObjectURL(file); vid.muted = true;
            prev.appendChild(vid);
        } else {
            const ext = file.name.split('.').pop().toUpperCase().slice(0, 5) || 'FILE';
            prev.innerHTML = `<span class="ext-thumb">${ext}</span>`;
        }
        q('#staged-card').classList.remove('hidden');
        dz.style.display = 'none';
        syncPinUI();
    }

    q('#btn-clear-stage').onclick = () => {
        stagedFile = null;
        q('#staged-card').classList.add('hidden');
        dz.style.display = '';
        syncPinUI();
    };

    q('#btn-quick-send').onclick = async () => {
        const t = getActiveTarget();
        if (!t) return toast('No device selected');
        if (!stagedFile) return toast('No file staged');
        await doSend(stagedFile, t.ipAddress, t.name);
    };

    /* ── Send Flow ── */

    async function doSend(file, ip, name) {
        try {
            lastSent = [file];
            clientTransferState = {
                phase: 'staging', direction: 'send', title: 'Preparing transfer',
                detail: `Staging ${file.name} for ${name}.`,
                fileName: file.name, peerName: name, totalBytes: file.size, transferredBytes: 0
            };
            renderTransferState(clientTransferState);

            const hostResp = await fetch('/api/host', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ files: [{ name: file.name, size: file.size }] })
            });
            if (!hostResp.ok) throw new Error(`Failed to host file (${hostResp.status})`);

            clientTransferState = {
                phase: 'connecting', direction: 'send', title: 'Contacting receiver',
                detail: `Waiting for ${name} to confirm the transfer.`,
                fileName: file.name, peerName: name, totalBytes: file.size, transferredBytes: 0
            };
            renderTransferState(clientTransferState);

            const { success, error } = await fetch('/api/sendHandshake', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ peerIp: ip })
            }).then(r => r.json());

            if (!success) throw new Error(error || 'Connection failed');

            clientTransferState = {
                phase: 'waiting', direction: 'send', title: 'Waiting for receiver',
                detail: `${name} can accept ${file.name} now.`,
                fileName: file.name, peerName: name, totalBytes: file.size, transferredBytes: 0
            };
            renderTransferState(clientTransferState);
            startUploadPolling();
            toast(`Waiting for ${name} to accept "${file.name}"`);
            return true;
        } catch (error) {
            console.error('Send failed', error);
            clientTransferState = {
                phase: 'failed', direction: 'send', title: 'Transfer failed',
                detail: error?.message || 'Connection failed',
                error: error?.message || 'Connection failed',
                fileName: file.name, peerName: name, totalBytes: file.size, transferredBytes: 0
            };
            renderTransferState(clientTransferState);
            toast(error?.message || 'Connection failed');
            return false;
        }
    }

    function uploadFileWithProgress(idx, file) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            xhr.open('POST', `/api/upload_stream/${idx}`);
            xhr.upload.onprogress = event => {
                if (!event.lengthComputable) return;
                clientTransferState = {
                    phase: 'sending', direction: 'send', title: 'Sending file',
                    detail: `Uploading ${file.name} to the receiver.`,
                    fileName: file.name,
                    peerName: clientTransferState?.peerName || getActiveTarget()?.name || '',
                    totalBytes: event.total || file.size, transferredBytes: event.loaded
                };
                renderTransferState(clientTransferState);
            };
            xhr.onload = () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    clientTransferState = {
                        phase: 'done', direction: 'send', title: 'Transfer complete',
                        detail: `${file.name} was sent successfully.`,
                        fileName: file.name,
                        peerName: clientTransferState?.peerName || getActiveTarget()?.name || '',
                        totalBytes: file.size, transferredBytes: file.size
                    };
                    renderTransferState(clientTransferState);
                    resolve();
                } else { reject(new Error(`Upload failed (${xhr.status})`)); }
            };
            xhr.onerror = () => reject(new Error('Upload failed'));
            xhr.send(file);
        });
    }

    /* ── Upload Polling ── */

    let uploadPollTimer = null;
    async function checkUploads() {
        if (!clientTransferState || clientTransferState.phase !== 'waiting') {
            uploadPollTimer = null;
            return;
        }
        try {
            const { triggerUploadIdx } = await fetch('/api/poll_uploads').then(r => r.json());
            if (triggerUploadIdx != null && lastSent?.[triggerUploadIdx]) {
                await uploadFileWithProgress(triggerUploadIdx, lastSent[triggerUploadIdx]);
            }
        } catch (error) {
            clientTransferState = {
                phase: 'failed', direction: 'send', title: 'Upload failed',
                detail: error?.message || 'Upload interrupted.',
                error: error?.message || 'Upload interrupted',
                fileName: lastSent?.[0]?.name || '',
                peerName: clientTransferState?.peerName || '',
                totalBytes: lastSent?.[0]?.size || 0, transferredBytes: 0
            };
            renderTransferState(clientTransferState);
        }
        if (clientTransferState && clientTransferState.phase === 'waiting') {
            uploadPollTimer = setTimeout(checkUploads, 800);
        } else {
            uploadPollTimer = null;
        }
    }

    function startUploadPolling() {
        if (!uploadPollTimer) {
            checkUploads();
        }
    }

    /* ── Receive Modal ── */

    let receiveModalMode = 'prompt';
    const recModal = q('#receive-modal');

    const receiveModalState = {
        progress: q('#receive-progress'),
        phase: q('#receive-progress-phase'),
        percent: q('#receive-progress-percent'),
        fill: q('#receive-progress-fill'),
        detail: q('#receive-progress-detail')
    };

    function renderReceiveModalProgress(state) {
        const total = Number(state.totalBytes || 0);
        const transferred = Math.max(0, Number(state.transferredBytes || 0));
        const percent = state.phase === 'done' ? 100 : total ? Math.round((transferred / total) * 100) : 8;
        receiveModalState.progress.classList.remove('hidden');
        receiveModalState.phase.textContent = state.title || 'Downloading';
        receiveModalState.percent.textContent = `${Math.max(0, Math.min(100, percent))}%`;
        receiveModalState.fill.style.width = `${Math.max(6, Math.min(100, percent))}%`;
        receiveModalState.fill.style.background = state.phase === 'failed'
            ? 'linear-gradient(90deg, #BA1A1A, #DC2626)'
            : 'linear-gradient(90deg, #00488D, #005FB8)';
        receiveModalState.detail.textContent = state.detail || 'Preparing transfer…';
    }

    function resetReceiveModal() {
        receiveModalMode = 'prompt';
        receiveModalState.progress.classList.add('hidden');
        receiveModalState.fill.style.width = '0%';
        q('#btn-accept').textContent = 'Accept & Download';
        q('#btn-accept').disabled = false;
        q('#btn-reject').textContent = 'Reject';
        q('#btn-reject').disabled = false;
        q('#btn-reject').classList.remove('hidden');
    }

    setInterval(async () => {
        try {
            if (!recModal.classList.contains('hidden')) return;
            const d = await fetch('/api/incoming').then(r => r.json());
            if (d?.senderName) {
                resetReceiveModal();
                q('#receive-info').textContent =
                    `${d.senderName} wants to send ${d.files.length} file(s): ${d.files.map(f => f.name).join(', ')}`;
                q('#receive-summary').innerHTML = `
                    <div class="receive-summary-row"><span>Sender</span><span>${d.senderName}</span></div>
                    <div class="receive-summary-row"><span>Files</span><span>${d.files.length}</span></div>
                    <div class="receive-summary-row"><span>Total</span><span>${fmt(d.files.reduce((sum, f) => sum + (f.sizeBytes || 0), 0))}</span></div>`;
                recModal.classList.remove('hidden');
            }
        } catch { }
    }, 1200);

    q('#btn-accept').onclick = async () => {
        const btn = q('#btn-accept');
        if (receiveModalMode === 'progress') {
            recModal.classList.add('hidden');
            resetReceiveModal();
            return;
        }
        btn.textContent = 'Starting…'; btn.disabled = true;
        q('#btn-reject').disabled = true;
        await fetch('/api/accept', { method: 'POST' });
        clientTransferState = null;
        receiveModalMode = 'progress';
        renderReceiveModalProgress({
            phase: 'receiving', title: 'Starting download',
            detail: 'Receiving file now.', totalBytes: 0, transferredBytes: 0
        });
        btn.textContent = 'Downloading…'; btn.disabled = true;
        q('#btn-reject').classList.add('hidden');
        toast('Download started');
    };

    q('#btn-reject').onclick = async () => {
        await fetch('/api/reject', { method: 'POST' });
        recModal.classList.add('hidden');
        resetReceiveModal();
    };

    /* ── History ── */

    let historyData = [];
    let historyFilter = 'all';

    const segBtns = document.querySelectorAll('.seg-btn');
    segBtns.forEach(btn => btn.addEventListener('click', () => {
        segBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        historyFilter = btn.dataset.filter;
        renderHistoryList();
    }));

    function renderHistoryList() {
        const list = q('#history-list');
        const filtered = historyFilter === 'all'
            ? historyData
            : historyData.filter(h => h.direction.toLowerCase() === historyFilter);

        if (!filtered.length) {
            list.innerHTML = '<div class="history-empty">No transfer records yet.</div>';
            return;
        }

        list.innerHTML = filtered.slice().reverse().map(h => {
            const d = new Date(h.timestamp || Date.now());
            const ts = d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            const dir = h.direction.toLowerCase();
            const icon = dir === 'sent' ? 'upload' : 'download';
            const clickable = dir === 'received' && h.filePath;
            return `<div class="history-item${clickable ? ' clickable' : ''}" ${clickable ? `data-path="${h.filePath.replace(/"/g, '"')}"` : ''}>
                <div class="history-icon ${dir}">
                    <span class="material-icons-round">${icon}</span>
                </div>
                <div class="history-body">
                    <div class="history-name">${h.fileName}</div>
                    <div class="history-meta">${dir === 'sent' ? 'To' : 'From'} ${h.peerName} · ${fmt(h.sizeBytes)} · ${ts}</div>
                </div>
                <span class="history-dir ${dir}">${h.direction}</span>
            </div>`;
        }).join('');

        list.querySelectorAll('.history-item.clickable').forEach(item => {
            item.addEventListener('click', () => {
                fetch('/api/openFile', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ filePath: item.dataset.path })
                }).then(r => r.json()).then(r => {
                    if (!r.success) toast(r.error || 'Could not open file');
                }).catch(() => toast('Failed to open file'));
            });
        });
    }

    async function loadHistory() {
        try {
            historyData = await fetch('/api/history').then(r => r.json());
            renderHistoryList();
        } catch { }
    }

    /* ── Settings ── */

    async function loadSettings() {
        try {
            const d = await fetch('/api/settings').then(r => r.json());
            q('#setting-name').value = d.deviceName || '';
            q('#setting-path').value = d.downloadPath || '';
            q('#setting-auto').checked = !!d.autoAccept;
            const nameEl = q('#settings-device-name');
            if (nameEl) nameEl.textContent = d.deviceName || '—';
        } catch { }
    }

    q('#btn-save-settings').onclick = async () => {
        const btn = q('#btn-save-settings');
        btn.textContent = 'Saving…';
        await fetch('/api/settings', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                deviceName: q('#setting-name').value,
                downloadPath: q('#setting-path').value,
                autoAccept: q('#setting-auto').checked
            })
        });
        q('#host-badge').textContent = q('#setting-name').value.slice(0, 14).toUpperCase();
        btn.textContent = 'Saved';
        setTimeout(() => btn.textContent = 'Save Settings', 2000);
    };

    fetch('/api/settings').then(r => r.json())
        .then(d => {
            q('#host-badge').textContent = (d.deviceName || '').slice(0, 14).toUpperCase();
            const nameEl = q('#settings-device-name');
            if (nameEl) nameEl.textContent = d.deviceName || '—';
        })
        .catch(() => { });
});

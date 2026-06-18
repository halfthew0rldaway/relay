/**
 * validation.js — Input validation for LocalLink Desktop server.
 *
 * Protects against malformed requests, path traversal,
 * oversized payloads, and invalid transfer metadata.
 */

/** Maximum files allowed per transfer */
export const MAX_FILES_PER_TRANSFER = 10;

/** Maximum filename length (bytes) */
export const MAX_FILENAME_LENGTH = 255;

/** Maximum single file size (10 GB) */
export const MAX_FILE_SIZE = 10 * 1024 * 1024 * 1024;

/**
 * Sanitize a filename: strip path traversal, null bytes, and trim.
 * Returns a safe filename or a fallback.
 */
export function sanitizeFileName(name = '') {
    let safe = String(name)
        .replace(/\0/g, '')           // strip null bytes
        .replace(/[/\\]/g, '')        // strip path separators
        .replace(/^\.\.?[/\\]?/, '')  // strip leading ../
        .trim();

    if (!safe) safe = `download-${Date.now()}`;

    // Truncate if too long, preserving extension
    if (safe.length > MAX_FILENAME_LENGTH) {
        const dotIdx = safe.lastIndexOf('.');
        if (dotIdx > 0) {
            const ext = safe.slice(dotIdx);
            const base = safe.slice(0, MAX_FILENAME_LENGTH - ext.length);
            safe = base + ext;
        } else {
            safe = safe.slice(0, MAX_FILENAME_LENGTH);
        }
    }

    return safe;
}

/**
 * Parse and validate a handshake payload.
 * Expected format: "SenderName|||file1:size1;;;file2:size2"
 * Returns { senderName, files } or { error }.
 */
export function parseHandshakePayload(body = '') {
    const parts = String(body).split('|||');
    const senderName = (parts[0] || '').trim();
    const filesPart = parts[1] || '';

    if (!senderName) {
        return { error: 'Missing sender name' };
    }

    const rawEntries = filesPart.split(';;;').filter(s => s.trim());
    if (rawEntries.length === 0) {
        return { error: 'No files specified' };
    }

    if (rawEntries.length > MAX_FILES_PER_TRANSFER) {
        return { error: `Too many files (max ${MAX_FILES_PER_TRANSFER})` };
    }

    const files = [];
    for (const entry of rawEntries) {
        const colonIdx = entry.lastIndexOf(':');
        if (colonIdx === -1) {
            return { error: `Malformed file entry: ${entry}` };
        }

        const name = sanitizeFileName(entry.slice(0, colonIdx));
        const sizeBytes = parseInt(entry.slice(colonIdx + 1), 10);

        if (!Number.isFinite(sizeBytes) || sizeBytes < 0) {
            return { error: `Invalid file size for: ${name}` };
        }

        if (sizeBytes > MAX_FILE_SIZE) {
            return { error: `File too large: ${name}` };
        }

        files.push({ name, sizeBytes });
    }

    return { senderName: senderName.slice(0, 100), files };
}

/**
 * Simple per-IP rate limiter.
 * Returns true if the request is allowed, false if rate-limited.
 */
const rateLimitMap = new Map();
const RATE_LIMIT_WINDOW_MS = 1000; // 1 second

export function checkRateLimit(ip) {
    const now = Date.now();
    const last = rateLimitMap.get(ip) || 0;
    if (now - last < RATE_LIMIT_WINDOW_MS) {
        return false;
    }
    rateLimitMap.set(ip, now);

    // Cleanup old entries every 100 requests
    if (rateLimitMap.size > 100) {
        for (const [key, timestamp] of rateLimitMap) {
            if (now - timestamp > 10000) rateLimitMap.delete(key);
        }
    }

    return true;
}

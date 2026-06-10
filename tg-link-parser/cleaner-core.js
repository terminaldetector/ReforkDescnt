/**
 * TG HARVESTER — CLEANER CORE (Backup / Delete bridge to Telethon)
 * ---------------------------------------------------------------------------
 * The browser CANNOT delete Telegram messages — that MUST go through the
 * official MTProto API. So this module is a thin, async CLIENT for a LOCAL
 * Python helper (telethon_backend/backend.py) that uses Telethon to operate on
 * the USER'S OWN account.
 *
 * Security model:
 *   - api_id / api_hash are stored locally in the extension and forwarded ONLY
 *     to 127.0.0.1; the login code / 2FA password are entered in the backend's
 *     own console — never typed into or transmitted by the extension.
 *   - Destructive operations require an explicit confirm token and honor a
 *     dialog whitelist; a dry-run mode previews counts without deleting.
 *   - "Load weight" maps to batch size + inter-batch delay to respect API
 *     flood limits (Telegram deletes up to 100 messages per call).
 *
 * This file performs NO deletion itself; it only relays configuration and
 * polls job status. If the backend is not running, every call fails cleanly.
 * ---------------------------------------------------------------------------
 */

const DEFAULT_BACKEND = "http://127.0.0.1:8787";

/** fetch JSON against the backend with a timeout; never throws. */
async function call(base, path, { method = "GET", body, timeoutMs = 15000 } = {}) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const resp = await fetch((base || DEFAULT_BACKEND) + path, {
      method,
      signal: controller.signal,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
    clearTimeout(t);
    const text = await resp.text();
    let json;
    try { json = text ? JSON.parse(text) : {}; } catch (_) { json = { raw: text }; }
    if (!resp.ok) return { ok: false, error: json.error || `HTTP ${resp.status}`, ...json };
    return { ok: true, ...json };
  } catch (e) {
    clearTimeout(t);
    return {
      ok: false,
      error: e && e.name === "AbortError"
        ? "backend timeout"
        : "backend unreachable — is telethon_backend/backend.py running?",
    };
  }
}

export const Cleaner = {
  /** Health check + auth status of the local backend. */
  ping(base) {
    return call(base, "/ping", { timeoutMs: 4000 });
  },

  /**
   * Send api_id / api_hash so the backend can build its Telethon client. The
   * interactive login (code / 2FA) happens in the backend console.
   */
  configure(base, { apiId, apiHash }) {
    return call(base, "/configure", {
      method: "POST",
      body: { api_id: apiId, api_hash: apiHash },
    });
  },

  /**
   * Start a backup job.
   * @param {object} cfg { dialog, withMedia, skipDuplicates, batch }
   * @returns {Promise<{ok, jobId}>}
   */
  startBackup(base, cfg) {
    return call(base, "/backup", { method: "POST", body: cfg, timeoutMs: 20000 });
  },

  /**
   * Start a delete job (own messages only).
   * @param {object} cfg {
   *   scope, dialog, olderThanDays, mediaType, wipeEverything,
   *   whitelist, confirmToken, dryRun, batch
   * }
   * @returns {Promise<{ok, jobId}>}
   */
  startDelete(base, cfg) {
    return call(base, "/delete", { method: "POST", body: cfg, timeoutMs: 20000 });
  },

  /** Poll a job's progress + log. */
  jobStatus(base, jobId) {
    return call(base, `/job/${encodeURIComponent(jobId)}`, { timeoutMs: 8000 });
  },

  /** Request a running job to abort. */
  abort(base, jobId) {
    return call(base, `/abort/${encodeURIComponent(jobId)}`, { method: "POST", timeoutMs: 5000 });
  },
};

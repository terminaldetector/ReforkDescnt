/**
 * TG HARVESTER — SHARED UTILITIES
 * ---------------------------------------------------------------------------
 * Small, dependency-free helpers shared by the background and popup modules.
 * ---------------------------------------------------------------------------
 */

/** Resolve after `ms` milliseconds (politeness delay / batch throttle). */
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Parse + clamp an integer with a default fallback. */
export function clampInt(v, min, max, dflt) {
  const n = parseInt(v, 10);
  if (Number.isNaN(n)) return dflt;
  return Math.min(max, Math.max(min, n));
}

/** Registrable-ish host (drops www.) for caps / exclude lists. */
export function hostOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, "").toLowerCase();
  } catch (_) {
    return String(url || "");
  }
}

/** True if the URL's host matches one of the excluded domains. */
export function isExcluded(url, excludeDomains) {
  if (!excludeDomains || !excludeDomains.length) return false;
  const h = hostOf(url);
  return excludeDomains.some((d) => h === d || h.endsWith("." + d));
}

/** Normalize + de-duplicate raw source URLs, dropping blanks. */
export function dedupeUrls(urls) {
  const seen = new Set();
  const out = [];
  for (let u of urls || []) {
    u = (u || "").trim();
    if (!u) continue;
    if (!/^https?:\/\//i.test(u)) u = "https://" + u;
    const key = u.replace(/\/+$/, "").toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(u);
  }
  return out;
}

/** ISO timestamp safe for filenames (no ':' or '.'). */
export function fileStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
}

/** Minimal HTML-escape for safe DOM injection. */
export function escapeHtml(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/** HTML-attribute escape (adds quote handling on top of escapeHtml). */
export function escapeAttr(s) {
  return escapeHtml(s).replace(/"/g, "&quot;");
}

/**
 * Map the user-facing "load weight" to concrete batch throttling parameters
 * used by the cleaner. Telegram allows deleting up to 100 messages per call;
 * heavier weight = bigger batches + shorter waits (faster, riskier on limits).
 * @param {"light"|"medium"|"heavy"} weight
 * @returns {{batchSize:number, batchDelayMs:number, label:string}}
 */
export function weightToBatch(weight) {
  switch (weight) {
    case "heavy": return { batchSize: 100, batchDelayMs: 400, label: "heavy" };
    case "medium": return { batchSize: 50, batchDelayMs: 1200, label: "medium" };
    case "light":
    default: return { batchSize: 20, batchDelayMs: 3000, label: "light" };
  }
}

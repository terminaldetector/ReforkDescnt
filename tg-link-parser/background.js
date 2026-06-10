/**
 * TG LINK HARVESTER — BACKGROUND SERVICE WORKER
 * ---------------------------------------------------------------------------
 * The orchestration core. Responsibilities:
 *   1. Manage the parse queue (list-of-URLs mode + keyword-search mode).
 *   2. Fetch pages with configurable timeout + inter-request delay (politeness
 *      / anti-ban), with a small retry budget.
 *   3. Hand fetched HTML to the extractor and persist unique results in
 *      IndexedDB (chosen over localStorage so we can hold tens of thousands of
 *      rows without hitting the ~5 MB cap).
 *   4. Track live progress and expose it (plus the result set) to the popup.
 *
 * Everything is message-driven; the popup is the only client.
 * ---------------------------------------------------------------------------
 */

importScripts("extractor.js", "searchEngines.js");

const { extractTelegramLinks } = self.TGExtractor;
const { buildSerpUrl, extractSerpResults, detectEngineByLang } = self.TGSearch;

// ─────────────────────────────────────────────────────────────────────────
//  IndexedDB layer
// ─────────────────────────────────────────────────────────────────────────
const DB_NAME = "tg_link_harvester";
const DB_VERSION = 1;
const STORE = "results"; // keyPath: "key" (normalized link) -> natural dedup

/** Open (and lazily create) the IndexedDB database. */
function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const os = db.createObjectStore(STORE, { keyPath: "key" });
        os.createIndex("type", "link_type", { unique: false });
        os.createIndex("source", "source_url", { unique: false });
        os.createIndex("parsed_at", "parsed_at", { unique: false });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

/**
 * Insert a row only if its key is not already present (true de-duplication).
 * @returns {Promise<boolean>} true if newly inserted, false if duplicate.
 */
async function dbInsertUnique(row) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    const os = tx.objectStore(STORE);
    const getReq = os.get(row.key);
    getReq.onsuccess = () => {
      if (getReq.result) {
        resolve(false); // duplicate — keep the first-seen source.
      } else {
        os.put(row);
        resolve(true);
      }
    };
    getReq.onerror = () => reject(getReq.error);
  });
}

/** Read every stored row. */
async function dbGetAll() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readonly");
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () => resolve(req.result || []);
    req.onerror = () => reject(req.error);
  });
}

/** Count stored rows. */
async function dbCount() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readonly");
    const req = tx.objectStore(STORE).count();
    req.onsuccess = () => resolve(req.result || 0);
    req.onerror = () => reject(req.error);
  });
}

/** Wipe all stored rows. */
async function dbClear() {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).clear();
    tx.oncomplete = () => resolve(true);
    tx.onerror = () => reject(tx.error);
  });
}

// ─────────────────────────────────────────────────────────────────────────
//  Live parse state (kept in-memory; mirrored to chrome.storage for the popup
//  so progress survives the popup being closed/reopened).
// ─────────────────────────────────────────────────────────────────────────
const STATE = {
  running: false,
  stopRequested: false,
  mode: null, // "list" | "search"
  total: 0, // total pages to process
  done: 0, // pages processed
  currentUrl: "",
  found: 0, // unique telegram links found this run
  errors: [], // [{url, error}]
  warnings: [], // human-readable warnings (captcha etc.)
  startedAt: 0,
};

/** Push the current STATE snapshot to chrome.storage and (best-effort) popup. */
function publishState() {
  const snapshot = { ...STATE };
  chrome.storage.local.set({ parserState: snapshot });
  chrome.action.setBadgeText({ text: STATE.found ? String(STATE.found) : "" });
  chrome.action.setBadgeBackgroundColor({ color: "#00e5ff" });
  // The popup may be closed — ignore the inevitable "no receiver" error.
  chrome.runtime.sendMessage({ type: "PROGRESS", state: snapshot }).catch(() => {});
}

function resetState(mode, total) {
  STATE.running = true;
  STATE.stopRequested = false;
  STATE.mode = mode;
  STATE.total = total;
  STATE.done = 0;
  STATE.currentUrl = "";
  STATE.found = 0;
  STATE.errors = [];
  STATE.warnings = [];
  STATE.startedAt = Date.now();
  publishState();
}

function finishState() {
  STATE.running = false;
  STATE.currentUrl = "";
  publishState();
  chrome.runtime.sendMessage({ type: "DONE", state: { ...STATE } }).catch(() => {});
}

// ─────────────────────────────────────────────────────────────────────────
//  Networking helpers
// ─────────────────────────────────────────────────────────────────────────

/** Promise that resolves after `ms` milliseconds (the politeness delay). */
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Fetch a URL as text with an abort-based timeout and a small retry budget.
 * @param {string} url
 * @param {number} timeoutMs
 * @param {number} retries
 * @returns {Promise<{ok:boolean, text:string, status:number, error?:string}>}
 */
async function fetchText(url, timeoutMs, retries) {
  let attempt = 0;
  let lastErr = "";
  while (attempt <= retries) {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const resp = await fetch(url, {
        signal: controller.signal,
        redirect: "follow",
        credentials: "omit",
        headers: { "Accept-Language": "en,ja;q=0.8,ko;q=0.7,zh;q=0.6" },
      });
      clearTimeout(t);
      const text = await resp.text();
      return { ok: resp.ok, text, status: resp.status };
    } catch (e) {
      clearTimeout(t);
      lastErr = e && e.name === "AbortError" ? "timeout" : String(e && e.message || e);
      attempt++;
      if (attempt <= retries) await sleep(500 * attempt); // brief backoff
    }
  }
  return { ok: false, text: "", status: 0, error: lastErr };
}

/**
 * Fetch a page's content. Default path uses the worker's `fetch`. When
 * `openInTab` is set we instead open the URL in a (background) tab and run the
 * content script there to read the *rendered* DOM (incl. Shadow DOM), which is
 * useful for JS-heavy pages. The tab is closed afterwards.
 * @returns {Promise<{ok:boolean, blob:string, error?:string}>}
 */
async function getPageContent(url, opts) {
  if (!opts.openInTab) {
    const res = await fetchText(url, opts.timeoutMs, opts.retries);
    if (!res.ok) return { ok: false, blob: "", error: res.error || `HTTP ${res.status}` };
    return { ok: true, blob: res.text };
  }

  // Tab + content-script extraction path.
  let tab;
  try {
    tab = await chrome.tabs.create({ url, active: false });
    await waitForTabLoad(tab.id, opts.timeoutMs);
    const [{ result } = {}] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      files: ["content.js"],
    });
    // content.js returns the harvested blob as its last expression value.
    const blob = typeof result === "string" ? result : "";
    return { ok: true, blob };
  } catch (e) {
    return { ok: false, blob: "", error: String(e && e.message || e) };
  } finally {
    if (tab && tab.id != null) {
      try { await chrome.tabs.remove(tab.id); } catch (_) {}
    }
  }
}

/** Resolve once a tab finishes loading, or after a timeout. */
function waitForTabLoad(tabId, timeoutMs) {
  return new Promise((resolve) => {
    let settled = false;
    const done = () => {
      if (settled) return;
      settled = true;
      chrome.tabs.onUpdated.removeListener(listener);
      resolve();
    };
    const listener = (id, info) => {
      if (id === tabId && info.status === "complete") done();
    };
    chrome.tabs.onUpdated.addListener(listener);
    setTimeout(done, timeoutMs);
  });
}

/** Extract the registrable-ish host from a URL for the per-domain cap. */
function hostOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, "").toLowerCase();
  } catch (_) {
    return url;
  }
}

// ─────────────────────────────────────────────────────────────────────────
//  Core: parse one source page and persist its Telegram links
// ─────────────────────────────────────────────────────────────────────────

/**
 * Fetch a single source URL, extract Telegram links, persist the unique ones.
 * @param {string} sourceUrl
 * @param {object} opts run options
 * @param {Map<string,number>} domainCounts running per-domain link tally
 * @returns {Promise<number>} count of NEW unique links stored from this page
 */
async function parseSource(sourceUrl, opts, domainCounts) {
  STATE.currentUrl = sourceUrl;
  publishState();

  const { ok, blob, error } = await getPageContent(sourceUrl, opts);
  if (!ok) {
    STATE.errors.push({ url: sourceUrl, error: error || "fetch failed" });
    return 0;
  }

  const links = extractTelegramLinks(blob);
  const parsedAt = new Date().toISOString();
  let newCount = 0;

  for (const item of links) {
    // Stop-list: skip links the user marked to ignore (e.g. their own).
    if (opts.stopList.some((s) => item.key.includes(s))) continue;

    // Per-domain cap (on the SOURCE domain).
    if (opts.maxPerDomain > 0) {
      const h = hostOf(sourceUrl);
      const used = domainCounts.get(h) || 0;
      if (used >= opts.maxPerDomain) break;
    }

    const inserted = await dbInsertUnique({
      key: item.key,
      source_url: sourceUrl,
      telegram_link: item.link,
      link_type: item.type,
      parsed_at: parsedAt,
    });

    if (inserted) {
      newCount++;
      STATE.found++;
      if (opts.maxPerDomain > 0) {
        const h = hostOf(sourceUrl);
        domainCounts.set(h, (domainCounts.get(h) || 0) + 1);
      }
    }
  }
  return newCount;
}

// ─────────────────────────────────────────────────────────────────────────
//  Run drivers
// ─────────────────────────────────────────────────────────────────────────

/**
 * Mode 1 — parse an explicit list of source URLs.
 * @param {string[]} urls
 * @param {object} opts
 */
async function runListMode(urls, opts) {
  const clean = dedupeUrls(urls);
  resetState("list", clean.length);
  const domainCounts = new Map();

  for (let i = 0; i < clean.length; i++) {
    if (STATE.stopRequested) break;
    await parseSource(clean[i], opts, domainCounts);
    STATE.done = i + 1;
    publishState();
    if (i < clean.length - 1 && !STATE.stopRequested) await sleep(opts.delayMs);
  }
  finishState();
}

/**
 * Mode 2 — keyword search across regional engines, then harvest each result.
 * For every keyword we walk `depth` SERP pages, collect up to `count` organic
 * result URLs, then parse each of those for Telegram links.
 * @param {string[]} keywords
 * @param {object} opts  (adds: engine, count, depth)
 */
async function runSearchMode(keywords, opts) {
  resetState("search", 0); // total grows as we discover result links
  const domainCounts = new Map();
  const targets = []; // accumulated organic result URLs (deduped)
  const seenTargets = new Set();

  // ── Phase A: collect organic result URLs from the SERPs ──────────────────
  for (const kwRaw of keywords) {
    if (STATE.stopRequested) break;
    const kw = kwRaw.trim();
    if (!kw) continue;
    const engine = opts.engine === "auto" ? detectEngineByLang(kw) : opts.engine;

    let collected = 0;
    for (let page = 0; page < opts.depth && collected < opts.count; page++) {
      if (STATE.stopRequested) break;
      const serpUrl = buildSerpUrl(engine, kw, page);
      STATE.currentUrl = `[${engine}] ${kw} (p${page + 1})`;
      publishState();

      const res = await fetchText(serpUrl, opts.timeoutMs, opts.retries);
      if (!res.ok) {
        STATE.errors.push({ url: serpUrl, error: res.error || `HTTP ${res.status}` });
        await sleep(opts.delayMs);
        continue;
      }
      const { links, blocked } = extractSerpResults(engine, res.text, opts.count - collected);
      if (blocked) {
        STATE.warnings.push(
          `${engine}: blocked / CAPTCHA for "${kw}" (p${page + 1}) — solve it manually or raise the delay.`
        );
      }
      for (const u of links) {
        const k = u.replace(/\/+$/, "").toLowerCase();
        if (!seenTargets.has(k)) {
          seenTargets.add(k);
          targets.push(u);
          collected++;
        }
        if (collected >= opts.count) break;
      }
      await sleep(opts.delayMs);
    }
  }

  // ── Phase B: harvest Telegram links from each discovered page ────────────
  STATE.total = targets.length;
  publishState();
  for (let i = 0; i < targets.length; i++) {
    if (STATE.stopRequested) break;
    await parseSource(targets[i], opts, domainCounts);
    STATE.done = i + 1;
    publishState();
    if (i < targets.length - 1 && !STATE.stopRequested) await sleep(opts.delayMs);
  }
  finishState();
}

/** Normalize + de-duplicate a raw list of source URLs, dropping blanks. */
function dedupeUrls(urls) {
  const seen = new Set();
  const out = [];
  for (let u of urls) {
    u = (u || "").trim();
    if (!u) continue;
    if (!/^https?:\/\//i.test(u)) u = "https://" + u; // tolerate bare hosts
    const key = u.replace(/\/+$/, "").toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(u);
  }
  return out;
}

/** Build a normalized options object from the popup's raw payload. */
function buildOptions(raw = {}) {
  return {
    delayMs: clampInt(raw.delayMs, 0, 60000, 800),
    timeoutMs: clampInt(raw.timeoutMs, 1000, 120000, 15000),
    retries: clampInt(raw.retries, 0, 5, 1),
    maxPerDomain: clampInt(raw.maxPerDomain, 0, 100000, 0), // 0 = unlimited
    openInTab: !!raw.openInTab,
    stopList: (raw.stopList || [])
      .map((s) => String(s).trim().toLowerCase())
      .filter(Boolean),
    engine: raw.engine || "auto",
    count: clampInt(raw.count, 1, 100, 10),
    depth: clampInt(raw.depth, 1, 20, 1),
  };
}

function clampInt(v, min, max, dflt) {
  const n = parseInt(v, 10);
  if (Number.isNaN(n)) return dflt;
  return Math.min(max, Math.max(min, n));
}

// ─────────────────────────────────────────────────────────────────────────
//  Message router (popup -> background)
// ─────────────────────────────────────────────────────────────────────────
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    try {
      switch (msg.type) {
        case "START_LIST": {
          if (STATE.running) return sendResponse({ ok: false, error: "already running" });
          const opts = buildOptions(msg.options);
          runListMode(msg.urls || [], opts); // fire-and-forget
          sendResponse({ ok: true });
          break;
        }
        case "START_SEARCH": {
          if (STATE.running) return sendResponse({ ok: false, error: "already running" });
          const opts = buildOptions(msg.options);
          runSearchMode(msg.keywords || [], opts);
          sendResponse({ ok: true });
          break;
        }
        case "STOP": {
          STATE.stopRequested = true;
          publishState();
          sendResponse({ ok: true });
          break;
        }
        case "GET_STATE": {
          sendResponse({ state: { ...STATE } });
          break;
        }
        case "GET_RESULTS": {
          const rows = await dbGetAll();
          sendResponse({ rows });
          break;
        }
        case "GET_COUNT": {
          sendResponse({ count: await dbCount() });
          break;
        }
        case "CLEAR_RESULTS": {
          await dbClear();
          STATE.found = 0;
          publishState();
          sendResponse({ ok: true });
          break;
        }
        default:
          sendResponse({ ok: false, error: "unknown message" });
      }
    } catch (e) {
      sendResponse({ ok: false, error: String(e && e.message || e) });
    }
  })();
  return true; // keep the channel open for the async reply
});

// Clear the badge whenever the user switches tabs (matches prototype UX).
chrome.tabs.onActivated.addListener(() => {
  if (!STATE.running) chrome.action.setBadgeText({ text: "" });
});

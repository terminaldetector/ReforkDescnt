/**
 * TG LINK HARVESTER — BACKGROUND SERVICE WORKER
 * ---------------------------------------------------------------------------
 * Orchestration core:
 *   1. Manage the parse queue for all modes (URL list, keyword search,
 *      deep personal-page scroll) and the periodic monitor (chrome.alarms).
 *   2. Fetch pages with configurable timeout + inter-request delay + retries.
 *   3. For scroll / "open in tab" modes, inject link-extractor.js +
 *      scroll-helper.js + content.js and drive the page (scroll → harvest →
 *      accumulate) so virtualized infinite-scroll feeds are captured fully.
 *   4. Persist unique results in IndexedDB (storage.js) and expose live
 *      progress + the result set to the popup. Everything is message-driven.
 * ---------------------------------------------------------------------------
 */

importScripts("link-extractor.js", "searchEngines.js", "storage.js");

const { extractTelegramLinks, extractPageTitle } = self.TGLinkExtractor;
const { buildSerpUrl, extractSerpResults, detectEngineByLang } = self.TGSearch;
const Store = self.TGStore;

// ─────────────────────────────────────────────────────────────────────────
//  Live run state (mirrored to chrome.storage so the popup can re-attach).
// ─────────────────────────────────────────────────────────────────────────
const STATE = {
  running: false,
  stopRequested: false,
  mode: null, // "list" | "search" | "personal"
  total: 0,
  done: 0,
  currentUrl: "",
  found: 0,
  errors: [],
  warnings: [],
  startedAt: 0,
};

function publishState() {
  const snapshot = { ...STATE };
  chrome.storage.local.set({ parserState: snapshot });
  if (STATE.running) {
    chrome.action.setBadgeText({ text: STATE.found ? String(STATE.found) : "" });
    chrome.action.setBadgeBackgroundColor({ color: "#00e5ff" });
  }
  chrome.runtime.sendMessage({ type: "PROGRESS", state: snapshot }).catch(() => {});
}

function resetState(mode, total) {
  Object.assign(STATE, {
    running: true, stopRequested: false, mode, total,
    done: 0, currentUrl: "", found: 0, errors: [], warnings: [],
    startedAt: Date.now(),
  });
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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Fetch a URL as text with abort-timeout and a small retry budget. */
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
      lastErr = e && e.name === "AbortError" ? "timeout" : String((e && e.message) || e);
      attempt++;
      if (attempt <= retries) await sleep(500 * attempt);
    }
  }
  return { ok: false, text: "", status: 0, error: lastErr };
}

/**
 * Page-side driver injected into a tab. Self-contained (no closure over the
 * worker). Harvests the rendered DOM once, optionally auto-scrolls while
 * harvesting after every step (to capture virtualized feeds), and returns the
 * accumulated unique links plus the page title.
 * @param {{scroll:boolean,maxScrolls:number,intervalMs:number}} opts
 */
async function tgPageDriver(opts) {
  const H = globalThis.TGHarvest;
  const S = globalThis.TGScroll;
  const E = globalThis.TGLinkExtractor;
  const acc = new Map();
  const harvest = () => {
    try {
      for (const it of E.extractTelegramLinks(H.harvestBlob())) acc.set(it.key, it);
    } catch (_) {}
  };
  harvest();
  if (opts && opts.scroll && S) {
    await S.autoScroll({
      maxScrolls: opts.maxScrolls,
      intervalMs: opts.intervalMs,
      onStep: harvest,
    });
  }
  return { links: Array.from(acc.values()), title: document.title || "" };
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

/**
 * Fetch a page's content as a normalized result. Two strategies:
 *   - default: worker `fetch` over raw HTML (fast, static pages)
 *   - tab mode (openInTab OR scroll): open a background tab, inject the page
 *     scripts, run the driver (with optional scrolling), read the rendered DOM.
 * @returns {Promise<{ok:boolean, links:Array, title:string, error?:string}>}
 */
async function getPageContent(url, opts) {
  if (!opts.openInTab && !opts.scroll) {
    const res = await fetchText(url, opts.timeoutMs, opts.retries);
    if (!res.ok) return { ok: false, links: [], title: "", error: res.error || `HTTP ${res.status}` };
    return {
      ok: true,
      links: extractTelegramLinks(res.text),
      title: extractPageTitle(res.text),
    };
  }

  // Tab + page-driver strategy (rendered DOM / infinite scroll).
  let tab;
  try {
    tab = await chrome.tabs.create({ url, active: false });
    await waitForTabLoad(tab.id, opts.timeoutMs);
    await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      files: ["link-extractor.js", "scroll-helper.js", "content.js"],
    });
    const [{ result } = {}] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: tgPageDriver,
      args: [{ scroll: !!opts.scroll, maxScrolls: opts.maxScrolls, intervalMs: opts.intervalMs }],
    });
    return {
      ok: true,
      links: (result && result.links) || [],
      title: (result && result.title) || "",
    };
  } catch (e) {
    return { ok: false, links: [], title: "", error: String((e && e.message) || e) };
  } finally {
    if (tab && tab.id != null) {
      try { await chrome.tabs.remove(tab.id); } catch (_) {}
    }
  }
}

/** Registrable-ish host of a URL (for per-domain caps and exclude lists). */
function hostOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, "").toLowerCase();
  } catch (_) {
    return url;
  }
}

/** True if the URL's host matches one of the excluded domains. */
function isExcluded(url, excludeDomains) {
  if (!excludeDomains || !excludeDomains.length) return false;
  const h = hostOf(url);
  return excludeDomains.some((d) => h === d || h.endsWith("." + d));
}

// ─────────────────────────────────────────────────────────────────────────
//  Core: parse one source page and persist its Telegram links
// ─────────────────────────────────────────────────────────────────────────

/**
 * Fetch/scan a single source URL, extract links, persist the unique ones.
 * @returns {Promise<number>} count of NEW unique links stored from this page.
 */
async function parseSource(sourceUrl, opts, domainCounts) {
  if (isExcluded(sourceUrl, opts.excludeDomains)) {
    STATE.warnings.push(`skipped (excluded domain): ${sourceUrl}`);
    return 0;
  }
  STATE.currentUrl = sourceUrl;
  publishState();

  const res = await getPageContent(sourceUrl, opts);
  if (!res.ok) {
    STATE.errors.push({ url: sourceUrl, error: res.error || "fetch failed" });
    return 0;
  }

  const detectedAt = new Date().toISOString();
  let newCount = 0;

  for (const item of res.links) {
    if (opts.stopList.some((s) => item.key.includes(s))) continue;

    if (opts.maxPerDomain > 0) {
      const h = hostOf(sourceUrl);
      if ((domainCounts.get(h) || 0) >= opts.maxPerDomain) break;
    }

    const inserted = await Store.insertUnique({
      key: item.key,
      source_url: sourceUrl,
      telegram_link: item.link,
      link_type: item.type,
      page_title: res.title || "",
      detected_at: detectedAt,
    });

    if (inserted) {
      newCount++;
      STATE.found++;
      if (opts.maxPerDomain > 0) {
        const h = hostOf(sourceUrl);
        domainCounts.set(h, (domainCounts.get(h) || 0) + 1);
      }
    } else if (!opts.skipKnown) {
      // Not skipping known links: surface that it was seen again this run.
      STATE.found++;
    }
  }
  return newCount;
}

// ─────────────────────────────────────────────────────────────────────────
//  Run drivers
// ─────────────────────────────────────────────────────────────────────────

/** Mode 1 / Mode 3 — parse an explicit list of source URLs (with/without scroll). */
async function runListMode(urls, opts, modeLabel = "list") {
  const clean = dedupeUrls(urls);
  resetState(modeLabel, clean.length);
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

/** Mode 2 — keyword search across regional engines, then harvest each result. */
async function runSearchMode(keywords, opts) {
  resetState("search", 0);
  const domainCounts = new Map();
  const targets = [];
  const seenTargets = new Set();

  // Phase A — collect organic result URLs from the SERPs.
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
        if (isExcluded(u, opts.excludeDomains)) continue;
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

  // Phase B — harvest Telegram links from each discovered page.
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

/** Normalize + de-duplicate raw source URLs, dropping blanks. */
function dedupeUrls(urls) {
  const seen = new Set();
  const out = [];
  for (let u of urls) {
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

/** Build a normalized options object from the popup's raw payload. */
function buildOptions(raw = {}) {
  return {
    delayMs: clampInt(raw.delayMs, 0, 60000, 800),
    timeoutMs: clampInt(raw.timeoutMs, 1000, 180000, 15000),
    retries: clampInt(raw.retries, 0, 5, 1),
    maxPerDomain: clampInt(raw.maxPerDomain, 0, 100000, 0),
    openInTab: !!raw.openInTab,
    scroll: !!raw.scroll,
    maxScrolls: clampInt(raw.maxScrolls, 0, 200, 5),
    intervalMs: clampInt(raw.intervalMs, 100, 30000, 1200),
    skipKnown: raw.skipKnown !== false, // default true
    excludeDomains: (raw.excludeDomains || [])
      .map((s) => String(s).trim().toLowerCase().replace(/^https?:\/\//, "").replace(/^www\./, "").replace(/\/.*$/, ""))
      .filter(Boolean),
    stopList: (raw.stopList || []).map((s) => String(s).trim().toLowerCase()).filter(Boolean),
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
//  Mode 4 — periodic monitoring (chrome.alarms)
// ─────────────────────────────────────────────────────────────────────────
const MONITOR_ALARM = "tg_monitor";

/** Configure (or tear down) the recurring monitor alarm + stored config. */
async function setMonitor(cfg) {
  await chrome.storage.local.set({ monitor: cfg });
  await chrome.alarms.clear(MONITOR_ALARM);
  if (cfg.enabled && cfg.urls && cfg.urls.length) {
    chrome.alarms.create(MONITOR_ALARM, {
      periodInMinutes: clampInt(cfg.intervalMinutes, 1, 1440, 60),
    });
  }
}

/** Run a quiet re-scan of the saved monitor URLs; notify on NEW links. */
async function runMonitorScan() {
  if (STATE.running) return; // never clobber a foreground run
  const { monitor } = await chrome.storage.local.get("monitor");
  if (!monitor || !monitor.enabled || !monitor.urls || !monitor.urls.length) return;

  const opts = buildOptions(monitor.options || {});
  const domainCounts = new Map();
  resetState("monitor", monitor.urls.length);
  let totalNew = 0;
  for (let i = 0; i < monitor.urls.length; i++) {
    if (STATE.stopRequested) break;
    totalNew += await parseSource(monitor.urls[i], opts, domainCounts);
    STATE.done = i + 1;
    publishState();
    if (i < monitor.urls.length - 1) await sleep(opts.delayMs);
  }
  finishState();

  if (totalNew > 0) {
    chrome.action.setBadgeText({ text: "NEW" });
    chrome.action.setBadgeBackgroundColor({ color: "#00ff88" });
    try {
      chrome.notifications.create({
        type: "basic",
        iconUrl: "icons/icon128.png",
        title: "TG Link Harvester",
        message: `Monitor found ${totalNew} new Telegram link(s).`,
      });
    } catch (_) {}
  }
}

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === MONITOR_ALARM) runMonitorScan();
});

// ─────────────────────────────────────────────────────────────────────────
//  Message router (popup -> background)
// ─────────────────────────────────────────────────────────────────────────
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    try {
      switch (msg.type) {
        case "START_LIST": {
          if (STATE.running) return sendResponse({ ok: false, error: "already running" });
          runListMode(msg.urls || [], buildOptions(msg.options), "list");
          sendResponse({ ok: true });
          break;
        }
        case "START_PERSONAL": {
          if (STATE.running) return sendResponse({ ok: false, error: "already running" });
          // Personal mode = list mode with scrolling forced on.
          const opts = buildOptions({ ...msg.options, openInTab: true, scroll: true });
          runListMode(msg.urls || [], opts, "personal");
          sendResponse({ ok: true });
          break;
        }
        case "START_SEARCH": {
          if (STATE.running) return sendResponse({ ok: false, error: "already running" });
          runSearchMode(msg.keywords || [], buildOptions(msg.options));
          sendResponse({ ok: true });
          break;
        }
        case "STOP": {
          STATE.stopRequested = true;
          publishState();
          sendResponse({ ok: true });
          break;
        }
        case "GET_STATE":
          sendResponse({ state: { ...STATE } });
          break;
        case "GET_RESULTS":
          sendResponse({ rows: await Store.getAll() });
          break;
        case "GET_COUNT":
          sendResponse({ count: await Store.count() });
          break;
        case "CLEAR_RESULTS":
          await Store.clear();
          STATE.found = 0;
          chrome.action.setBadgeText({ text: "" });
          publishState();
          sendResponse({ ok: true });
          break;
        case "SET_MONITOR":
          await setMonitor(msg.config || {});
          sendResponse({ ok: true });
          break;
        case "GET_MONITOR": {
          const { monitor } = await chrome.storage.local.get("monitor");
          sendResponse({ monitor: monitor || null });
          break;
        }
        case "RUN_MONITOR_NOW":
          runMonitorScan();
          sendResponse({ ok: true });
          break;
        default:
          sendResponse({ ok: false, error: "unknown message" });
      }
    } catch (e) {
      sendResponse({ ok: false, error: String((e && e.message) || e) });
    }
  })();
  return true; // async reply
});

// Clear the badge when switching tabs (unless a run/monitor is active).
chrome.tabs.onActivated.addListener(() => {
  if (!STATE.running) chrome.action.setBadgeText({ text: "" });
});

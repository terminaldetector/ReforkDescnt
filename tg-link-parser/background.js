/**
 * TG HARVESTER — BACKGROUND (Service Worker / Event Page)
 * ---------------------------------------------------------------------------
 * Cross-browser orchestration core, loaded as an ES module so the SAME file
 * runs as a Chrome module service worker AND a Firefox (Desktop/Android) module
 * event page (manifest declares both `service_worker` and `scripts`).
 *
 * Responsibilities:
 *   - Parser/OSINT runs (URL list, keyword search, deep personal-page scroll).
 *   - Periodic monitor via alarms (feature-gated for Firefox Android).
 *   - Relay backup/delete requests to the local Telethon backend (cleaner-core).
 *   - Persist unique link results in IndexedDB (adapter Store) and stream live
 *     progress to the popup.
 * ---------------------------------------------------------------------------
 */

import { ext, Store, Settings, Features, getPlatform } from "./platform-adapter.js";
import { sleep, clampInt, hostOf, isExcluded, dedupeUrls } from "./utils.js";
import * as Parser from "./parser-core.js";
import { Cleaner } from "./cleaner-core.js";

// ── Live run state (mirrored to storage so the popup can re-attach) ─────────
const STATE = {
  running: false, stopRequested: false, mode: null,
  total: 0, done: 0, currentUrl: "", found: 0,
  errors: [], warnings: [], startedAt: 0,
};

function publishState() {
  const snapshot = { ...STATE };
  Settings.set({ parserState: snapshot });
  try {
    if (STATE.running) {
      ext.action.setBadgeText({ text: STATE.found ? String(STATE.found) : "" });
      ext.action.setBadgeBackgroundColor({ color: "#00e5ff" });
    }
  } catch (_) {}
  ext.runtime.sendMessage({ type: "PROGRESS", state: snapshot }).catch(() => {});
}

function resetState(mode, total) {
  Object.assign(STATE, {
    running: true, stopRequested: false, mode, total,
    done: 0, currentUrl: "", found: 0, errors: [], warnings: [], startedAt: Date.now(),
  });
  publishState();
}

function finishState() {
  STATE.running = false;
  STATE.currentUrl = "";
  publishState();
  ext.runtime.sendMessage({ type: "DONE", state: { ...STATE } }).catch(() => {});
}

// ── Networking ──────────────────────────────────────────────────────────────
async function fetchText(url, timeoutMs, retries) {
  let attempt = 0, lastErr = "";
  while (attempt <= retries) {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const resp = await fetch(url, {
        signal: controller.signal, redirect: "follow", credentials: "omit",
        headers: { "Accept-Language": "en,ja;q=0.8,ko;q=0.7,zh;q=0.6" },
      });
      clearTimeout(t);
      return { ok: resp.ok, text: await resp.text(), status: resp.status };
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
 * worker). Harvests the rendered DOM into a blob, optionally auto-scrolling and
 * harvesting after every step (accumulating raw blobs so VIRTUALIZED feeds that
 * drop off-screen nodes are still captured). Returns the joined blob + title;
 * the background does the link extraction.
 */
async function tgPageDriver(opts) {
  const H = globalThis.TGHarvest, S = globalThis.TGScroll;
  const blobs = [];
  const grab = () => { try { blobs.push(H.harvestBlob()); } catch (_) {} };
  grab();
  if (opts && opts.scroll && S) {
    await S.autoScroll({ maxScrolls: opts.maxScrolls, intervalMs: opts.intervalMs, onStep: grab });
  }
  return { blob: blobs.join("\n"), title: document.title || "" };
}

function waitForTabLoad(tabId, timeoutMs) {
  return new Promise((resolve) => {
    let settled = false;
    const done = () => {
      if (settled) return;
      settled = true;
      ext.tabs.onUpdated.removeListener(listener);
      resolve();
    };
    const listener = (id, info) => { if (id === tabId && info.status === "complete") done(); };
    ext.tabs.onUpdated.addListener(listener);
    setTimeout(done, timeoutMs);
  });
}

/**
 * Get a page's content as a normalized result. Default = worker fetch over raw
 * HTML. Tab mode (openInTab OR scroll) = open a background tab, inject the page
 * harvester + scroll helper, run the driver, then extract here.
 * @returns {Promise<{ok, links, title, error?}>}
 */
async function getPageContent(url, opts) {
  if (!opts.openInTab && !opts.scroll) {
    const res = await fetchText(url, opts.timeoutMs, opts.retries);
    if (!res.ok) return { ok: false, links: [], title: "", error: res.error || `HTTP ${res.status}` };
    return { ok: true, links: Parser.extractTelegramLinks(res.text), title: Parser.extractPageTitle(res.text) };
  }
  let tab;
  try {
    tab = await ext.tabs.create({ url, active: false });
    await waitForTabLoad(tab.id, opts.timeoutMs);
    await ext.scripting.executeScript({
      target: { tabId: tab.id },
      files: ["content.js", "scroll-helper.js"],
    });
    const [{ result } = {}] = await ext.scripting.executeScript({
      target: { tabId: tab.id },
      func: tgPageDriver,
      args: [{ scroll: !!opts.scroll, maxScrolls: opts.maxScrolls, intervalMs: opts.intervalMs }],
    });
    const blob = (result && result.blob) || "";
    return { ok: true, links: Parser.extractTelegramLinks(blob), title: (result && result.title) || "" };
  } catch (e) {
    return { ok: false, links: [], title: "", error: String((e && e.message) || e) };
  } finally {
    if (tab && tab.id != null) { try { await ext.tabs.remove(tab.id); } catch (_) {} }
  }
}

// ── Parse one source page ────────────────────────────────────────────────────
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
      key: item.key, source_url: sourceUrl, telegram_link: item.link,
      link_type: item.type, page_title: res.title || "", detected_at: detectedAt,
    });
    if (inserted) {
      newCount++; STATE.found++;
      if (opts.maxPerDomain > 0) {
        const h = hostOf(sourceUrl);
        domainCounts.set(h, (domainCounts.get(h) || 0) + 1);
      }
    } else if (!opts.skipKnown) {
      STATE.found++;
    }
  }
  return newCount;
}

// ── Run drivers ───────────────────────────────────────────────────────────────
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

async function runSearchMode(keywords, opts) {
  resetState("search", 0);
  const domainCounts = new Map();
  const targets = [];
  const seenTargets = new Set();

  for (const kwRaw of keywords) {
    if (STATE.stopRequested) break;
    const kw = kwRaw.trim();
    if (!kw) continue;
    const engine = opts.engine === "auto" ? Parser.detectEngineByLang(kw) : opts.engine;
    let collected = 0;
    for (let page = 0; page < opts.depth && collected < opts.count; page++) {
      if (STATE.stopRequested) break;
      const serpUrl = Parser.buildSerpUrl(engine, kw, page);
      STATE.currentUrl = `[${engine}] ${kw} (p${page + 1})`;
      publishState();
      const res = await fetchText(serpUrl, opts.timeoutMs, opts.retries);
      if (!res.ok) {
        STATE.errors.push({ url: serpUrl, error: res.error || `HTTP ${res.status}` });
        await sleep(opts.delayMs);
        continue;
      }
      const { links, blocked } = Parser.extractSerpResults(engine, res.text, opts.count - collected);
      if (blocked) STATE.warnings.push(`${engine}: blocked / CAPTCHA for "${kw}" (p${page + 1}).`);
      for (const u of links) {
        if (isExcluded(u, opts.excludeDomains)) continue;
        const k = u.replace(/\/+$/, "").toLowerCase();
        if (!seenTargets.has(k)) { seenTargets.add(k); targets.push(u); collected++; }
        if (collected >= opts.count) break;
      }
      await sleep(opts.delayMs);
    }
  }

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
    skipKnown: raw.skipKnown !== false,
    excludeDomains: (raw.excludeDomains || [])
      .map((s) => String(s).trim().toLowerCase().replace(/^https?:\/\//, "").replace(/^www\./, "").replace(/\/.*$/, ""))
      .filter(Boolean),
    stopList: (raw.stopList || []).map((s) => String(s).trim().toLowerCase()).filter(Boolean),
    engine: raw.engine || "auto",
    count: clampInt(raw.count, 1, 100, 10),
    depth: clampInt(raw.depth, 1, 20, 1),
  };
}

// ── Periodic monitor (alarms) ──────────────────────────────────────────────────
const MONITOR_ALARM = "tg_monitor";

async function setMonitor(cfg) {
  await Settings.set({ monitor: cfg });
  if (!Features.alarms) return { ok: false, error: "alarms API unavailable on this platform" };
  await ext.alarms.clear(MONITOR_ALARM);
  if (cfg.enabled && cfg.urls && cfg.urls.length) {
    ext.alarms.create(MONITOR_ALARM, { periodInMinutes: clampInt(cfg.intervalMinutes, 1, 1440, 60) });
  }
  return { ok: true };
}

async function runMonitorScan() {
  if (STATE.running) return;
  const { monitor } = await Settings.get("monitor");
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
    try { ext.action.setBadgeText({ text: "NEW" }); ext.action.setBadgeBackgroundColor({ color: "#00ff88" }); } catch (_) {}
    if (Features.notifications) {
      try {
        ext.notifications.create({
          type: "basic", iconUrl: "icons/icon128.png",
          title: "TG Harvester", message: `Monitor found ${totalNew} new Telegram link(s).`,
        });
      } catch (_) {}
    }
  }
}

if (Features.alarms) {
  ext.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === MONITOR_ALARM) runMonitorScan();
  });
}

// ── Cleaner (backup/delete) backend URL ──────────────────────────────────────
async function backendBase() {
  const { cleanerBackend } = await Settings.get("cleanerBackend");
  return cleanerBackend || "http://127.0.0.1:8787";
}

// ── Message router ────────────────────────────────────────────────────────────
ext.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    try {
      switch (msg.type) {
        // ── Parser ──────────────────────────────────────────────────────────
        case "START_LIST":
          if (STATE.running) return sendResponse({ ok: false, error: "already running" });
          runListMode(msg.urls || [], buildOptions(msg.options), "list");
          return sendResponse({ ok: true });
        case "START_PERSONAL":
          if (STATE.running) return sendResponse({ ok: false, error: "already running" });
          runListMode(msg.urls || [], buildOptions({ ...msg.options, openInTab: true, scroll: true }), "personal");
          return sendResponse({ ok: true });
        case "START_SEARCH":
          if (STATE.running) return sendResponse({ ok: false, error: "already running" });
          runSearchMode(msg.keywords || [], buildOptions(msg.options));
          return sendResponse({ ok: true });
        case "STOP":
          STATE.stopRequested = true; publishState();
          return sendResponse({ ok: true });
        case "GET_STATE":
          return sendResponse({ state: { ...STATE } });
        case "GET_RESULTS":
          return sendResponse({ rows: await Store.getAll() });
        case "GET_COUNT":
          return sendResponse({ count: await Store.count() });
        case "CLEAR_RESULTS":
          await Store.clear(); STATE.found = 0;
          try { ext.action.setBadgeText({ text: "" }); } catch (_) {}
          publishState();
          return sendResponse({ ok: true });

        // ── Platform info (popup adapts UI) ───────────────────────────────────
        case "GET_PLATFORM":
          return sendResponse({ platform: await getPlatform(), features: Features });

        // ── Monitor ───────────────────────────────────────────────────────────
        case "SET_MONITOR":
          return sendResponse(await setMonitor(msg.config || {}));
        case "GET_MONITOR": {
          const { monitor } = await Settings.get("monitor");
          return sendResponse({ monitor: monitor || null });
        }
        case "RUN_MONITOR_NOW":
          runMonitorScan();
          return sendResponse({ ok: true });

        // ── Cleaner (Telethon bridge) ─────────────────────────────────────────
        case "CLEANER_PING":
          return sendResponse(await Cleaner.ping(await backendBase()));
        case "CLEANER_CONFIGURE":
          return sendResponse(await Cleaner.configure(await backendBase(), msg.config || {}));
        case "CLEANER_BACKUP":
          return sendResponse(await Cleaner.startBackup(await backendBase(), msg.config || {}));
        case "CLEANER_DELETE":
          return sendResponse(await Cleaner.startDelete(await backendBase(), msg.config || {}));
        case "CLEANER_STATUS":
          return sendResponse(await Cleaner.jobStatus(await backendBase(), msg.jobId));
        case "CLEANER_ABORT":
          return sendResponse(await Cleaner.abort(await backendBase(), msg.jobId));

        default:
          return sendResponse({ ok: false, error: "unknown message" });
      }
    } catch (e) {
      sendResponse({ ok: false, error: String((e && e.message) || e) });
    }
  })();
  return true; // async reply
});

// Clear the badge when switching tabs (unless a run/monitor is active).
ext.tabs.onActivated.addListener(() => {
  if (!STATE.running) { try { ext.action.setBadgeText({ text: "" }); } catch (_) {} }
});

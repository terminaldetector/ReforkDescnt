/**
 * TG LINK HARVESTER — POPUP UI LOGIC
 * ---------------------------------------------------------------------------
 * Drives the five-tab interface (List / Search / Personal / Results /
 * Settings), persists settings + monitor config, talks to the background
 * worker over chrome.runtime messages, polls live progress, and renders /
 * filters / sorts / exports the harvested results.
 * ---------------------------------------------------------------------------
 */

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

let allRows = [];
let sortKey = "detected_at";
let sortDir = -1; // -1 desc, 1 asc
let pollTimer = null;

/** Promisified runtime.sendMessage. */
function send(msg) {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(msg, (resp) => {
      if (chrome.runtime.lastError) return resolve({ error: chrome.runtime.lastError.message });
      resolve(resp || {});
    });
  });
}

// ── Tab switching ─────────────────────────────────────────────────────────
$$(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    const name = tab.dataset.panel;
    $$(".tab").forEach((t) => t.classList.toggle("active", t === tab));
    $$(".panel").forEach((p) => p.classList.toggle("active", p.dataset.panel === name));
    if (name === "results") refreshResults();
  });
});

// ── Theme toggle (persisted) ────────────────────────────────────────────────
$("#themeBtn").addEventListener("click", () => {
  const next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
  applyTheme(next);
  chrome.storage.local.set({ theme: next });
});
function applyTheme(theme) {
  if (theme === "light") document.documentElement.setAttribute("data-theme", "light");
  else document.documentElement.removeAttribute("data-theme");
}

// ── Settings: read / persist ────────────────────────────────────────────────
const SETTINGS_FIELDS = {
  setDelay: "delaySec", setTimeout: "timeoutSec", setRetries: "retries",
  setMaxDomain: "maxPerDomain", setExclude: "excludeDomains",
  setStopList: "stopList", setSkipKnown: "skipKnown",
  monEnabled: "monEnabled", monInterval: "monInterval",
};

/** Persist all Settings-tab fields to chrome.storage.local. */
function saveSettings() {
  const s = {};
  for (const [id, key] of Object.entries(SETTINGS_FIELDS)) {
    const el = $("#" + id);
    s[key] = el.type === "checkbox" ? el.checked : el.value;
  }
  chrome.storage.local.set({ settings: s });
}

/** Load persisted settings into the Settings-tab fields. */
function loadSettings(s) {
  if (!s) return;
  for (const [id, key] of Object.entries(SETTINGS_FIELDS)) {
    if (s[key] === undefined) continue;
    const el = $("#" + id);
    if (el.type === "checkbox") el.checked = !!s[key];
    else el.value = s[key];
  }
}

// Auto-save settings on any change.
Object.keys(SETTINGS_FIELDS).forEach((id) => {
  $("#" + id).addEventListener("change", saveSettings);
});

/** Build the shared options object the background expects (from Settings). */
function baseOptions() {
  const split = (v) => v.split(",").map((x) => x.trim()).filter(Boolean);
  return {
    delayMs: Math.round(parseFloat($("#setDelay").value || "0") * 1000),
    timeoutMs: Math.round(parseFloat($("#setTimeout").value || "15") * 1000),
    retries: +$("#setRetries").value,
    maxPerDomain: +$("#setMaxDomain").value,
    excludeDomains: split($("#setExclude").value),
    stopList: split($("#setStopList").value),
    skipKnown: $("#setSkipKnown").checked,
  };
}

// ── START / STOP — LIST MODE ────────────────────────────────────────────────
$("#btnStartList").addEventListener("click", async () => {
  const urls = linesOf("#urlList");
  if (!urls.length) return setStatus("statusList", "Enter at least one URL.", "warn");
  const options = {
    ...baseOptions(),
    scroll: $("#listScroll").checked,
    openInTab: $("#listScroll").checked,
    maxScrolls: +$("#listMaxScrolls").value,
    intervalMs: +$("#listInterval").value,
  };
  await startRun({ type: "START_LIST", urls, options }, "statusList");
});
$("#btnStopList").addEventListener("click", stopRun);

// ── START / STOP — SEARCH MODE ──────────────────────────────────────────────
$("#btnStartSearch").addEventListener("click", async () => {
  const keywords = linesOf("#keywords");
  if (!keywords.length) return setStatus("statusSearch", "Enter at least one keyword.", "warn");
  const scroll = $("#searchScroll").checked;
  const options = {
    ...baseOptions(),
    engine: $("#engine").value,
    count: +$("#resCount").value,
    depth: +$("#pageDepth").value,
    scroll,
    openInTab: scroll,
  };
  await startRun({ type: "START_SEARCH", keywords, options }, "statusSearch");
});
$("#btnStopSearch").addEventListener("click", stopRun);

// ── START / STOP — PERSONAL MODE ────────────────────────────────────────────
$("#btnStartPersonal").addEventListener("click", async () => {
  const urls = linesOf("#personalUrls");
  if (!urls.length) return setStatus("statusPersonal", "Enter at least one profile URL.", "warn");
  const options = {
    ...baseOptions(),
    maxScrolls: +$("#personalScrolls").value,
    intervalMs: +$("#personalInterval").value,
  };
  await startRun({ type: "START_PERSONAL", urls, options }, "statusPersonal");
});
$("#btnStopPersonal").addEventListener("click", stopRun);

/** Common start handler: fire the message, flip UI, begin polling. */
async function startRun(message, statusId) {
  const resp = await send(message);
  if (resp.error || resp.ok === false) {
    return setStatus(statusId, "Could not start: " + (resp.error || "busy"), "err");
  }
  setRunningUI(true);
  startPolling();
}

async function stopRun() {
  await send({ type: "STOP" });
  ["statusList", "statusSearch", "statusPersonal"].forEach((id) => setStatus(id, "Stopping…", "warn"));
}

// Engine hint text.
$("#engine").addEventListener("change", () => {
  const hints = {
    auto: "Auto picks engine per keyword.",
    google: "Global Google results.",
    google_jp: "Localized to Japan (hl=ja).",
    google_kr: "Localized to Korea (hl=ko).",
    bing: "Bing global.",
    yahoo_jp: "Yahoo! Japan — best for 日本語.",
    naver: "Naver — best for 한국어.",
    baidu: "Baidu — best for 中文 (TG may be blocked).",
  };
  $("#srEngineHint").textContent = hints[$("#engine").value] || "";
});

// ── Monitoring ──────────────────────────────────────────────────────────────
$("#btnSaveMonitor").addEventListener("click", async () => {
  const config = {
    enabled: $("#monEnabled").checked,
    intervalMinutes: +$("#monInterval").value,
    urls: linesOf("#urlList"),
    options: { ...baseOptions() },
  };
  saveSettings();
  await send({ type: "SET_MONITOR", config });
  setStatus(
    "statusSettings",
    config.enabled
      ? `Monitor ON — ${config.urls.length} URL(s) every ${config.intervalMinutes} min.`
      : "Monitor disabled.",
    config.enabled ? "ok" : "warn"
  );
});
$("#btnRunMonitor").addEventListener("click", async () => {
  await send({ type: "RUN_MONITOR_NOW" });
  setStatus("statusSettings", "Monitor scan started…", "ok");
  setRunningUI(true);
  startPolling();
});

// ── Live progress polling ────────────────────────────────────────────────────
function startPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(pollState, 600);
  pollState();
}
function stopPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = null;
}
async function pollState() {
  const { state } = await send({ type: "GET_STATE" });
  if (!state) return;
  renderProgress(state);
  if (!state.running) {
    stopPolling();
    setRunningUI(false);
    await refreshResults();
  }
}

chrome.runtime.onMessage.addListener((msg) => {
  if (msg.type === "PROGRESS" && msg.state) renderProgress(msg.state);
  if (msg.type === "DONE" && msg.state) {
    renderProgress(msg.state);
    stopPolling();
    setRunningUI(false);
    refreshResults();
  }
});

const STATUS_BY_MODE = {
  list: "statusList", personal: "statusPersonal",
  search: "statusSearch", monitor: "statusSettings",
};

function renderProgress(state) {
  const pct = state.total ? Math.round((state.done / state.total) * 100) : (state.running ? 5 : 0);
  $("#scanFill").style.width = pct + "%";
  $("#statusDot").classList.toggle("active", !!state.running);

  const line =
    `<span class="hl">${state.done}/${state.total || "?"}</span> pages · ` +
    `<span class="ok">${state.found}</span> links · ` +
    `<span class="${state.errors.length ? "err" : ""}">${state.errors.length} err</span>` +
    (state.currentUrl ? `<br>→ ${escapeHtml(state.currentUrl)}` : "");
  const target = STATUS_BY_MODE[state.mode] || "statusList";
  $("#" + target).innerHTML = state.running ? line : `<span class="ok">✓ Done.</span> ${line}`;

  $("#tabCount").textContent = state.found ? `(${state.found})` : "";
  renderLog(state);
}

function renderLog(state) {
  const parts = [];
  (state.warnings || []).slice(-20).forEach((w) => parts.push(`<div class="warn">⚠ ${escapeHtml(w)}</div>`));
  (state.errors || []).slice(-20).forEach((e) =>
    parts.push(`<div class="err">✕ ${escapeHtml(e.url)} — ${escapeHtml(e.error)}</div>`)
  );
  $("#logBox").innerHTML = parts.length ? parts.join("") : "—";
}

function setRunningUI(running) {
  ["#btnStartList", "#btnStartSearch", "#btnStartPersonal", "#btnRunMonitor"].forEach((s) => ($(s).disabled = running));
  ["#btnStopList", "#btnStopSearch", "#btnStopPersonal"].forEach((s) => ($(s).disabled = !running));
}

function setStatus(id, text, cls) {
  $("#" + id).innerHTML = cls ? `<span class="${cls}">${escapeHtml(text)}</span>` : escapeHtml(text);
}

function linesOf(sel) {
  return $(sel).value.split("\n").map((s) => s.trim()).filter(Boolean);
}

// ── Results: load, filter, sort, render ──────────────────────────────────────
async function refreshResults() {
  const { rows } = await send({ type: "GET_RESULTS" });
  allRows = rows || [];
  renderResults();
}

function getView() {
  const type = $("#filterType").value;
  const q = $("#filterText").value.trim().toLowerCase();
  let view = allRows.filter((r) => {
    if (type !== "all" && r.link_type !== type) return false;
    if (q && !(`${r.telegram_link} ${r.source_url} ${r.page_title || ""}`.toLowerCase().includes(q))) return false;
    return true;
  });
  view.sort((a, b) => {
    const av = (a[sortKey] || "").toString();
    const bv = (b[sortKey] || "").toString();
    return av < bv ? -sortDir : av > bv ? sortDir : 0;
  });
  return view;
}

function renderResults() {
  const view = getView();
  $("#resCountPill").textContent = `${view.length}/${allRows.length}`;
  const body = $("#resultsBody");

  if (!view.length) {
    body.innerHTML = `<tr><td colspan="4" class="empty">${
      allRows.length ? "No links match the filter." : "No links yet."
    }</td></tr>`;
    return;
  }

  body.innerHTML = view
    .map((r) => {
      const when = (r.detected_at || "").replace("T", " ").replace(/\..*$/, "").slice(5);
      const srcTitle = (r.page_title ? r.page_title + " — " : "") + r.source_url;
      return `<tr>
        <td><a href="${escapeAttr(r.telegram_link)}" target="_blank" rel="noopener noreferrer">${escapeHtml(r.telegram_link)}</a></td>
        <td><span class="badge ${r.link_type}">${r.link_type}</span></td>
        <td class="src-cell" title="${escapeAttr(srcTitle)}">${escapeHtml(shortHost(r.source_url))}</td>
        <td>${escapeHtml(when)}</td>
      </tr>`;
    })
    .join("");
}

$$("thead th").forEach((th) => {
  th.addEventListener("click", () => {
    const key = th.dataset.sort;
    if (sortKey === key) sortDir = -sortDir;
    else { sortKey = key; sortDir = 1; }
    renderResults();
  });
});
$("#filterType").addEventListener("change", renderResults);
$("#filterText").addEventListener("input", renderResults);

// ── Export: CSV / JSON / clipboard ───────────────────────────────────────────
const CSV_HEADER = ["source_url", "telegram_link", "link_type", "page_title", "detected_at"];

function buildCsv(rows) {
  const esc = (v) => `"${String(v == null ? "" : v).replace(/"/g, '""')}"`;
  const lines = [CSV_HEADER.join(",")];
  rows.forEach((r) => lines.push(CSV_HEADER.map((k) => esc(r[k])).join(",")));
  return lines.join("\n");
}

function download(filename, content, mime) {
  // UTF-8 BOM so CJK opens correctly in Excel.
  const blob = new Blob(["﻿", content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}
function stamp() {
  return new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
}

$("#btnCsv").addEventListener("click", () => {
  const rows = getView();
  if (rows.length) download(`tg_links_${stamp()}.csv`, buildCsv(rows), "text/csv;charset=utf-8");
});
$("#btnJson").addEventListener("click", () => {
  const rows = getView();
  if (!rows.length) return;
  const payload = rows.map((r) => ({
    source_url: r.source_url, telegram_link: r.telegram_link,
    link_type: r.link_type, page_title: r.page_title || "", detected_at: r.detected_at,
  }));
  download(`tg_links_${stamp()}.json`, JSON.stringify(payload, null, 2), "application/json");
});
$("#btnCopy").addEventListener("click", async () => {
  const rows = getView();
  if (!rows.length) return;
  // Copy bare links, one per line — the most common downstream need.
  const text = rows.map((r) => r.telegram_link).join("\n");
  try {
    await navigator.clipboard.writeText(text);
    flashBtn("#btnCopy", "✓ Copied");
  } catch (_) {
    flashBtn("#btnCopy", "✕ Failed");
  }
});
$("#btnClear").addEventListener("click", async () => {
  if (!confirm("Delete ALL harvested links from storage?")) return;
  await send({ type: "CLEAR_RESULTS" });
  allRows = [];
  renderResults();
  $("#tabCount").textContent = "";
});

function flashBtn(sel, text) {
  const el = $(sel);
  const orig = el.textContent;
  el.textContent = text;
  setTimeout(() => (el.textContent = orig), 1200);
}

// ── Utilities ────────────────────────────────────────────────────────────────
function escapeHtml(s) {
  return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
function escapeAttr(s) {
  return escapeHtml(s).replace(/"/g, "&quot;");
}
function shortHost(url) {
  try {
    const u = new URL(url);
    return u.hostname.replace(/^www\./, "") + (u.pathname.length > 1 ? u.pathname : "");
  } catch (_) {
    return url;
  }
}

// ── Init ──────────────────────────────────────────────────────────────────────
(async function init() {
  chrome.storage.local.get(["theme", "settings", "parserState", "monitor"], (data) => {
    applyTheme(data.theme || "dark");
    loadSettings(data.settings);
    if (data.monitor) {
      $("#monEnabled").checked = !!data.monitor.enabled;
      if (data.monitor.intervalMinutes) $("#monInterval").value = data.monitor.intervalMinutes;
    }
    if (data.parserState && data.parserState.running) {
      renderProgress(data.parserState);
      setRunningUI(true);
      startPolling();
    } else if (data.parserState) {
      renderProgress(data.parserState);
    }
  });
  await refreshResults();
})();

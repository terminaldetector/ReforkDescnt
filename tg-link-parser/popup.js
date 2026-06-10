/**
 * TG HARVESTER — POPUP UI (module)
 * ---------------------------------------------------------------------------
 * Drives the four-tab, touch-friendly interface (Parser / Arsenal / Results /
 * Settings), talks to the background over the cross-browser messaging adapter,
 * polls live progress + cleaner-job status, and renders/exports results.
 * ---------------------------------------------------------------------------
 */
import { sendMessage, Settings } from "./platform-adapter.js";
import { escapeHtml, escapeAttr, fileStamp, weightToBatch } from "./utils.js";

const $ = (s) => document.querySelector(s);
const $$ = (s) => Array.from(document.querySelectorAll(s));

let allRows = [];
let sortKey = "detected_at";
let sortDir = -1;
let pollTimer = null;
let jobTimer = null;
const WEIGHTS = ["light", "medium", "heavy"];

// ── Tabs ─────────────────────────────────────────────────────────────────────
$$(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    const name = tab.dataset.panel;
    $$(".tab").forEach((t) => t.classList.toggle("active", t === tab));
    $$(".panel").forEach((p) => p.classList.toggle("active", p.dataset.panel === name));
    if (name === "results") refreshResults();
  });
});

// ── Theme ──────────────────────────────────────────────────────────────────────
$("#themeBtn").addEventListener("click", () => {
  const next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
  applyTheme(next);
  Settings.set({ theme: next });
});
function applyTheme(t) {
  if (t === "light") document.documentElement.setAttribute("data-theme", "light");
  else document.documentElement.removeAttribute("data-theme");
}

// ── Parser: mode-dependent UI ────────────────────────────────────────────────
function syncParserMode() {
  const mode = $("#parseMode").value;
  $$("[data-mode]").forEach((el) => {
    el.style.display = el.dataset.mode.split(" ").includes(mode) ? "" : "none";
  });
  const labels = {
    list: "Source URLs (one per line)",
    search: "Keywords (one per line · JP/KR/CN)",
    personal: "Profile / feed URLs (one per line)",
  };
  $("#parseInputLabel").textContent = labels[mode];
  // Personal mode always scrolls; hide the toggle but keep scroll opts.
  $("#scrollOpts").style.display = (mode === "personal" || $("#parseScroll").checked || mode === "search") ? "" : "none";
}
$("#parseMode").addEventListener("change", syncParserMode);
$("#parseScroll").addEventListener("change", syncParserMode);

// ── Settings persistence ──────────────────────────────────────────────────────
const SETTINGS_FIELDS = {
  setDelay: "delaySec", setTimeout: "timeoutSec", setRetries: "retries",
  setMaxDomain: "maxPerDomain", setExclude: "excludeDomains", setStopList: "stopList",
  setSkipKnown: "skipKnown", monEnabled: "monEnabled", monInterval: "monInterval",
  setBackend: "backend", setApiId: "apiId",
};
function saveSettings() {
  const s = {};
  for (const [id, key] of Object.entries(SETTINGS_FIELDS)) {
    const el = $("#" + id);
    s[key] = el.type === "checkbox" ? el.checked : el.value;
  }
  Settings.set({ settings: s });
}
function loadSettings(s) {
  if (!s) return;
  for (const [id, key] of Object.entries(SETTINGS_FIELDS)) {
    if (s[key] === undefined) continue;
    const el = $("#" + id);
    if (el.type === "checkbox") el.checked = !!s[key];
    else el.value = s[key];
  }
}
Object.keys(SETTINGS_FIELDS).forEach((id) => $("#" + id).addEventListener("change", saveSettings));

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

// ── Parser start/stop ─────────────────────────────────────────────────────────
$("#btnStart").addEventListener("click", async () => {
  const mode = $("#parseMode").value;
  const lines = $("#parseInput").value.split("\n").map((s) => s.trim()).filter(Boolean);
  if (!lines.length) return setStatus("statusParser", "Enter input first.", "warn");

  const scroll = mode === "personal" ? true : $("#parseScroll").checked;
  const common = {
    ...baseOptions(),
    scroll, openInTab: scroll,
    maxScrolls: +$("#maxScrolls").value, intervalMs: +$("#scrollInterval").value,
  };

  let msg;
  if (mode === "search") {
    msg = {
      type: "START_SEARCH", keywords: lines,
      options: { ...common, engine: $("#engine").value, count: +$("#resCount").value, depth: +$("#pageDepth").value },
    };
  } else if (mode === "personal") {
    msg = { type: "START_PERSONAL", urls: lines, options: common };
  } else {
    msg = { type: "START_LIST", urls: lines, options: common };
  }

  const resp = await sendMessage(msg);
  if (resp.error || resp.ok === false) return setStatus("statusParser", "Could not start: " + (resp.error || "busy"), "err");
  setRunningUI(true);
  startPolling();
});
$("#btnStop").addEventListener("click", async () => {
  await sendMessage({ type: "STOP" });
  setStatus("statusParser", "Stopping…", "warn");
});

// ── Progress polling ────────────────────────────────────────────────────────────
function startPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(pollState, 600);
  pollState();
}
function stopPolling() { if (pollTimer) clearInterval(pollTimer); pollTimer = null; }
async function pollState() {
  const { state } = await sendMessage({ type: "GET_STATE" });
  if (!state) return;
  renderProgress(state);
  if (!state.running) { stopPolling(); setRunningUI(false); await refreshResults(); }
}
let runtimeMsg = (msg) => {
  if (msg.type === "PROGRESS" && msg.state) renderProgress(msg.state);
  if (msg.type === "DONE" && msg.state) { renderProgress(msg.state); stopPolling(); setRunningUI(false); refreshResults(); }
};
try { (window.browser || window.chrome).runtime.onMessage.addListener(runtimeMsg); } catch (_) {}

function renderProgress(state) {
  const pct = state.total ? Math.round((state.done / state.total) * 100) : (state.running ? 5 : 0);
  $("#scanFill").style.width = pct + "%";
  $("#statusDot").classList.toggle("active", !!state.running);
  const line =
    `<span class="hl">${state.done}/${state.total || "?"}</span> pages · ` +
    `<span class="ok">${state.found}</span> links · ` +
    `<span class="${state.errors.length ? "err" : ""}">${state.errors.length} err</span>` +
    (state.currentUrl ? `<br>→ ${escapeHtml(state.currentUrl)}` : "");
  const target = state.mode === "monitor" ? "statusSettings" : "statusParser";
  $("#" + target).innerHTML = state.running ? line : `<span class="ok">✓ Done.</span> ${line}`;
  $("#tabCount").textContent = state.found ? `(${state.found})` : "";
  renderLog(state);
}
function renderLog(state) {
  const parts = [];
  (state.warnings || []).slice(-20).forEach((w) => parts.push(`<div class="warn">⚠ ${escapeHtml(w)}</div>`));
  (state.errors || []).slice(-20).forEach((e) => parts.push(`<div class="err">✕ ${escapeHtml(e.url)} — ${escapeHtml(e.error)}</div>`));
  $("#logBox").innerHTML = parts.length ? parts.join("") : "—";
}
function setRunningUI(running) {
  $("#btnStart").disabled = running;
  $("#btnStop").disabled = !running;
  $("#btnRunMonitor").disabled = running;
}
function setStatus(id, text, cls) {
  $("#" + id).innerHTML = cls ? `<span class="${cls}">${escapeHtml(text)}</span>` : escapeHtml(text);
}

// ── Monitor ──────────────────────────────────────────────────────────────────────
$("#btnSaveMonitor").addEventListener("click", async () => {
  const config = {
    enabled: $("#monEnabled").checked,
    intervalMinutes: +$("#monInterval").value,
    urls: $("#parseInput").value.split("\n").map((s) => s.trim()).filter(Boolean),
    options: { ...baseOptions() },
  };
  saveSettings();
  const resp = await sendMessage({ type: "SET_MONITOR", config });
  if (resp.ok === false) return setStatus("statusSettings", "Monitor: " + resp.error, "err");
  setStatus("statusSettings", config.enabled
    ? `Monitor ON — ${config.urls.length} URL(s) / ${config.intervalMinutes} min.`
    : "Monitor disabled.", config.enabled ? "ok" : "warn");
});
$("#btnRunMonitor").addEventListener("click", async () => {
  await sendMessage({ type: "RUN_MONITOR_NOW" });
  setStatus("statusSettings", "Monitor scan started…", "ok");
  setRunningUI(true); startPolling();
});

// ── Arsenal: weight sliders ────────────────────────────────────────────────────
function bindWeight(sliderId, labelId) {
  const upd = () => { $("#" + labelId).textContent = weightToBatch(WEIGHTS[+$("#" + sliderId).value]).label; };
  $("#" + sliderId).addEventListener("input", upd);
  upd();
}
bindWeight("bkWeight", "bkWeightLabel");
bindWeight("delWeight", "delWeightLabel");

// Delete-scope conditional fields.
$("#delScope").addEventListener("change", () => {
  const scope = $("#delScope").value;
  $$("[data-del]").forEach((el) => {
    el.style.display = el.dataset.del.split(" ").includes(scope) ? "" : "none";
  });
});

// ── Cleaner: backend + jobs ─────────────────────────────────────────────────────
function cleanerLog(text, cls) {
  $("#cleanerLog").innerHTML = (cls ? `<span class="${cls}">` : "") + escapeHtml(text) + (cls ? "</span>" : "");
}
$("#btnPing").addEventListener("click", async () => {
  cleanerLog("Pinging backend…");
  const r = await sendMessage({ type: "CLEANER_PING" });
  if (!r.ok) return setStatus("cleanerStatus", "Backend: " + (r.error || "offline"), "err");
  setStatus("cleanerStatus",
    r.authorized ? `Backend OK — logged in as ${r.me || "?"}` : "Backend OK — not logged in (run backend console login)",
    r.authorized ? "ok" : "warn");
});

$("#btnBackup").addEventListener("click", async () => {
  const cfg = {
    dialog: $("#bkDialog").value.trim() || null,
    withMedia: $("#bkMedia").checked,
    skipDuplicates: $("#bkSkipDup").checked,
    batch: weightToBatch(WEIGHTS[+$("#bkWeight").value]),
  };
  cleanerLog("Starting backup…");
  const r = await sendMessage({ type: "CLEANER_BACKUP", config: cfg });
  if (!r.ok) return cleanerLog("Backup failed: " + r.error, "err");
  pollJob(r.jobId);
});

$("#btnDelete").addEventListener("click", async () => {
  const scope = $("#delScope").value;
  const dryRun = $("#delDryRun").checked;
  // Hard gate for real (non-dry) destructive runs.
  if (!dryRun && $("#delConfirm").value.trim() !== "DELETE") {
    return cleanerLog('Type DELETE in the confirm box for a real run (or keep Dry run on).', "warn");
  }
  if (!dryRun && scope === "wipe-everything" &&
      !confirm("WIPE EVERYTHING will permanently delete ALL your messages across all dialogs (except whitelist). This cannot be undone. Continue?")) {
    return;
  }
  const cfg = {
    scope,
    dialog: $("#delDialog").value.trim() || null,
    olderThanDays: +$("#delOlderThan").value,
    mediaType: $("#delMediaType").value,
    wipeEverything: scope === "wipe-everything",
    whitelist: $("#delWhitelist").value.split("\n").map((s) => s.trim()).filter(Boolean),
    dryRun,
    confirmToken: dryRun ? null : "DELETE",
    batch: weightToBatch(WEIGHTS[+$("#delWeight").value]),
  };
  cleanerLog((dryRun ? "Dry-run" : "DELETE") + " starting…");
  const r = await sendMessage({ type: "CLEANER_DELETE", config: cfg });
  if (!r.ok) return cleanerLog("Cleaner failed: " + r.error, "err");
  pollJob(r.jobId);
});

$("#btnAbortJob").addEventListener("click", async () => {
  const jobId = $("#btnAbortJob").dataset.job;
  if (jobId) await sendMessage({ type: "CLEANER_ABORT", jobId });
});

/** Poll a cleaner job until it finishes, streaming its log. */
function pollJob(jobId) {
  if (!jobId) return;
  $("#btnAbortJob").disabled = false;
  $("#btnAbortJob").dataset.job = jobId;
  if (jobTimer) clearInterval(jobTimer);
  jobTimer = setInterval(async () => {
    const r = await sendMessage({ type: "CLEANER_STATUS", jobId });
    if (!r.ok) { clearInterval(jobTimer); return cleanerLog("Status error: " + r.error, "err"); }
    const log = (r.log || []).slice(-12).map(escapeHtml).join("<br>");
    $("#cleanerLog").innerHTML = `<div class="hl">${escapeHtml(r.status || "")} ${r.progress != null ? r.progress + "%" : ""}</div>${log}`;
    if (r.done) {
      clearInterval(jobTimer);
      $("#btnAbortJob").disabled = true;
    }
  }, 800);
}

$("#btnSaveCreds").addEventListener("click", async () => {
  const backend = $("#setBackend").value.trim() || "http://127.0.0.1:8787";
  await Settings.set({ cleanerBackend: backend });
  saveSettings();
  const apiId = $("#setApiId").value.trim();
  const apiHash = $("#setApiHash").value.trim();
  if (apiId && apiHash) {
    const r = await sendMessage({ type: "CLEANER_CONFIGURE", config: { apiId, apiHash } });
    setStatus("statusSettings", r.ok ? "Credentials sent to local backend." : "Backend: " + r.error, r.ok ? "ok" : "err");
  } else {
    setStatus("statusSettings", "Backend URL saved (no credentials entered).", "ok");
  }
});

// ── Results ──────────────────────────────────────────────────────────────────────
async function refreshResults() {
  const { rows } = await sendMessage({ type: "GET_RESULTS" });
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
    const av = (a[sortKey] || "").toString(), bv = (b[sortKey] || "").toString();
    return av < bv ? -sortDir : av > bv ? sortDir : 0;
  });
  return view;
}
function renderResults() {
  const view = getView();
  $("#resCountPill").textContent = `${view.length}/${allRows.length}`;
  const body = $("#resultsBody");
  if (!view.length) {
    body.innerHTML = `<tr><td colspan="4" class="empty">${allRows.length ? "No links match the filter." : "No links yet."}</td></tr>`;
    return;
  }
  body.innerHTML = view.map((r) => {
    const when = (r.detected_at || "").replace("T", " ").replace(/\..*$/, "").slice(5);
    const srcTitle = (r.page_title ? r.page_title + " — " : "") + r.source_url;
    return `<tr>
      <td><a href="${escapeAttr(r.telegram_link)}" target="_blank" rel="noopener noreferrer">${escapeHtml(r.telegram_link)}</a></td>
      <td><span class="badge ${r.link_type}">${r.link_type}</span></td>
      <td class="src-cell" title="${escapeAttr(srcTitle)}">${escapeHtml(shortHost(r.source_url))}</td>
      <td>${escapeHtml(when)}</td>
    </tr>`;
  }).join("");
}
$$("thead th").forEach((th) => th.addEventListener("click", () => {
  const key = th.dataset.sort;
  if (sortKey === key) sortDir = -sortDir; else { sortKey = key; sortDir = 1; }
  renderResults();
}));
$("#filterType").addEventListener("change", renderResults);
$("#filterText").addEventListener("input", renderResults);

// ── Export ──────────────────────────────────────────────────────────────────────
const CSV_HEADER = ["source_url", "telegram_link", "link_type", "page_title", "detected_at"];
function buildCsv(rows) {
  const esc = (v) => `"${String(v == null ? "" : v).replace(/"/g, '""')}"`;
  return [CSV_HEADER.join(","), ...rows.map((r) => CSV_HEADER.map((k) => esc(r[k])).join(","))].join("\n");
}
function download(filename, content, mime) {
  const blob = new Blob(["﻿", content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.download = filename; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}
$("#btnCsv").addEventListener("click", () => { const r = getView(); if (r.length) download(`tg_links_${fileStamp()}.csv`, buildCsv(r), "text/csv;charset=utf-8"); });
$("#btnJson").addEventListener("click", () => {
  const r = getView(); if (!r.length) return;
  const payload = r.map((x) => ({ source_url: x.source_url, telegram_link: x.telegram_link, link_type: x.link_type, page_title: x.page_title || "", detected_at: x.detected_at }));
  download(`tg_links_${fileStamp()}.json`, JSON.stringify(payload, null, 2), "application/json");
});
$("#btnCopy").addEventListener("click", async () => {
  const r = getView(); if (!r.length) return;
  try { await navigator.clipboard.writeText(r.map((x) => x.telegram_link).join("\n")); flashBtn("#btnCopy", "✓ Copied"); }
  catch (_) { flashBtn("#btnCopy", "✕ Failed"); }
});
$("#btnClear").addEventListener("click", async () => {
  if (!confirm("Delete ALL harvested links from storage?")) return;
  await sendMessage({ type: "CLEAR_RESULTS" });
  allRows = []; renderResults(); $("#tabCount").textContent = "";
});
function flashBtn(sel, text) { const el = $(sel); const o = el.textContent; el.textContent = text; setTimeout(() => (el.textContent = o), 1200); }

// ── Utils ──────────────────────────────────────────────────────────────────────
function shortHost(url) {
  try { const u = new URL(url); return u.hostname.replace(/^www\./, "") + (u.pathname.length > 1 ? u.pathname : ""); }
  catch (_) { return url; }
}

// ── Init ──────────────────────────────────────────────────────────────────────
(async function init() {
  const data = await Settings.get(["theme", "settings", "parserState", "monitor", "cleanerBackend"]);
  applyTheme(data.theme || "dark");
  loadSettings(data.settings);
  if (data.cleanerBackend) $("#setBackend").value = data.cleanerBackend;
  if (data.monitor) {
    $("#monEnabled").checked = !!data.monitor.enabled;
    if (data.monitor.intervalMinutes) $("#monInterval").value = data.monitor.intervalMinutes;
  }
  syncParserMode();
  $("#delScope").dispatchEvent(new Event("change"));

  // Adapt the subtitle to the detected platform (mobile awareness).
  const { platform } = await sendMessage({ type: "GET_PLATFORM" });
  if (platform && platform.isAndroid) {
    $("#platformSub").textContent = "Firefox Android · touch mode";
    document.body.classList.add("mobile");
  }

  if (data.parserState) {
    renderProgress(data.parserState);
    if (data.parserState.running) { setRunningUI(true); startPolling(); }
  }
  await refreshResults();
})();

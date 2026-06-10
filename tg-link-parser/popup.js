/**
 * TG LINK HARVESTER — POPUP UI LOGIC
 * ---------------------------------------------------------------------------
 * Drives the three-tab interface, talks to the background worker over
 * chrome.runtime messages, polls live progress, and renders/filters/sorts/
 * exports the harvested results.
 * ---------------------------------------------------------------------------
 */

// Tiny DOM helpers.
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

// Local cache of all rows + current view settings.
let allRows = [];
let sortKey = "parsed_at";
let sortDir = -1; // -1 = desc, 1 = asc
let pollTimer = null;

// ─────────────────────────────────────────────────────────────────────────
//  Messaging helper (promisified runtime.sendMessage)
// ─────────────────────────────────────────────────────────────────────────
function send(msg) {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(msg, (resp) => {
      if (chrome.runtime.lastError) return resolve({ error: chrome.runtime.lastError.message });
      resolve(resp || {});
    });
  });
}

// ─────────────────────────────────────────────────────────────────────────
//  Tab switching
// ─────────────────────────────────────────────────────────────────────────
$$(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    const name = tab.dataset.panel;
    $$(".tab").forEach((t) => t.classList.toggle("active", t === tab));
    $$(".panel").forEach((p) => p.classList.toggle("active", p.dataset.panel === name));
    if (name === "results") refreshResults();
  });
});

// ─────────────────────────────────────────────────────────────────────────
//  Theme toggle (persisted)
// ─────────────────────────────────────────────────────────────────────────
$("#themeBtn").addEventListener("click", () => {
  const cur = document.documentElement.getAttribute("data-theme");
  const next = cur === "light" ? "dark" : "light";
  applyTheme(next);
  chrome.storage.local.set({ theme: next });
});
function applyTheme(theme) {
  if (theme === "light") document.documentElement.setAttribute("data-theme", "light");
  else document.documentElement.removeAttribute("data-theme");
}

// ─────────────────────────────────────────────────────────────────────────
//  Read shared options from the Options panel
// ─────────────────────────────────────────────────────────────────────────
function readOptions() {
  return {
    delayMs: +$("#optDelay").value,
    timeoutMs: +$("#optTimeout").value,
    retries: +$("#optRetries").value,
    maxPerDomain: +$("#optMaxDomain").value,
    openInTab: $("#optOpenTab").checked,
    stopList: $("#optStopList").value.split(",").map((s) => s.trim()).filter(Boolean),
  };
}

// ─────────────────────────────────────────────────────────────────────────
//  START / STOP — URL LIST MODE
// ─────────────────────────────────────────────────────────────────────────
$("#btnStartList").addEventListener("click", async () => {
  const urls = $("#urlList").value.split("\n").map((s) => s.trim()).filter(Boolean);
  if (!urls.length) {
    setStatus("statusList", "Enter at least one URL.", "warn");
    return;
  }
  const resp = await send({ type: "START_LIST", urls, options: readOptions() });
  if (resp.error || resp.ok === false) {
    setStatus("statusList", "Could not start: " + (resp.error || "busy"), "err");
    return;
  }
  setRunningUI(true);
  startPolling();
});

$("#btnStopList").addEventListener("click", stopRun);

// ─────────────────────────────────────────────────────────────────────────
//  START / STOP — KEYWORD SEARCH MODE
// ─────────────────────────────────────────────────────────────────────────
$("#btnStartSearch").addEventListener("click", async () => {
  const keywords = $("#keywords").value.split("\n").map((s) => s.trim()).filter(Boolean);
  if (!keywords.length) {
    setStatus("statusSearch", "Enter at least one keyword.", "warn");
    return;
  }
  const options = {
    ...readOptions(),
    engine: $("#engine").value,
    count: +$("#resCount").value,
    depth: +$("#pageDepth").value,
  };
  const resp = await send({ type: "START_SEARCH", keywords, options });
  if (resp.error || resp.ok === false) {
    setStatus("statusSearch", "Could not start: " + (resp.error || "busy"), "err");
    return;
  }
  setRunningUI(true);
  startPolling();
});

$("#btnStopSearch").addEventListener("click", stopRun);

async function stopRun() {
  await send({ type: "STOP" });
  setStatus("statusList", "Stopping…", "warn");
  setStatus("statusSearch", "Stopping…", "warn");
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

// ─────────────────────────────────────────────────────────────────────────
//  Live progress polling
// ─────────────────────────────────────────────────────────────────────────
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

// Also react to pushed messages (faster than polling when popup is open).
chrome.runtime.onMessage.addListener((msg) => {
  if (msg.type === "PROGRESS" && msg.state) renderProgress(msg.state);
  if (msg.type === "DONE" && msg.state) {
    renderProgress(msg.state);
    stopPolling();
    setRunningUI(false);
    refreshResults();
  }
});

function renderProgress(state) {
  const pct = state.total ? Math.round((state.done / state.total) * 100) : (state.running ? 5 : 0);
  $("#scanFill").style.width = pct + "%";
  $("#statusDot").classList.toggle("active", !!state.running);

  const line =
    `<span class="hl">${state.done}/${state.total || "?"}</span> pages · ` +
    `<span class="ok">${state.found}</span> links · ` +
    `<span class="${state.errors.length ? "err" : ""}">${state.errors.length} err</span>` +
    (state.currentUrl ? `<br>→ ${escapeHtml(state.currentUrl)}` : "");
  const target = state.mode === "search" ? "statusSearch" : "statusList";
  $("#" + target).innerHTML = state.running
    ? line
    : `<span class="ok">✓ Done.</span> ${line}`;

  // Update Results tab count badge.
  $("#tabCount").textContent = state.found ? `(${state.found})` : "";
  renderLog(state);
}

function renderLog(state) {
  const parts = [];
  (state.warnings || []).forEach((w) => parts.push(`<div class="warn">⚠ ${escapeHtml(w)}</div>`));
  (state.errors || []).slice(-20).forEach((e) =>
    parts.push(`<div class="err">✕ ${escapeHtml(e.url)} — ${escapeHtml(e.error)}</div>`)
  );
  $("#logBox").innerHTML = parts.length ? parts.join("") : "—";
}

function setRunningUI(running) {
  $("#btnStartList").disabled = running;
  $("#btnStartSearch").disabled = running;
  $("#btnStopList").disabled = !running;
  $("#btnStopSearch").disabled = !running;
}

function setStatus(id, text, cls) {
  const el = $("#" + id);
  el.innerHTML = cls ? `<span class="${cls}">${escapeHtml(text)}</span>` : escapeHtml(text);
}

// ─────────────────────────────────────────────────────────────────────────
//  Results: load, filter, sort, render
// ─────────────────────────────────────────────────────────────────────────
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
    if (q && !(`${r.telegram_link} ${r.source_url}`.toLowerCase().includes(q))) return false;
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
      const when = (r.parsed_at || "").replace("T", " ").replace(/\..*$/, "").slice(5);
      return `<tr>
        <td><a href="${escapeAttr(r.telegram_link)}" target="_blank" rel="noopener noreferrer">${escapeHtml(r.telegram_link)}</a></td>
        <td><span class="badge ${r.link_type}">${r.link_type}</span></td>
        <td class="src-cell" title="${escapeAttr(r.source_url)}">${escapeHtml(shortHost(r.source_url))}</td>
        <td>${escapeHtml(when)}</td>
      </tr>`;
    })
    .join("");
}

// Sorting via header clicks.
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

// ─────────────────────────────────────────────────────────────────────────
//  Export: CSV / JSON / clipboard
// ─────────────────────────────────────────────────────────────────────────
const CSV_HEADER = ["source_url", "telegram_link", "link_type", "parsed_at"];

function buildCsv(rows) {
  const esc = (v) => `"${String(v == null ? "" : v).replace(/"/g, '""')}"`;
  const lines = [CSV_HEADER.join(",")];
  rows.forEach((r) => lines.push(CSV_HEADER.map((k) => esc(r[k])).join(",")));
  return lines.join("\n");
}

function download(filename, content, mime) {
  // Prepend a UTF-8 BOM so CJK characters open correctly in Excel.
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
  if (!rows.length) return;
  download(`tg_links_${stamp()}.csv`, buildCsv(rows), "text/csv;charset=utf-8");
});

$("#btnJson").addEventListener("click", () => {
  const rows = getView();
  if (!rows.length) return;
  const payload = rows.map((r) => ({
    source_url: r.source_url,
    telegram_link: r.telegram_link,
    link_type: r.link_type,
    parsed_at: r.parsed_at,
  }));
  download(`tg_links_${stamp()}.json`, JSON.stringify(payload, null, 2), "application/json");
});

$("#btnCopy").addEventListener("click", async () => {
  const rows = getView();
  if (!rows.length) return;
  try {
    await navigator.clipboard.writeText(buildCsv(rows));
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

// ─────────────────────────────────────────────────────────────────────────
//  Small utilities
// ─────────────────────────────────────────────────────────────────────────
function escapeHtml(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
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

// ─────────────────────────────────────────────────────────────────────────
//  Init on open
// ─────────────────────────────────────────────────────────────────────────
(async function init() {
  // Restore theme.
  chrome.storage.local.get(["theme", "parserState"], (data) => {
    applyTheme(data.theme || "dark");
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

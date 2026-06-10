# 🔗 TG Link Harvester — Telegram Channel Link Parser (JP / KR / CN)

A **Chrome Extension (Manifest V3)** that scans a list of web pages — or
regional search-engine results — and harvests every unique **Telegram channel,
group and invite link** it can find, then lets you export them to **CSV / JSON**
or copy them to the clipboard.

Built for multilingual sources (Japanese / Korean / Chinese): it understands
region-specific search engines (Yahoo! Japan, Naver, Baidu, Google JP/KR) and
normalizes full-width (zenkaku) characters so links like `ｔ．ｍｅ／ｃｈ` are still detected.

```
┌─────────────── popup.html / popup.js (3 tabs) ───────────────┐
│  URL List   │   Keyword Search   │   Results (filter/export)  │
└───────┬──────────────┬──────────────────────┬────────────────┘
        │  messages     │                      │
        ▼               ▼                      ▼
┌───────────────────── background.js (service worker) ─────────┐
│  queue · fetch(timeout/delay/retry) · pagination             │
│  extractor.js  (regex link finder + classifier + dedup)      │
│  searchEngines.js (SERP URL builder + result unwrapping)     │
│  IndexedDB  (results store — handles tens of thousands)      │
└───────────────────────────┬──────────────────────────────────┘
                            │ (optional "tab" mode)
                            ▼
                content.js — reads the rendered DOM
                (recursive walk incl. Shadow DOM + text nodes)
```

---

## Install (Developer Mode)

1. Open `chrome://extensions/`
2. Enable **Developer mode** (top-right)
3. Click **Load unpacked**
4. Select the `tg-link-parser/` folder
5. The 🔗 icon appears in the toolbar

> Icons are pre-generated. To regenerate them: `python3 make_icons.py` (needs `pip install Pillow`).

---

## Usage

### Tab 1 — URL List
Paste source URLs (one per line) and press **▶ START**. Each page is fetched,
all Telegram links are extracted and stored uniquely.

### Tab 2 — Keyword Search
Enter keywords (JP/KR/CN supported), choose a search engine (or **Auto** — it
picks one per keyword based on the script), set *results per keyword* and
*pagination depth*, then **▶ SEARCH & HARVEST**. The extension queries the
SERPs, collects the organic result URLs, then harvests Telegram links from each.

### Tab 3 — Results
Filter by type, free-text search, sort by any column, and export:
- **⬇ CSV** — `source_url,telegram_link,link_type,parsed_at` (UTF-8 + BOM for Excel)
- **⬇ JSON** — same fields, pretty-printed
- **⧉ Copy** — CSV to clipboard
- **✕ Clear** — wipe the IndexedDB store

### ⚙ Options (shared by both modes)
| Option | Meaning |
|---|---|
| Delay (ms) | Wait between requests (anti-ban / politeness) |
| Timeout (ms) | Per-request abort timeout |
| Retries | Retry budget per URL on network error |
| Max links / domain | Cap links taken from one source domain (0 = ∞) |
| Stop-list | Comma-separated substrings to ignore (e.g. your own channel) |
| Open in tab | Open each page in a background tab and read the **rendered** DOM (incl. Shadow DOM) — slower, but works on JS-heavy pages |

---

## What it detects

| Pattern | Example | Type |
|---|---|---|
| `t.me/joinchat/…`, `t.me/+…` | `t.me/+XyZ_789` | **invite** |
| `t.me/s/…` | `t.me/s/korean_channel` | **channel** |
| `t.me/c/…` | `t.me/c/123/45` | **group** |
| bare username | `t.me/animehub_jp` | **channel** |
| `addstickers` / `addtheme` / bots | `t.me/somebot` | **other** |

Links are found in `href`, `src`, `onclick`, `data-*` attributes **and** in bare
page text. Detection covers `t.me`, `telegram.me`, `telegram.dog`, with or
without scheme/`www.`. All results are de-duplicated (scheme/`www.`/query/
fragment/case stripped for the dedup key).

---

## Regional search engines & pagination

| Language | Engine | Pagination param |
|---|---|---|
| 日本語 | `search.yahoo.co.jp`, `google.co.jp` | `b=` (1,11,21…), `start=` |
| 한국어 | `search.naver.com`, `google.co.kr` | `start=` (1,11,21…) |
| 中文 | `baidu.com` | `pn=` (0,10,20…) — destination read from `mu=` |
| — | Google, Bing | `start=`, `first=` |

Baidu naturally receives the worker's desktop-Chrome User-Agent, which it
requires. Telegram is blocked in CN, but Chinese pages often republish
work-around links — those are still harvested.

---

## Ethics & safety

- ✅ Collects **public** data only — respect Telegram's and the search engines' ToS.
- 🚫 Never follows invite links automatically; never asks for Telegram login/password.
- 🚫 Never bypasses CAPTCHAs. If a SERP is blocked, a **warning** is shown in
  Results — solve it manually in a normal tab or increase the delay.
- 🟥 A **■ STOP** button aborts a run at any time; errors are logged per-URL.

---

## File layout

```
tg-link-parser/
├── manifest.json       MV3 config (storage, tabs, scripting, host *://*/*)
├── background.js       Service worker: queue, fetch, pagination, IndexedDB
├── extractor.js        DOM-free Telegram-link regex/classify/normalize
├── searchEngines.js    SERP URL builder + result-link unwrapping
├── content.js          Rendered-DOM harvester (Shadow DOM + text nodes)
├── popup.html          3-tab UI
├── popup.js            UI logic, polling, filter/sort, export
├── styles.css          Dark/light neon theme (flex/grid)
├── make_icons.py       Icon generator
└── icons/              16 / 48 / 128 px
```

---

## Acceptance criteria — mapping

1. **Installs without errors** → clean MV3 manifest, no external deps.
2. **List of URLs → all TG links** → Tab 1 + `extractor.js`.
3. **Keyword → real channels via Google/Yahoo JP** → Tab 2 + `searchEngines.js`.
4. **CSV & JSON export** → Tab 3 export buttons.
5. **Unique links, correct types** → IndexedDB keyPath dedup + `classifyLink`.
6. **No freeze on 50+ pages** → async queue with delays; progress polled, results in IndexedDB.

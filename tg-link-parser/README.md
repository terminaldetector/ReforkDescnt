# 🔗 TG Link Harvester — Telegram Channel Link Parser (JP / KR / CN)

A **Chrome Extension (Manifest V3)** that deep-scans web pages — including
**dynamic / infinite-scroll personal pages** (Twitter, Instagram, Medium,
Ameba, Naver Blog, Weibo, Douban…) and **regional search results** — and
harvests every unique **Telegram channel, group, invite and bot link**, then
exports them to **CSV / JSON** or the clipboard.

Tuned for multilingual sources (Japanese / Korean / Chinese): it understands
region-specific search engines (Yahoo! Japan, Naver, Baidu, Google JP/KR),
normalizes full-width (zenkaku) characters, and reads links from `href`,
`data-*` attributes, `<script>` bodies, Shadow DOM and bare text.

```
┌────────── popup.html / popup.js (5 tabs) ──────────┐
│ List │ Search │ Personal │ Results │ ⚙ Settings    │
└───┬───────┬────────┬──────────┬──────────┬──────────┘
    │ messages                            │
    ▼                                     ▼
┌───────────── background.js (service worker) ─────────────┐
│ queue · fetch(timeout/delay/retry) · pagination          │
│ chrome.alarms monitor · exclude-domains · per-domain cap  │
│   link-extractor.js  regex find + classify + normalize    │
│   searchEngines.js   SERP URL builder + result unwrapping │
│   storage.js         IndexedDB results store (persistent) │
└───────────────────────────┬──────────────────────────────┘
            │ tab / scroll mode (chrome.scripting)
            ▼
   inject:  link-extractor.js + scroll-helper.js + content.js
   scroll-helper.js  auto-scroll + "no more content" detection
   content.js        harvest rendered DOM (Shadow DOM + text + scripts)
```

---

## Install (Developer Mode)

1. Open `chrome://extensions/`
2. Enable **Developer mode** (top-right)
3. **Load unpacked** → select the `tg-link-parser/` folder
4. The 🔗 icon appears in the toolbar

> Regenerate icons with `python3 make_icons.py` (needs `pip install Pillow`).

---

## Modes

### 1 · List (`List` tab)
Paste source URLs (one per line) → **START**. Optionally tick **Deep scan** to
open each page in a background tab and auto-scroll it before harvesting.

### 2 · Keyword Search (`Search` tab)
Enter JP/KR/CN keywords, pick an engine (or **Auto** — chosen per keyword from
the script), set *results/keyword* and *pagination depth*. The extension reads
the SERPs, collects organic result URLs, then harvests each. Tick **Auto-scroll
on each target page** for dynamic results.

### 3 · Personal Pages (`Personal` tab)
For profiles / feeds. Opens each in a background tab, **scrolls** it
`Scroll depth` times (waiting `Interval` ms between scrolls for AJAX content),
and **harvests after every scroll** — important for *virtualized* feeds (Twitter,
Instagram) that remove off-screen nodes. Log into the site first so the
extension sees the same public content you do.

### 4 · Periodic Monitor (`⚙ Settings` tab)
Enable the monitor to re-scan the **List** URLs on a `chrome.alarms` interval.
When new links appear vs. the persisted set, the toolbar badge shows **NEW** and
a desktop notification fires. **Run now** triggers an immediate scan.

### Results (`Results` tab)
Filter by type, free-text search, sort by any column. Export:
- **⬇ CSV** — `source_url,telegram_link,link_type,page_title,detected_at` (UTF-8+BOM for Excel)
- **⬇ JSON** — same fields
- **⧉ Copy** — bare links, one per line
- **✕ Clear** — wipe the IndexedDB store

---

## Settings (`⚙` tab) — persisted

| Setting | Effect |
|---|---|
| Delay (s) | Wait between requests (anti-ban / politeness) |
| Timeout (s) | Per-page load/abort timeout |
| Retries | Retry budget per URL on network error |
| Max links / domain | Cap links taken from one source domain (0 = ∞) |
| Exclude domains | Skip scanning these hosts (e.g. `youtube.com`) |
| Stop-list | Substrings of Telegram links to ignore (e.g. your own channel) |
| Skip known links | Don't re-count links already saved in previous sessions |

---

## What it detects

| Pattern | Example | Type |
|---|---|---|
| `t.me/joinchat/…`, `t.me/+…` | `t.me/+XyZ_789` | **invite** |
| `t.me/s/…` | `t.me/s/korean_channel` | **channel** |
| `t.me/c/…` | `t.me/c/123/45` | **group** |
| handle ending in `bot` | `t.me/MyCoolBot` | **bot** |
| bare username | `t.me/animehub_jp` | **channel** |
| `addstickers` / `addtheme` / reserved | `t.me/addstickers/x` | **other** |

Found in `href`, `src`, `onclick`, `data-url/href/link/uri/target/clipboard-text`,
inside `<script>` bodies, in Shadow DOM, and in bare page text. Covers `t.me`,
`telegram.me`, `telegram.dog` with/without scheme/`www.`. Query strings
(`?start=…`, `?startapp=…`) are ignored for de-duplication but the original link
is preserved. Dedup key = scheme/`www.`/query/fragment/case stripped, and it
**persists across sessions** in IndexedDB.

---

## Regional search engines & pagination

| Language | Engine | Pagination |
|---|---|---|
| 日本語 | Yahoo! Japan, Google JP | `b=` (1,11,21…), `start=` |
| 한국어 | Naver, Google KR | `start=` (1,11,21…) |
| 中文 | Baidu | `pn=` (0,10,20…) — destination read from `mu=` |
| — | Google, Bing | `start=`, `first=` |

Baidu receives the worker's desktop-Chrome User-Agent automatically. If a SERP
returns a CAPTCHA/consent wall, a **warning** is logged in Results (never bypassed).

---

## Ethics & safety

- ✅ Public data only — respect Telegram's and the search engines' ToS.
- 🚫 Never follows invite links; never asks for Telegram login/password.
- 🚫 Never bypasses CAPTCHAs — warns and skips instead.
- 🟥 **STOP** aborts any run; per-URL errors are logged.
- 🔒 No data about the extension's own user is collected.

---

## File layout

```
tg-link-parser/
├── manifest.json       MV3 (storage, tabs, scripting, alarms, notifications, *://*/*)
├── background.js       Service worker: queues, fetch, pagination, scroll driver, monitor
├── link-extractor.js   Telegram-link regex / classify / normalize / title
├── searchEngines.js    SERP URL builder + result-link unwrapping (region-aware)
├── storage.js          IndexedDB results store (persistent dedup)
├── scroll-helper.js    Page auto-scroll + "no more content" detection
├── content.js          Rendered-DOM harvester (Shadow DOM + text + <script>)
├── popup.html / popup.js / styles.css   5-tab UI, dark/light theme
├── make_icons.py       Icon generator
└── icons/              16 / 48 / 128 px
```

> `searchEngines.js` is a focused helper split out of `background.js` for
> clarity; everything else maps 1:1 to the requested structure.

---

## Acceptance criteria — mapping

1. **Installs without errors** → clean MV3 manifest, no external deps.
2. **List of URLs → all TG links** → List tab + `link-extractor.js`.
3. **Dynamic / personal pages** → Personal tab + `scroll-helper.js` + `content.js`.
4. **Keyword → channels via Google/Yahoo JP** → Search tab + `searchEngines.js`.
5. **Pagination + scroll depth** → configurable per mode.
6. **CSV & JSON export, unique + typed links** → Results tab + IndexedDB keyPath dedup.
7. **Periodic monitoring** → `chrome.alarms` + notifications (Settings tab).
8. **No freeze on 50+ pages** → async queue with delays; progress polled, results in IndexedDB.

# 🔗 TG Harvester — Parser · Arsenal · Exploit Warner

A **cross-platform WebExtension (Manifest V3)** for **Chrome, Firefox Desktop
and Firefox for Android (142+)** that combines three independent layers over a
single code base:

1. **Parser (OSINT)** — harvest unique Telegram channel / group / invite / bot
   links from URL lists, regional search engines (JP/KR/CN), and dynamic
   infinite-scroll personal pages.
2. **Arsenal (your own account)** — **Backup** and **Cleaner** for *your own*
   Telegram messages/media, executed through a **local Telethon backend**
   (the browser can't speak MTProto). Throttled by a "load weight" slider,
   gated by confirmation + dry-run + whitelist.
3. **Exploit Warner (Shield)** — a **defensive** content script on Telegram Web
   that flags exploit/scam messages and puts a confirm modal in front of
   suspicious links — fully local, nothing leaves the page.

```
┌──────── popup (4 tabs, touch-friendly) ────────┐
│ Parser │ Arsenal │ Results │ ⚙ Settings        │
└───┬─────────────────────────────────────────────┘
    │ cross-browser messaging
    ▼
┌──────────────── background.js (module SW / event page) ─────────────┐
│ platform-adapter.js  ext namespace · feature-detect · Store/Settings │
│ parser-core.js       link regex/classify + SERP adapters             │
│ cleaner-core.js  ───────────────►  telethon_backend/backend.py       │
│ utils.js                              (Backup / Delete via Telethon)  │
└───┬──────────────────────────────────────────────────────────────────┘
    │ inject (chrome.scripting)            content_scripts on web.telegram.org
    ▼                                       ▼
 content.js + scroll-helper.js        warner-config.js + content-scanner.js
 (rendered-DOM harvest + scroll)      (defensive exploit warner + modal)
```

---

## Cross-platform design (Chrome / Firefox / Firefox Android)

Everything platform-sensitive lives in **`platform-adapter.js`**:

- **Namespace** — unifies `browser` (Firefox, promise-based) and `chrome`
  (Chrome MV3, also promise-based) into a single `ext`.
- **Background** — the manifest declares **both** `service_worker` (Chrome) and
  `scripts` (Firefox) with `"type": "module"`, so one ES-module `background.js`
  loads on every target.
- **Storage** — large/persistent results in **IndexedDB** (on Firefox
  `storage.local` is itself IDB-backed; `unlimitedStorage` lifts quotas);
  config in `storage.local`; transient state in `storage.session` **with a
  `storage.local` fallback** for Firefox Android.
- **Feature detection + fallbacks** for APIs missing/limited on Android:
  `contextMenus`, `commands`, `sidebarAction` are never assumed — the popup is
  the single entry point, the monitor degrades gracefully if `alarms` is
  absent, and notifications are feature-gated. The popup detects Android and
  switches to a larger touch layout.

---

## Install

### Extension
- **Chrome:** `chrome://extensions` → Developer mode → **Load unpacked** →
  this folder.
- **Firefox Desktop:** `about:debugging` → This Firefox → **Load Temporary
  Add-on** → pick `manifest.json`.
- **Firefox Android (142+):** load via `about:debugging` (USB) or sign/install
  the packaged add-on; the popup auto-switches to touch mode.

### Telethon backend (only needed for the Arsenal tab)
```bash
cd telethon_backend/
pip install -r requirements.txt
python backend.py        # first run prompts for phone/code/2FA IN THIS CONSOLE
```
Get `api_id` / `api_hash` from <https://my.telegram.org>. Enter them in the
extension's **Settings → Telethon backend** (stored locally, forwarded only to
`127.0.0.1`). The server listens on `http://127.0.0.1:8787`.

---

## Modes & features

### Parser tab
Pick **URL list**, **Keyword search** (Google/Bing/Yahoo JP/Naver/Baidu, auto by
language) or **Personal pages** (forces tab + scroll). Deep-scan opens pages in
a background tab and **auto-scrolls**, harvesting after every step so
*virtualized* feeds (Twitter/Instagram) are fully captured. Detects links in
`href`, `src`, `data-*`, `onclick`, **`<script>` bodies**, Shadow DOM and bare
text; normalizes full-width (zenkaku) characters; classifies invite / channel /
group / bot / other; de-duplicates persistently in IndexedDB.

### Arsenal tab
- **Backup (Bastion):** dump your own messages to structured JSON + optional
  media (with duplicate-skip).
- **Cleaner:** delete *your own* messages by **dialog**, **older-than-N-days**,
  **media type**, or **wipe everything** — with a **whitelist**, a **dry-run**
  (default), a typed `DELETE` confirm, and a **load-weight** slider that maps to
  batch size + inter-batch delay (≤100 ids/call, respects flood limits).
  Deletion happens **only via Telethon**, never a browser API.

### Results tab
Filter/sort/search, export **CSV / JSON** (`source_url, telegram_link,
link_type, page_title, detected_at`; UTF-8 BOM for Excel), copy links, clear.

### Settings tab
Network (delay/timeout/retries/max-per-domain), filtering (exclude domains,
stop-list, skip-known), **periodic monitor** (`alarms` + notifications), and the
Telethon backend URL + credentials.

---

## Exploit Warner signatures

Editable in **`warner-config.js`** (separate config, local-only): exploit/warez
hashtags, scam keyword patterns, and link rules (Cyrillic-homograph / punycode /
raw-IP / risky TLD / URL-shortener / executable download). A `MutationObserver`
re-scans dynamically loaded messages; risky links are blocked behind a confirm
modal until the user chooses **Open anyway**.

---

## Security & ethics

- ✅ Parser collects **public** data; Arsenal touches **only your own account
  and your own messages** (`from_user='me'`).
- 🔒 `api_id`/`api_hash` stored locally, sent only to `127.0.0.1`; the login
  **code/2FA password are entered in the backend console**, never by the
  extension.
- 🚫 Deletion is **irreversible** — guarded by dry-run, typed confirm, explicit
  wipe warning, and a whitelist.
- 🛡️ Exploit Warner is purely **defensive** and **local**; it never bypasses
  CAPTCHAs and never exfiltrates anything.

---

## File layout

```
tg-link-parser/
├── manifest.json          MV3, cross-browser (service_worker + scripts, gecko id)
├── background.js          Module orchestrator (parser/monitor/cleaner bridge)
├── platform-adapter.js    Kernel & Adapter: namespace, features, storage
├── parser-core.js         Link extraction + classify + SERP adapters
├── cleaner-core.js        Telethon backend client (backup/delete bridge)
├── utils.js               Shared helpers (incl. load-weight → batch mapping)
├── scroll-helper.js       Page auto-scroll + "no more content" heuristic
├── content.js             Rendered-DOM harvester (Shadow DOM + text + <script>)
├── warner-config.js       Exploit-warner signatures (editable config)
├── content-scanner.js     Defensive exploit warner (web.telegram.org)
├── popup.html / popup.js / popup.css   4-tab touch-friendly UI
├── make_icons.py          Icon generator
├── icons/                 16 / 48 / 128 px
└── telethon_backend/
    ├── backend.py         Local Telethon HTTP helper (own-account backup/delete)
    └── requirements.txt
```

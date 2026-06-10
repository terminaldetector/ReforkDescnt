/**
 * TG HARVESTER — PARSER CORE (OSINT link collection logic)
 * ---------------------------------------------------------------------------
 * DOM-free logic for (a) finding/classifying/normalizing Telegram links inside
 * a text/HTML blob and (b) building region-aware search-engine URLs and
 * unwrapping their result links. Runs in the background module (Chrome SW /
 * Firefox event page). The page side only HARVESTS raw blobs (content.js);
 * all extraction happens here, keeping one source of truth.
 *
 * Detects t.me / telegram.me / telegram.dog links from <a href>, src,
 * data-* attributes, onclick handlers, <script> bodies and bare text.
 * ---------------------------------------------------------------------------
 */

// ── Link extraction ─────────────────────────────────────────────────────────

/** Full-width (zenkaku) ASCII -> half-width, so JP full-width links match. */
export function fullwidthToHalfwidth(str) {
  if (!str) return "";
  return str
    .replace(/[！-～]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0xfee0))
    .replace(/[　 ]/g, " ")
    .replace(/．/g, ".")
    .replace(/／/g, "/");
}

const TG_LINK_RE = new RegExp(
  "(?:https?:)?//(?:www\\.)?(?:t(?:elegram)?\\.(?:me|dog))/" +
    "(?:joinchat/[\\w-]+|\\+[\\w-]+|s/[A-Za-z0-9_]+(?:/\\d+)?|c/\\d+(?:/\\d+)?" +
    "|addstickers/[\\w-]+|addemoji/[\\w-]+|addtheme/[\\w-]+|setlanguage/[\\w-]+" +
    "|share/url\\?[^\"'\\s<>]*|[A-Za-z][A-Za-z0-9_]{2,31}(?:/\\d+)?)",
  "gi"
);

const TG_LINK_BARE_RE = new RegExp(
  "(?:^|[\\s\"'>(\\[])((?:www\\.)?(?:t(?:elegram)?\\.(?:me|dog))/" +
    "(?:joinchat/[\\w-]+|\\+[\\w-]+|s/[A-Za-z0-9_]+(?:/\\d+)?|c/\\d+(?:/\\d+)?" +
    "|addstickers/[\\w-]+|addemoji/[\\w-]+|addtheme/[\\w-]+|setlanguage/[\\w-]+" +
    "|[A-Za-z][A-Za-z0-9_]{2,31}(?:/\\d+)?))",
  "gi"
);

const TG_RESERVED = new Set([
  "share", "addstickers", "addtheme", "addemoji", "setlanguage",
  "iv", "login", "confirmphone", "socks", "proxy", "bg", "joinchat",
]);

/** Classify into invite | channel | group | bot | other. */
export function classifyLink(link) {
  const path = link
    .replace(/^https?:\/\//i, "").replace(/^www\./i, "")
    .replace(/^(?:t(?:elegram)?\.(?:me|dog))\//i, "").toLowerCase();
  if (path.startsWith("joinchat/") || path.startsWith("+")) return "invite";
  if (path.startsWith("s/")) return "channel";
  if (path.startsWith("c/")) return "group";
  if (path.startsWith("addstickers/") || path.startsWith("addtheme/") ||
      path.startsWith("addemoji/") || path.startsWith("setlanguage/") ||
      path.startsWith("share/")) return "other";
  const first = path.split(/[\/?#]/)[0];
  if (TG_RESERVED.has(first)) return "other";
  if (/bot$/.test(first)) return "bot";
  return "channel";
}

/** De-dup key: drop scheme/www./query/fragment/trailing-slash; lower-case. */
export function normalizeLink(link) {
  let s = link.trim().replace(/^https?:\/\//i, "").replace(/^www\./i, "");
  return s.split("#")[0].split("?")[0].replace(/\/+$/, "").toLowerCase();
}

/** Clean display link (https-prefixed, no query/fragment). */
export function canonicalDisplay(raw) {
  let s = raw.trim().replace(/^https?:\/\//i, "").replace(/^\/\//, "");
  s = s.split("#")[0].split("?")[0].replace(/\/+$/, "");
  return "https://" + s;
}

/**
 * Extract every unique Telegram link in a blob.
 * @returns {Array<{link:string,key:string,type:string}>}
 */
export function extractTelegramLinks(blob) {
  if (!blob) return [];
  const text = fullwidthToHalfwidth(String(blob));
  const seen = new Set();
  const out = [];
  const collect = (raw) => {
    if (!raw) return;
    const display = canonicalDisplay(raw);
    const key = normalizeLink(display);
    if (seen.has(key)) return;
    seen.add(key);
    out.push({ link: display, key, type: classifyLink(display) });
  };
  let m;
  TG_LINK_RE.lastIndex = 0;
  while ((m = TG_LINK_RE.exec(text)) !== null) collect(m[0]);
  TG_LINK_BARE_RE.lastIndex = 0;
  while ((m = TG_LINK_BARE_RE.exec(text)) !== null) collect(m[1]);
  return out;
}

/** Pull <title> text from raw HTML (fetch-mode page title). */
export function extractPageTitle(html) {
  if (!html) return "";
  const m = /<title[^>]*>([\s\S]*?)<\/title>/i.exec(html);
  if (!m) return "";
  return m[1]
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, " ").trim().slice(0, 300);
}

// ── Search-engine adapters ──────────────────────────────────────────────────

const SERP_NOISE_HOSTS = [
  "google.", "gstatic.", "googleusercontent.", "googleadservices.",
  "bing.com", "microsoft.com", "msn.com", "microsofttranslator.",
  "yahoo.co.jp", "yahoo.com", "yimg.jp", "yimg.com",
  "naver.com", "naver.net", "pstatic.net",
  "baidu.com", "bdstatic.com", "bdimg.com",
  "youtube.com/redirect", "schema.org", "w3.org", "googleapis.com",
];

/** Pick an engine from the keyword's dominant script. */
export function detectEngineByLang(keyword) {
  const s = keyword || "";
  if (/[가-힯ᄀ-ᇿ]/.test(s)) return "naver";
  if (/[぀-ヿ]/.test(s)) return "yahoo_jp";
  if (/[一-鿿㐀-䶿]/.test(s)) return "baidu";
  return "google";
}

/** Build a SERP URL for a given engine, keyword and zero-based page index. */
export function buildSerpUrl(engine, keyword, page) {
  const q = encodeURIComponent(keyword);
  const p = Math.max(0, page | 0);
  switch (engine) {
    case "google": return `https://www.google.com/search?q=${q}&num=10&start=${p * 10}`;
    case "google_jp": return `https://www.google.co.jp/search?q=${q}&num=10&hl=ja&start=${p * 10}`;
    case "google_kr": return `https://www.google.co.kr/search?q=${q}&num=10&hl=ko&start=${p * 10}`;
    case "bing": return `https://www.bing.com/search?q=${q}&first=${p * 10 + 1}`;
    case "yahoo_jp": return `https://search.yahoo.co.jp/search?p=${q}&b=${p * 10 + 1}`;
    case "naver": return `https://search.naver.com/search.naver?query=${q}&start=${p * 10 + 1}`;
    case "baidu": return `https://www.baidu.com/s?wd=${q}&pn=${p * 10}`;
    default: return `https://www.google.com/search?q=${q}&start=${p * 10}`;
  }
}

function safeDecode(u) {
  try {
    let d = decodeURIComponent(u);
    if (/%[0-9a-f]{2}/i.test(d)) { try { d = decodeURIComponent(d); } catch (_) {} }
    return d;
  } catch (_) { return u; }
}

function isNoiseHost(url) {
  const low = url.toLowerCase();
  return SERP_NOISE_HOSTS.some((h) => low.includes(h));
}

/**
 * Extract organic result URLs from SERP HTML.
 * @returns {{links:string[], blocked:boolean}}
 */
export function extractSerpResults(engine, html, limit) {
  if (!html) return { links: [], blocked: false };
  const found = [];
  const seen = new Set();
  const push = (u) => {
    if (!u) return;
    let url = u.trim();
    if (!/^https?:\/\//i.test(url)) return;
    url = url.split("#")[0];
    if (isNoiseHost(url)) return;
    const key = url.replace(/\/+$/, "").toLowerCase();
    if (seen.has(key)) return;
    seen.add(key);
    found.push(url);
  };

  let m;
  if (engine.startsWith("google")) {
    const re = /\/url\?(?:[^"]*&)?q=([^&"]+)/g;
    while ((m = re.exec(html)) !== null) push(safeDecode(m[1]));
  }
  if (engine === "yahoo_jp") {
    const re = /\/RU=([^/"]+)\//g;
    while ((m = re.exec(html)) !== null) push(safeDecode(m[1]));
  }
  if (engine === "baidu") {
    const re = /\bmu="([^"]+)"/g;
    while ((m = re.exec(html)) !== null) push(m[1]);
  }
  const hrefRe = /href="(https?:\/\/[^"]+)"/g;
  while ((m = hrefRe.exec(html)) !== null) push(m[1].replace(/&amp;/g, "&"));

  const lower = html.toLowerCase();
  const blocked = found.length === 0 &&
    (lower.includes("captcha") || lower.includes("unusual traffic") ||
     lower.includes("consent.google") || lower.includes("verify you are a human") ||
     lower.includes("人机验证") || lower.includes("자동입력 방지"));

  return { links: found.slice(0, limit), blocked };
}

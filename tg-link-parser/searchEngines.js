/**
 * TG LINK HARVESTER — SEARCH ENGINE ADAPTERS
 * ---------------------------------------------------------------------------
 * Builds region-aware SERP (search engine results page) URLs and extracts the
 * organic result links out of fetched SERP HTML — again purely with regex so
 * it runs inside the MV3 service worker.
 *
 * Supported engines: google, google_jp, google_kr, bing, yahoo_jp, naver,
 * baidu, plus an "auto" mode that picks an engine from the keyword's script.
 *
 * NOTE: search engines actively fight scraping. Any of these requests may be
 * answered with a CAPTCHA / consent wall instead of results — in that case we
 * simply surface a warning (per the project's "no automatic CAPTCHA bypass"
 * ethics rule) and move on.
 * ---------------------------------------------------------------------------
 */

/** Hostnames we never treat as a genuine organic result (engine chrome/assets). */
const SERP_NOISE_HOSTS = [
  "google.", "gstatic.", "googleusercontent.", "googleadservices.",
  "bing.com", "microsoft.com", "msn.com", "microsofttranslator.",
  "yahoo.co.jp", "yahoo.com", "yimg.jp", "yimg.com",
  "naver.com", "naver.net", "pstatic.net",
  "baidu.com", "bdstatic.com", "bdimg.com",
  "youtube.com/redirect", "schema.org", "w3.org", "googleapis.com",
];

/**
 * Decide which engine fits a keyword based on the Unicode scripts present.
 *   - Hangul            -> Naver (Korean)
 *   - Kana (+ Han)      -> Yahoo! Japan (Japanese)
 *   - Han only          -> Baidu (Chinese)
 *   - otherwise         -> Google
 * @param {string} keyword
 * @returns {string} engine id
 */
function detectEngineByLang(keyword) {
  const s = keyword || "";
  if (/[가-힯ᄀ-ᇿ]/.test(s)) return "naver"; // Hangul
  const hasKana = /[぀-ヿ]/.test(s);
  const hasHan = /[一-鿿㐀-䶿]/.test(s);
  if (hasKana) return "yahoo_jp";
  if (hasHan) return "baidu";
  return "google";
}

/**
 * Build the SERP URL for a given engine, keyword and zero-based page index.
 * @param {string} engine
 * @param {string} keyword
 * @param {number} page zero-based page number
 * @returns {string} fully-qualified SERP URL
 */
function buildSerpUrl(engine, keyword, page) {
  const q = encodeURIComponent(keyword);
  const p = Math.max(0, page | 0);
  switch (engine) {
    case "google":
      return `https://www.google.com/search?q=${q}&num=10&start=${p * 10}`;
    case "google_jp":
      return `https://www.google.co.jp/search?q=${q}&num=10&hl=ja&start=${p * 10}`;
    case "google_kr":
      return `https://www.google.co.kr/search?q=${q}&num=10&hl=ko&start=${p * 10}`;
    case "bing":
      return `https://www.bing.com/search?q=${q}&first=${p * 10 + 1}`;
    case "yahoo_jp":
      // Yahoo! Japan paginates with p= (query) and b= (1-based result offset).
      return `https://search.yahoo.co.jp/search?p=${q}&b=${p * 10 + 1}`;
    case "naver":
      // Naver paginates with start= (1, 11, 21, ...).
      return `https://search.naver.com/search.naver?query=${q}&start=${p * 10 + 1}`;
    case "baidu":
      // Baidu paginates with pn= (0, 10, 20, ...). Desktop Chrome UA is sent
      // automatically by the worker's fetch, which Baidu requires.
      return `https://www.baidu.com/s?wd=${q}&pn=${p * 10}`;
    default:
      return `https://www.google.com/search?q=${q}&start=${p * 10}`;
  }
}

/** Safely percent-decode a possibly double-encoded URL fragment. */
function safeDecode(u) {
  try {
    let d = decodeURIComponent(u);
    // Some engines double-encode; one more pass is usually safe.
    if (/%[0-9a-f]{2}/i.test(d)) {
      try { d = decodeURIComponent(d); } catch (_) {}
    }
    return d;
  } catch (_) {
    return u;
  }
}

/** True if `url` should be discarded as engine chrome rather than a result. */
function isNoiseHost(url) {
  const low = url.toLowerCase();
  return SERP_NOISE_HOSTS.some((h) => low.includes(h));
}

/**
 * Extract organic result URLs from a SERP HTML body.
 * A generic absolute-URL sweep is combined with engine-specific redirect
 * "unwrapping" (Google /url?q=, Yahoo /RU=, Baidu mu="...").
 *
 * @param {string} engine
 * @param {string} html raw SERP HTML
 * @param {number} limit max results to return
 * @returns {{links: string[], blocked: boolean}}
 */
function extractSerpResults(engine, html, limit) {
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

  // ── Engine-specific redirect unwrapping ──────────────────────────────────
  let m;
  if (engine.startsWith("google")) {
    const re = /\/url\?(?:[^"]*&)?q=([^&"]+)/g;
    while ((m = re.exec(html)) !== null) push(safeDecode(m[1]));
  }
  if (engine === "yahoo_jp") {
    // Yahoo wraps results as .../RU=<encoded>/RK=...
    const re = /\/RU=([^/"]+)\//g;
    while ((m = re.exec(html)) !== null) push(safeDecode(m[1]));
  }
  if (engine === "baidu") {
    // Baidu exposes the true destination in a mu="" attribute on result nodes.
    const re = /\bmu="([^"]+)"/g;
    while ((m = re.exec(html)) !== null) push(m[1]);
  }

  // ── Generic absolute href sweep (covers Bing, Naver, and the rest) ───────
  const hrefRe = /href="(https?:\/\/[^"]+)"/g;
  while ((m = hrefRe.exec(html)) !== null) {
    push(m[1].replace(/&amp;/g, "&"));
  }

  // ── Blocked / CAPTCHA detection (best-effort, surfaced as a warning) ─────
  const lower = html.toLowerCase();
  const blocked =
    found.length === 0 &&
    (lower.includes("captcha") ||
      lower.includes("unusual traffic") ||
      lower.includes("consent.google") ||
      lower.includes("verify you are a human") ||
      lower.includes("人机验证") || // Baidu CAPTCHA
      lower.includes("자동입력 방지")); // Naver CAPTCHA

  return { links: found.slice(0, limit), blocked };
}

if (typeof self !== "undefined") {
  self.TGSearch = {
    detectEngineByLang,
    buildSerpUrl,
    extractSerpResults,
  };
}

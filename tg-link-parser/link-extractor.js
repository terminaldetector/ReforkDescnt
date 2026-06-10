/**
 * TG LINK HARVESTER — LINK EXTRACTOR LIBRARY
 * ---------------------------------------------------------------------------
 * Pure, DOM-free helpers for finding, classifying and normalizing Telegram
 * links inside a blob of text/HTML. Because it touches no `document`/`window`
 * API it runs identically in two places:
 *   1. the MV3 service worker (loaded via importScripts) — over raw fetched HTML
 *   2. an injected page world (loaded via chrome.scripting) — over a DOM blob
 *
 * It attaches to `globalThis`, so the same file works in both `self` (worker)
 * and `window` (page) contexts.
 * ---------------------------------------------------------------------------
 */
(function (root) {
  /**
   * Convert full-width (zenkaku) ASCII — common on Japanese pages — to
   * half-width so links written with full-width letters/slashes still match.
   * e.g. "ｔ．ｍｅ／ｃｈ" -> "t.me/ch"
   */
  function fullwidthToHalfwidth(str) {
    if (!str) return "";
    return str
      .replace(/[！-～]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0xfee0))
      .replace(/[　 ]/g, " ")
      .replace(/．/g, ".")
      .replace(/／/g, "/");
  }

  /**
   * Master regex for Telegram links across all hosts and forms:
   * t.me / telegram.me / telegram.dog, optional scheme + www., invite links
   * (/joinchat, /+), previews (/s/), private refs (/c/), utility paths, bots
   * and bare usernames (optionally with /postid).
   */
  const TG_LINK_RE = new RegExp(
    "(?:https?:)?//" +
      "(?:www\\.)?" +
      "(?:t(?:elegram)?\\.(?:me|dog))" +
      "/" +
      "(?:" +
      "joinchat/[\\w-]+" +
      "|\\+[\\w-]+" +
      "|s/[A-Za-z0-9_]+(?:/\\d+)?" +
      "|c/\\d+(?:/\\d+)?" +
      "|addstickers/[\\w-]+" +
      "|addemoji/[\\w-]+" +
      "|addtheme/[\\w-]+" +
      "|setlanguage/[\\w-]+" +
      "|share/url\\?[^\"'\\s<>]*" +
      "|[A-Za-z][A-Za-z0-9_]{2,31}(?:/\\d+)?" +
      ")",
    "gi"
  );

  /** Looser pattern for scheme-less bare text ("visit t.me/foo"). */
  const TG_LINK_BARE_RE = new RegExp(
    "(?:^|[\\s\"'>(\\[])" +
      "((?:www\\.)?(?:t(?:elegram)?\\.(?:me|dog))" +
      "/" +
      "(?:joinchat/[\\w-]+|\\+[\\w-]+|s/[A-Za-z0-9_]+(?:/\\d+)?|c/\\d+(?:/\\d+)?" +
      "|addstickers/[\\w-]+|addemoji/[\\w-]+|addtheme/[\\w-]+|setlanguage/[\\w-]+" +
      "|[A-Za-z][A-Za-z0-9_]{2,31}(?:/\\d+)?))",
    "gi"
  );

  /** Reserved t.me paths that are NOT channels/groups/invites. */
  const TG_RESERVED = new Set([
    "share", "addstickers", "addtheme", "addemoji", "setlanguage",
    "iv", "login", "confirmphone", "socks", "proxy", "bg", "joinchat",
  ]);

  /**
   * Classify a link into: invite | channel | group | bot | other.
   * Bare "t.me/username" is ambiguous (user/bot/channel/group); we detect bots
   * (handles ending in "bot") and otherwise default to "channel".
   */
  function classifyLink(link) {
    const path = link
      .replace(/^https?:\/\//i, "")
      .replace(/^www\./i, "")
      .replace(/^(?:t(?:elegram)?\.(?:me|dog))\//i, "")
      .toLowerCase();

    if (path.startsWith("joinchat/") || path.startsWith("+")) return "invite";
    if (path.startsWith("s/")) return "channel";
    if (path.startsWith("c/")) return "group";
    if (
      path.startsWith("addstickers/") || path.startsWith("addtheme/") ||
      path.startsWith("addemoji/") || path.startsWith("setlanguage/") ||
      path.startsWith("share/")
    ) {
      return "other";
    }
    const first = path.split(/[\/?#]/)[0];
    if (TG_RESERVED.has(first)) return "other";
    if (/bot$/.test(first)) return "bot";
    return "channel";
  }

  /**
   * De-duplication key: drop scheme/www., query (joinchat/+ live in the path),
   * fragment, trailing slash; lower-case everything.
   */
  function normalizeLink(link) {
    let s = link.trim();
    s = s.replace(/^https?:\/\//i, "").replace(/^www\./i, "");
    s = s.split("#")[0].split("?")[0].replace(/\/+$/, "");
    return s.toLowerCase();
  }

  /** Rebuild a clean, display-ready https link from a raw match. */
  function canonicalDisplay(raw) {
    let s = raw.trim().replace(/^https?:\/\//i, "").replace(/^\/\//, "");
    s = s.split("#")[0].split("?")[0].replace(/\/+$/, "");
    return "https://" + s;
  }

  /**
   * Extract every unique Telegram link in a blob (HTML or text). Catches
   * href/src/onclick/data-* attribute values, links embedded inside <script>
   * bodies and bare text — they are all just substrings of the blob.
   * @returns {Array<{link:string,key:string,type:string}>}
   */
  function extractTelegramLinks(blob) {
    if (!blob) return [];
    const text = fullwidthToHalfwidth(String(blob));
    const seen = new Set();
    const out = [];

    const collect = (rawMatch) => {
      if (!rawMatch) return;
      const display = canonicalDisplay(rawMatch);
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

  /** Pull the <title> text out of a raw HTML string (fetch-mode page title). */
  function extractPageTitle(html) {
    if (!html) return "";
    const m = /<title[^>]*>([\s\S]*?)<\/title>/i.exec(html);
    if (!m) return "";
    return m[1]
      .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
      .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, " ").trim()
      .slice(0, 300);
  }

  root.TGLinkExtractor = {
    fullwidthToHalfwidth,
    classifyLink,
    normalizeLink,
    canonicalDisplay,
    extractTelegramLinks,
    extractPageTitle,
  };
})(typeof self !== "undefined" ? self : globalThis);

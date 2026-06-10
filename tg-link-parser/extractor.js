/**
 * TG LINK HARVESTER — EXTRACTOR
 * ---------------------------------------------------------------------------
 * Pure, DOM-free helpers for finding and normalizing Telegram links inside an
 * arbitrary blob of text/HTML. Because Manifest V3 service workers have NO
 * access to `DOMParser`/`document`, all extraction here is done with regular
 * expressions so the exact same code can run both in the background worker
 * (over raw fetched HTML) and conceptually in the content script.
 *
 * This file is loaded via `importScripts('extractor.js')` from background.js.
 * ---------------------------------------------------------------------------
 */

/**
 * Convert full-width (zenkaku) ASCII characters — common in Japanese pages —
 * into their half-width (hankaku) equivalents so that links written with
 * full-width letters/slashes still match the link patterns.
 * e.g. "ｔ．ｍｅ／ｃｈａｎｎｅｌ" -> "t.me/channel"
 * @param {string} str
 * @returns {string}
 */
function fullwidthToHalfwidth(str) {
  if (!str) return "";
  return str
    // Full-width ASCII range (U+FF01–U+FF5E) maps to ASCII (U+0021–U+007E).
    .replace(/[！-～]/g, (ch) =>
      String.fromCharCode(ch.charCodeAt(0) - 0xfee0)
    )
    // Ideographic / full-width space -> normal space.
    .replace(/[　 ]/g, " ")
    // Full-width full stop and solidus that fall outside the range above.
    .replace(/．/g, ".")
    .replace(/／/g, "/");
}

/**
 * Master regex for Telegram links across all known hosts and forms:
 *   - t.me / telegram.me / telegram.dog
 *   - optional scheme + optional "www."
 *   - invite links: /joinchat/XXXX and /+XXXX
 *   - public previews: /s/username
 *   - private refs: /c/1234567890[/123]
 *   - utility links: /addstickers, /addtheme, /addemoji, /share, /setlanguage
 *   - bare usernames: /username  (5–32 valid chars per Telegram rules, but we
 *     relax the lower bound to 3 to avoid missing short handles)
 */
const TG_LINK_RE = new RegExp(
  "(?:https?:)?//" + // scheme is optional in text, but we still require the //
    "(?:www\\.)?" +
    "(?:t(?:elegram)?\\.(?:me|dog))" + // t.me | telegram.me | telegram.dog
    "/" +
    "(?:" +
    "joinchat/[\\w-]+" + // /joinchat/HASH
    "|\\+[\\w-]+" + // /+HASH
    "|s/[A-Za-z0-9_]+(?:/\\d+)?" + // /s/name
    "|c/\\d+(?:/\\d+)?" + // /c/123/45
    "|addstickers/[\\w-]+" +
    "|addemoji/[\\w-]+" +
    "|addtheme/[\\w-]+" +
    "|setlanguage/[\\w-]+" +
    "|share/url\\?[^\"'\\s<>]*" +
    "|[A-Za-z][A-Za-z0-9_]{2,31}(?:/\\d+)?" + // bare username[/postid]
    ")",
  "gi"
);

/**
 * A looser pattern that also catches scheme-less "t.me/foo" written as bare
 * text without the leading "//". Used as a second pass over plain text nodes.
 */
const TG_LINK_BARE_RE = new RegExp(
  "(?:^|[\\s\"'>(\\[])" + // boundary so we don't match inside e.g. "list.me"
    "((?:www\\.)?(?:t(?:elegram)?\\.(?:me|dog))" +
    "/" +
    "(?:joinchat/[\\w-]+|\\+[\\w-]+|s/[A-Za-z0-9_]+(?:/\\d+)?|c/\\d+(?:/\\d+)?" +
    "|addstickers/[\\w-]+|addemoji/[\\w-]+|addtheme/[\\w-]+|setlanguage/[\\w-]+" +
    "|[A-Za-z][A-Za-z0-9_]{2,31}(?:/\\d+)?))",
  "gi"
);

/** Reserved t.me paths that are NOT channels/groups/invites. */
const TG_RESERVED = new Set([
  "share",
  "addstickers",
  "addtheme",
  "addemoji",
  "setlanguage",
  "iv",
  "login",
  "confirmphone",
  "socks",
  "proxy",
  "bg",
  "joinchat", // handled separately, but the bare word alone is reserved
]);

/**
 * Classify a Telegram link into one of: invite | channel | group | other.
 * Note: a bare "t.me/username" is ambiguous (could be a user, bot, channel or
 * group), so we label such links "channel" by convention while flagging bots.
 * @param {string} link normalized-ish link (scheme optional)
 * @returns {"invite"|"channel"|"group"|"other"}
 */
function classifyLink(link) {
  // Strip scheme + host to inspect the path.
  const path = link
    .replace(/^https?:\/\//i, "")
    .replace(/^www\./i, "")
    .replace(/^(?:t(?:elegram)?\.(?:me|dog))\//i, "")
    .toLowerCase();

  if (path.startsWith("joinchat/") || path.startsWith("+")) return "invite";
  if (path.startsWith("s/")) return "channel"; // public channel preview
  if (path.startsWith("c/")) return "group"; // private chat/channel by internal id
  if (
    path.startsWith("addstickers/") ||
    path.startsWith("addtheme/") ||
    path.startsWith("addemoji/") ||
    path.startsWith("setlanguage/") ||
    path.startsWith("share/")
  ) {
    return "other";
  }

  const first = path.split(/[\/?#]/)[0];
  if (TG_RESERVED.has(first)) return "other";
  // Heuristic: handles ending in "bot" are bots -> treat as "other".
  if (/bot$/.test(first)) return "other";
  return "channel";
}

/**
 * Produce a canonical key used purely for de-duplication. Per spec we:
 *   - drop the scheme and "www."
 *   - lower-case the host (and, for case-insensitive Telegram usernames, the
 *     whole thing) — note this means two invite hashes differing only in case
 *     would collide, which is acceptable for this tool.
 *   - drop query string and fragment
 *   - drop a trailing slash
 * @param {string} link
 * @returns {string}
 */
function normalizeLink(link) {
  let s = link.trim();
  s = s.replace(/^https?:\/\//i, "");
  s = s.replace(/^www\./i, "");
  s = s.split("#")[0];
  s = s.split("?")[0];
  s = s.replace(/\/+$/, "");
  // Canonicalize the host so telegram.me / telegram.dog / t.me all compare on
  // their path only would be wrong (different services), so keep host but
  // lower-case everything.
  return s.toLowerCase();
}

/**
 * Rebuild a clean, display-ready https link from a raw match.
 * @param {string} raw
 * @returns {string}
 */
function canonicalDisplay(raw) {
  let s = raw.trim().replace(/^https?:\/\//i, "");
  s = s.replace(/^\/\//, "");
  // Keep query/fragment off for display consistency.
  s = s.split("#")[0].split("?")[0].replace(/\/+$/, "");
  return "https://" + s;
}

/**
 * Extract every unique Telegram link contained in a blob of HTML/text.
 * Handles href="" attributes, onclick/data-* attribute values and bare text —
 * they are all just substrings of the blob, so a single regex sweep over the
 * full-width-normalized blob catches them all.
 *
 * @param {string} blob raw HTML or text
 * @returns {Array<{link: string, key: string, type: string}>}
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
  while ((m = TG_LINK_RE.exec(text)) !== null) {
    collect(m[0]);
  }

  TG_LINK_BARE_RE.lastIndex = 0;
  while ((m = TG_LINK_BARE_RE.exec(text)) !== null) {
    collect(m[1]);
  }

  return out;
}

// Expose in service-worker (importScripts) global scope.
if (typeof self !== "undefined") {
  self.TGExtractor = {
    fullwidthToHalfwidth,
    classifyLink,
    normalizeLink,
    canonicalDisplay,
    extractTelegramLinks,
  };
}

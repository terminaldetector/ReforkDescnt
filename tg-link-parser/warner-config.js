/**
 * TG HARVESTER — EXPLOIT-WARNER SIGNATURES (config)
 * ---------------------------------------------------------------------------
 * Local, editable signature set for the defensive content scanner. Nothing
 * here leaves the device — the warner matches messages/links against these
 * patterns entirely client-side. Tune freely; this is intentionally a separate
 * file so signatures can be updated without touching scanner logic.
 *
 * Runs as the FIRST content script on web.telegram.org and exposes the config
 * on `window.WARNER_CONFIG` for content-scanner.js (same isolated world).
 * ---------------------------------------------------------------------------
 */
(function (root) {
  root.WARNER_CONFIG = {
    /** Hashtags frequently attached to malicious / warez / exploit offers. */
    hashtags: [
      "#exploit", "#0day", "#warez", "#варез", "#эксплойт", "#взлом",
      "#malware", "#stealer", "#ransomware", "#carding", "#cc", "#dump",
      "#combolist", "#rdp", "#botnet", "#rat",
    ],

    /** Free-text keyword patterns (case-insensitive) that warrant a warning. */
    keywordPatterns: [
      /\bfree\s+(?:rat|stealer|botnet|crypter)\b/i,
      /\b(?:fresh|valid)\s+(?:cc|cvv|fullz|logs)\b/i,
      /\bseed\s*phrase\b/i,
      /\bprivate\s*key\s*(?:dump|leak)\b/i,
      /\bdouble\s+your\s+(?:btc|eth|crypto)\b/i, // classic giveaway scam
      /бесплатн\w*\s+(?:курс|схем|залив)/i,
    ],

    /**
     * Suspicious LINK heuristics. Each entry has a test(urlObj, rawHref) fn and
     * a human-readable reason. Links that match are blocked behind a confirm
     * modal before navigation.
     */
    linkRules: [
      {
        reason: "Cyrillic characters in the domain (possible homograph spoof)",
        test: (u) => /[Ѐ-ӿ]/.test(u.hostname),
      },
      {
        reason: "Punycode/IDN domain (xn--) — verify it is not a look-alike",
        test: (u) => /(^|\.)xn--/i.test(u.hostname),
      },
      {
        reason: "Raw IP address instead of a domain name",
        test: (u) => /^\d{1,3}(\.\d{1,3}){3}$/.test(u.hostname),
      },
      {
        reason: "High-risk top-level domain often used for throwaway hosts",
        test: (u) => /\.(?:zip|mov|top|xyz|tk|gq|cf|ml|ga|ru\.com)$/i.test(u.hostname),
      },
      {
        reason: "Known URL shortener — destination is hidden",
        test: (u) => /(^|\.)(?:bit\.ly|tinyurl\.com|goo\.gl|cutt\.ly|is\.gd|t\.co)$/i.test(u.hostname),
      },
      {
        reason: "Direct executable / archive download",
        test: (u) => /\.(?:exe|scr|bat|cmd|apk|jar|msi|vbs|ps1)(?:$|\?)/i.test(u.pathname),
      },
    ],
  };
})(typeof window !== "undefined" ? window : globalThis);

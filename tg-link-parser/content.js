/**
 * TG LINK HARVESTER — CONTENT / PAGE HARVESTER
 * ---------------------------------------------------------------------------
 * Injected into the target PAGE (via chrome.scripting) for "tab" / deep-scroll
 * modes. Unlike the background's raw-HTML fetch, this reads the fully RENDERED
 * DOM, so it sees JS-inserted content and links hidden in Shadow DOM.
 *
 * It only GATHERS — it does not classify or store. It collects every place a
 * Telegram link can hide into one text blob; the shared link-extractor then
 * runs the regex sweep, keeping a single source of truth for detection.
 *
 * Exposes `window.TGHarvest.harvestBlob()`. It does NOT auto-run, so it can be
 * injected once and called repeatedly between scroll steps.
 * ---------------------------------------------------------------------------
 */
(function (root) {
  /** Attribute names that frequently carry URLs on apps/forums/socials. */
  const URL_ATTRS = [
    "href", "src",
    "data-url", "data-href", "data-link", "data-uri", "data-target",
    "data-redirect", "data-clipboard-text", "data-expanded-url",
    "onclick", "title", "alt", "content", "value",
  ];

  /**
   * Recursively walk a root node (document or shadow root), collecting:
   *   - URL-ish attribute values from every element
   *   - the body text of <script> tags (links embedded in JS objects)
   *   - the text content of every text node (bare links written as plain text)
   * descending into any open Shadow DOM along the way.
   * @param {Node} node
   * @param {string[]} parts accumulator
   */
  function walk(node, parts) {
    const els = node.querySelectorAll ? node.querySelectorAll("*") : [];
    for (const el of els) {
      for (const attr of URL_ATTRS) {
        const v = el.getAttribute && el.getAttribute(attr);
        if (v) parts.push(v);
      }
      // <script> bodies: scan as TEXT, never execute.
      if (el.tagName === "SCRIPT" && el.textContent) parts.push(el.textContent);
      // Descend into open shadow roots.
      if (el.shadowRoot) walk(el.shadowRoot, parts);
    }

    // Raw text nodes (bare links).
    try {
      const tw = document.createTreeWalker(node, NodeFilter.SHOW_TEXT, null);
      let t;
      while ((t = tw.nextNode())) {
        const v = t.nodeValue;
        if (v && v.length > 3) parts.push(v);
      }
    } catch (_) {
      /* Some shadow roots reject TreeWalker; ignore. */
    }
  }

  /**
   * Build a single text blob from the current rendered DOM.
   * @returns {string}
   */
  function harvestBlob() {
    const parts = [];
    try { walk(document, parts); } catch (_) {}
    let html = "";
    try {
      html = document.documentElement ? document.documentElement.innerHTML : "";
    } catch (_) {}
    return parts.join("\n") + "\n" + html;
  }

  root.TGHarvest = { harvestBlob };
})(typeof window !== "undefined" ? window : globalThis);

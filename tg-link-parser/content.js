/**
 * TG LINK HARVESTER — CONTENT SCRIPT
 * ---------------------------------------------------------------------------
 * Injected on-demand (chrome.scripting.executeScript) into a target tab ONLY
 * when the user enables "open pages in a tab" mode. Unlike the background's
 * raw-HTML fetch, this runs against the fully *rendered* DOM, so it can reach
 * content added by JavaScript and links hidden inside Shadow DOM.
 *
 * It does not classify or store anything itself — it just gathers every place
 * a Telegram link could hide into one big text blob and returns it. The
 * background's extractor then does the regex sweep, keeping a single source of
 * truth for link detection.
 *
 * The trailing IIFE expression's value becomes `result` in executeScript().
 * ---------------------------------------------------------------------------
 */
(function harvestPageBlob() {
  /** Attribute names that frequently carry URLs in apps/forums. */
  const URL_ATTRS = [
    "href",
    "src",
    "data-href",
    "data-url",
    "data-link",
    "data-redirect",
    "onclick",
    "title",
    "alt",
    "content", // <meta content="...">
    "value",
  ];

  const parts = [];

  /**
   * Recursively walk a root node (document or shadow root), pulling out:
   *   - URL-ish attribute values from every element
   *   - the text content of every text node (catches "bare" links)
   *   - and descending into any open Shadow DOM roots.
   * @param {Node} root
   */
  function walk(root) {
    // Collect interesting attribute values.
    const els = root.querySelectorAll ? root.querySelectorAll("*") : [];
    for (const el of els) {
      for (const attr of URL_ATTRS) {
        const v = el.getAttribute && el.getAttribute(attr);
        if (v) parts.push(v);
      }
      // Descend into open shadow roots.
      if (el.shadowRoot) walk(el.shadowRoot);
    }

    // Collect raw text nodes (bare links written as plain text).
    try {
      const tw = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
      let node;
      while ((node = tw.nextNode())) {
        const t = node.nodeValue;
        if (t && t.length > 3) parts.push(t);
      }
    } catch (_) {
      /* TreeWalker may reject some shadow roots; ignore. */
    }
  }

  try {
    walk(document);
  } catch (_) {}

  // Also include the full serialized HTML as a safety net.
  let html = "";
  try {
    html = document.documentElement ? document.documentElement.innerHTML : "";
  } catch (_) {}

  return parts.join("\n") + "\n" + html;
})();

/**
 * TG LINK HARVESTER — SCROLL HELPER
 * ---------------------------------------------------------------------------
 * Runs in the target PAGE (injected via chrome.scripting). Emulates a human
 * scrolling to the bottom of an infinite-scroll feed so lazily-loaded content
 * (posts, comments, search results) renders before we harvest it.
 *
 * Exposes `window.TGScroll.autoScroll(opts)`:
 *   - scrolls to the bottom up to `maxScrolls` times
 *   - waits `intervalMs` between scrolls for AJAX/XHR content to arrive
 *   - calls `onStep()` after every scroll (so callers can harvest the CURRENT
 *     DOM — important for virtualized lists like Twitter that REMOVE off-screen
 *     nodes, meaning links must be collected incrementally, not just at the end)
 *   - stops early when the document height stops growing for two consecutive
 *     scrolls (the "no more content" heuristic)
 * ---------------------------------------------------------------------------
 */
(function (root) {
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  /** Current full scrollable height of the document. */
  function docHeight() {
    return Math.max(
      document.body ? document.body.scrollHeight : 0,
      document.documentElement ? document.documentElement.scrollHeight : 0
    );
  }

  /**
   * Auto-scroll the page, invoking onStep() after each scroll.
   * @param {Object} opts
   * @param {number} opts.maxScrolls   hard cap on scroll iterations (default 5)
   * @param {number} opts.intervalMs   wait between scrolls in ms (default 1200)
   * @param {Function} [opts.onStep]   async/sync callback fired after each step
   * @returns {Promise<{scrolls:number, finalHeight:number}>}
   */
  async function autoScroll(opts) {
    const maxScrolls = Math.max(0, (opts && opts.maxScrolls) | 0) || 5;
    const intervalMs = Math.max(100, (opts && opts.intervalMs) | 0) || 1200;
    const onStep = (opts && opts.onStep) || (() => {});

    let stableCount = 0; // consecutive scrolls with no height growth
    let lastHeight = docHeight();
    let scrolls = 0;

    for (let i = 0; i < maxScrolls; i++) {
      // Scroll to the bottom; also dispatch a wheel event for listeners that
      // react to user gestures rather than scrollTop changes.
      window.scrollTo({ top: docHeight(), behavior: "auto" });
      try {
        window.dispatchEvent(new WheelEvent("wheel", { deltaY: 1200, bubbles: true }));
      } catch (_) {}

      await sleep(intervalMs);
      scrolls++;

      // Harvest whatever is currently in the DOM (covers virtualized feeds).
      try { await onStep(); } catch (_) {}

      const h = docHeight();
      if (h <= lastHeight) {
        stableCount++;
        // Two stable iterations in a row => assume nothing more will load.
        if (stableCount >= 2) break;
      } else {
        stableCount = 0;
        lastHeight = h;
      }
    }

    return { scrolls, finalHeight: docHeight() };
  }

  root.TGScroll = { autoScroll, docHeight };
})(typeof window !== "undefined" ? window : globalThis);

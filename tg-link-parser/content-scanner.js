/**
 * TG HARVESTER — EXPLOIT WARNER (defensive content scanner)
 * ---------------------------------------------------------------------------
 * Injected on web.telegram.org. PURELY DEFENSIVE: it watches incoming messages
 * with a MutationObserver and, using the LOCAL signatures in warner-config.js:
 *   1. outlines messages that match exploit/scam signatures with a red frame
 *      and a small "⚠ flagged" chip, and
 *   2. intercepts clicks on suspicious links (cyrillic/punycode/IP/risky TLD/
 *      shortener/executable) and shows a confirm modal BEFORE navigation.
 *
 * No data ever leaves the page — matching is entirely client-side. The user is
 * always free to proceed; this only adds a speed-bump for risky content.
 * ---------------------------------------------------------------------------
 */
(function () {
  if (window.__tgWarnerInjected) return;
  window.__tgWarnerInjected = true;

  const CFG = window.WARNER_CONFIG || { hashtags: [], keywordPatterns: [], linkRules: [] };
  const FLAG_CLASS = "tgw-flagged";
  const SEEN = new WeakSet();

  // ── Inject styles once ─────────────────────────────────────────────────
  const style = document.createElement("style");
  style.textContent = `
    .${FLAG_CLASS} {
      outline: 2px solid #ff3b5c !important;
      outline-offset: 1px;
      border-radius: 6px;
      position: relative;
    }
    .tgw-chip {
      display: inline-block; font: 700 10px/1.4 monospace;
      color: #fff; background: #ff3b5c; padding: 1px 6px;
      border-radius: 4px; margin: 2px 0; letter-spacing: .5px;
    }
    .tgw-modal-bg {
      position: fixed; inset: 0; background: rgba(0,0,0,.6);
      z-index: 2147483647; display: flex; align-items: center; justify-content: center;
    }
    .tgw-modal {
      background: #111520; color: #e0e8f0; border: 1px solid #ff3b5c;
      border-radius: 10px; padding: 18px 20px; max-width: 360px; width: 90%;
      font: 13px/1.5 -apple-system, sans-serif; box-shadow: 0 8px 40px rgba(0,0,0,.6);
    }
    .tgw-modal h3 { color: #ff3b5c; font-size: 14px; margin: 0 0 8px; }
    .tgw-modal code { color: #00e5ff; word-break: break-all; font-size: 12px; }
    .tgw-modal .tgw-reason { color: #ffcc00; margin: 8px 0; }
    .tgw-modal .tgw-row { display: flex; gap: 8px; margin-top: 14px; }
    .tgw-btn {
      flex: 1; border: none; border-radius: 6px; padding: 8px; cursor: pointer;
      font-weight: 600; font-size: 12px;
    }
    .tgw-btn-cancel { background: #1e2535; color: #e0e8f0; }
    .tgw-btn-go { background: #ff3b5c; color: #fff; }
  `;
  (document.head || document.documentElement).appendChild(style);

  // ── Text signature matching ─────────────────────────────────────────────
  /** @returns {string|null} the first reason a text blob is flagged, or null. */
  function textFlagReason(text) {
    if (!text) return null;
    const low = text.toLowerCase();
    for (const tag of CFG.hashtags) {
      if (low.includes(tag.toLowerCase())) return `hashtag ${tag}`;
    }
    for (const re of CFG.keywordPatterns) {
      if (re.test(text)) return "suspicious keyword";
    }
    return null;
  }

  /** Mark a message element as flagged (idempotent). */
  function flagMessage(el, reason) {
    if (el.classList.contains(FLAG_CLASS)) return;
    el.classList.add(FLAG_CLASS);
    const chip = document.createElement("div");
    chip.className = "tgw-chip";
    chip.textContent = "⚠ flagged: " + reason;
    el.prepend(chip);
  }

  // ── Link risk assessment ─────────────────────────────────────────────────
  /** @returns {string|null} reason a link is risky, or null if it looks fine. */
  function linkRiskReason(href) {
    let u;
    try { u = new URL(href, location.href); } catch (_) { return null; }
    if (u.protocol !== "http:" && u.protocol !== "https:") return null;
    for (const rule of CFG.linkRules) {
      try { if (rule.test(u, href)) return rule.reason; } catch (_) {}
    }
    return null;
  }

  // ── Confirm modal for risky links ─────────────────────────────────────────
  function confirmModal(href, reason) {
    return new Promise((resolve) => {
      const bg = document.createElement("div");
      bg.className = "tgw-modal-bg";
      bg.innerHTML = `
        <div class="tgw-modal" role="alertdialog" aria-modal="true">
          <h3>⚠ Potentially dangerous link</h3>
          <div>You are about to open:</div>
          <code></code>
          <div class="tgw-reason"></div>
          <div class="tgw-row">
            <button class="tgw-btn tgw-btn-cancel">Cancel</button>
            <button class="tgw-btn tgw-btn-go">Open anyway</button>
          </div>
        </div>`;
      // Set untrusted text via textContent (never innerHTML) to avoid injection.
      bg.querySelector("code").textContent = href;
      bg.querySelector(".tgw-reason").textContent = "Reason: " + reason;
      const close = (val) => { bg.remove(); resolve(val); };
      bg.querySelector(".tgw-btn-cancel").addEventListener("click", () => close(false));
      bg.querySelector(".tgw-btn-go").addEventListener("click", () => close(true));
      bg.addEventListener("click", (e) => { if (e.target === bg) close(false); });
      document.body.appendChild(bg);
    });
  }

  // Capture-phase click guard: block risky links until confirmed.
  document.addEventListener(
    "click",
    async (e) => {
      const a = e.target && e.target.closest && e.target.closest("a[href]");
      if (!a) return;
      if (a.dataset.tgwApproved === "1") return; // already confirmed
      const reason = linkRiskReason(a.getAttribute("href"));
      if (!reason) return;
      e.preventDefault();
      e.stopPropagation();
      const go = await confirmModal(a.href, reason);
      if (go) {
        a.dataset.tgwApproved = "1";
        window.open(a.href, a.target || "_blank", "noopener");
      }
    },
    true
  );

  // ── Scan a subtree for flagged messages + risky links ─────────────────────
  const MSG_SELECTORS = [
    ".message", ".Message", "[data-message-id]",
    '[class*="message-content"]', '[class*="Message_message"]',
  ];

  function scan(root) {
    let messages = [];
    for (const sel of MSG_SELECTORS) {
      const found = root.querySelectorAll ? root.querySelectorAll(sel) : [];
      if (found.length) { messages = found; break; }
    }
    messages.forEach((el) => {
      if (SEEN.has(el)) return;
      SEEN.add(el);
      const reason = textFlagReason(el.textContent || "");
      if (reason) flagMessage(el, reason);
      // Mark risky links visually (the click guard still enforces the modal).
      el.querySelectorAll("a[href]").forEach((a) => {
        if (linkRiskReason(a.getAttribute("href"))) a.style.color = "#ff3b5c";
      });
    });
  }

  // ── Observe the dynamic chat for new messages ─────────────────────────────
  const observer = new MutationObserver((mutations) => {
    for (const mut of mutations) {
      for (const node of mut.addedNodes) {
        if (node.nodeType === 1) scan(node.parentElement || node);
      }
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });

  // Initial sweep.
  scan(document);
  console.log("[TG-Warner] Exploit warner active (defensive, local-only).");
})();

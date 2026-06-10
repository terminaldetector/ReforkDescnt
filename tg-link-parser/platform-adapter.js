/**
 * TG HARVESTER — PLATFORM ADAPTER (Kernel & Adapter Layer)
 * ---------------------------------------------------------------------------
 * A single, environment-agnostic API that hides the differences between
 * Chrome, Firefox Desktop and Firefox for Android (142+). Everything that is
 * platform-sensitive — the extension namespace, storage backends and the
 * presence/absence of optional APIs — is resolved HERE so the rest of the code
 * never has to branch on the host browser.
 *
 * Loaded as an ES module by both the background (module service worker /
 * module event page) and the popup.
 *
 * Storage strategy (per spec):
 *   - Large/persistent results -> IndexedDB. On Firefox `storage.local` is
 *     itself IndexedDB-backed, so we use IDB directly for cross-browser, high
 *     performance, gated behind the `unlimitedStorage` permission.
 *   - Config/settings         -> ext.storage.local (small key/value).
 *   - Transient/warm-up state -> ext.storage.session when available, else a
 *     storage.local fallback (Firefox Android may lack session storage).
 * ---------------------------------------------------------------------------
 */

/** Unified WebExtension namespace. Firefox exposes `browser` (promise-based);
 *  Chrome exposes `chrome` (promise-based for most APIs under MV3). */
export const ext =
  typeof globalThis.browser !== "undefined" && globalThis.browser.runtime
    ? globalThis.browser
    : globalThis.chrome;

/** Are we running under Gecko (Firefox)? Detected via the extension URL scheme. */
export const IS_FIREFOX = (() => {
  try {
    return ext.runtime.getURL("").startsWith("moz-extension://");
  } catch (_) {
    return typeof globalThis.browser !== "undefined";
  }
})();

/**
 * Feature-detection table for optional APIs that are missing or restricted on
 * Firefox for Android. Callers consult this instead of touching the API blind.
 */
export const Features = {
  contextMenus: !!(ext.contextMenus || ext.menus),
  commands: !!ext.commands,
  sidebar: !!(ext.sidebarAction || ext.sidebarPanel),
  notifications: !!ext.notifications,
  alarms: !!ext.alarms,
  sessionStorage: !!(ext.storage && ext.storage.session),
};

/** Cached platform info (os/arch). Resolved once. */
let _platform = null;
/**
 * @returns {Promise<{os:string, arch:string, isAndroid:boolean}>}
 */
export async function getPlatform() {
  if (_platform) return _platform;
  let info = { os: "unknown", arch: "unknown" };
  try {
    info = await ext.runtime.getPlatformInfo();
  } catch (_) {}
  _platform = { ...info, isAndroid: info.os === "android" };
  return _platform;
}

// ─────────────────────────────────────────────────────────────────────────
//  Settings — small key/value config over ext.storage.local
// ─────────────────────────────────────────────────────────────────────────
export const Settings = {
  async get(keys) {
    return await ext.storage.local.get(keys);
  },
  async set(obj) {
    return await ext.storage.local.set(obj);
  },
  async remove(keys) {
    return await ext.storage.local.remove(keys);
  },
};

// ─────────────────────────────────────────────────────────────────────────
//  Session — transient state with graceful fallback (Firefox Android safe)
// ─────────────────────────────────────────────────────────────────────────
export const Session = {
  async get(keys) {
    if (Features.sessionStorage) return await ext.storage.session.get(keys);
    // Fallback: namespace inside local so we don't collide with real settings.
    const data = await ext.storage.local.get("__session__");
    const bag = data.__session__ || {};
    if (typeof keys === "string") return { [keys]: bag[keys] };
    return bag;
  },
  async set(obj) {
    if (Features.sessionStorage) return await ext.storage.session.set(obj);
    const data = await ext.storage.local.get("__session__");
    await ext.storage.local.set({ __session__: { ...(data.__session__ || {}), ...obj } });
  },
};

// ─────────────────────────────────────────────────────────────────────────
//  Store — IndexedDB-backed results store (high-performance, cross-browser)
//  Row: { key, source_url, telegram_link, link_type, page_title, detected_at }
// ─────────────────────────────────────────────────────────────────────────
const DB_NAME = "tg_link_harvester";
const DB_VERSION = 2;
const STORE = "results";
let _dbPromise = null;

function openDB() {
  if (_dbPromise) return _dbPromise;
  _dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      let os;
      if (!db.objectStoreNames.contains(STORE)) {
        os = db.createObjectStore(STORE, { keyPath: "key" });
      } else {
        os = req.transaction.objectStore(STORE);
      }
      if (!os.indexNames.contains("type")) os.createIndex("type", "link_type");
      if (!os.indexNames.contains("source")) os.createIndex("source", "source_url");
      if (!os.indexNames.contains("detected_at")) os.createIndex("detected_at", "detected_at");
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return _dbPromise;
}

export const Store = {
  /** True if a normalized key already exists. */
  async has(key) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const r = db.transaction(STORE, "readonly").objectStore(STORE).getKey(key);
      r.onsuccess = () => resolve(r.result !== undefined);
      r.onerror = () => reject(r.error);
    });
  },
  /** Insert only if the key is new. @returns {Promise<boolean>} inserted? */
  async insertUnique(row) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const os = db.transaction(STORE, "readwrite").objectStore(STORE);
      const g = os.get(row.key);
      g.onsuccess = () => {
        if (g.result) return resolve(false);
        os.put(row);
        resolve(true);
      };
      g.onerror = () => reject(g.error);
    });
  },
  async getAll() {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const r = db.transaction(STORE, "readonly").objectStore(STORE).getAll();
      r.onsuccess = () => resolve(r.result || []);
      r.onerror = () => reject(r.error);
    });
  },
  async count() {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const r = db.transaction(STORE, "readonly").objectStore(STORE).count();
      r.onsuccess = () => resolve(r.result || 0);
      r.onerror = () => reject(r.error);
    });
  },
  async clear() {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).clear();
      tx.oncomplete = () => resolve(true);
      tx.onerror = () => reject(tx.error);
    });
  },
};

/**
 * Promisified runtime messaging that works identically on Chrome & Firefox.
 * @param {object} msg
 * @returns {Promise<any>}
 */
export function sendMessage(msg) {
  try {
    const p = ext.runtime.sendMessage(msg);
    // Firefox & Chrome MV3 both return a promise; guard the legacy case.
    if (p && typeof p.then === "function") return p.catch((e) => ({ error: String(e) }));
  } catch (_) {}
  return new Promise((resolve) => {
    ext.runtime.sendMessage(msg, (resp) => {
      if (ext.runtime.lastError) return resolve({ error: ext.runtime.lastError.message });
      resolve(resp || {});
    });
  });
}

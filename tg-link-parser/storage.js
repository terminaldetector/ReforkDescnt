/**
 * TG LINK HARVESTER — STORAGE (IndexedDB)
 * ---------------------------------------------------------------------------
 * Thin IndexedDB wrapper for the harvested-results store. IndexedDB is used
 * over localStorage so the tool can hold tens of thousands of rows without the
 * ~5 MB cap. Loaded into the service worker via importScripts; attaches to
 * `self` as `TGStore`.
 *
 * Result row shape:
 *   { key, source_url, telegram_link, link_type, page_title, detected_at }
 *   - `key` (keyPath) is the normalized link, giving natural de-duplication
 *     that persists across sessions (used by the "skip known links" option).
 * ---------------------------------------------------------------------------
 */
(function (root) {
  const DB_NAME = "tg_link_harvester";
  const DB_VERSION = 2; // bumped: added page_title / detected_at + indexes
  const STORE = "results";

  let dbPromise = null;

  /** Open (and lazily create/upgrade) the database — memoized. */
  function openDB() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        let os;
        if (!db.objectStoreNames.contains(STORE)) {
          os = db.createObjectStore(STORE, { keyPath: "key" });
        } else {
          os = req.transaction.objectStore(STORE);
        }
        // Ensure indexes exist (idempotent across versions).
        if (!os.indexNames.contains("type")) os.createIndex("type", "link_type");
        if (!os.indexNames.contains("source")) os.createIndex("source", "source_url");
        if (!os.indexNames.contains("detected_at")) os.createIndex("detected_at", "detected_at");
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
    return dbPromise;
  }

  /** True if a normalized key already exists in storage. */
  async function has(key) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const req = db.transaction(STORE, "readonly").objectStore(STORE).getKey(key);
      req.onsuccess = () => resolve(req.result !== undefined);
      req.onerror = () => reject(req.error);
    });
  }

  /**
   * Insert a row only if its key is new (first-seen source wins).
   * @returns {Promise<boolean>} true if newly inserted, false if duplicate.
   */
  async function insertUnique(row) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const os = db.transaction(STORE, "readwrite").objectStore(STORE);
      const getReq = os.get(row.key);
      getReq.onsuccess = () => {
        if (getReq.result) return resolve(false);
        os.put(row);
        resolve(true);
      };
      getReq.onerror = () => reject(getReq.error);
    });
  }

  /** Read every stored row. */
  async function getAll() {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const req = db.transaction(STORE, "readonly").objectStore(STORE).getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  }

  /** Count stored rows. */
  async function count() {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const req = db.transaction(STORE, "readonly").objectStore(STORE).count();
      req.onsuccess = () => resolve(req.result || 0);
      req.onerror = () => reject(req.error);
    });
  }

  /** Wipe all stored rows. */
  async function clear() {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).clear();
      tx.oncomplete = () => resolve(true);
      tx.onerror = () => reject(tx.error);
    });
  }

  root.TGStore = { openDB, has, insertUnique, getAll, count, clear };
})(typeof self !== "undefined" ? self : globalThis);

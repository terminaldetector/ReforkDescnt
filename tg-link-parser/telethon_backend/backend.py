#!/usr/bin/env python3
"""
TG HARVESTER — TELETHON BACKEND (local helper for Backup / Cleaner)
===========================================================================
The browser cannot talk MTProto, so this small LOCAL HTTP server uses Telethon
to back up and delete the USER'S OWN Telegram messages on request from the
extension's Arsenal tab.

Security / ethics:
  * Operates ONLY on the authenticated user's own account and own messages
    (`from_user='me'`). It never targets other people's content.
  * api_id / api_hash arrive from the extension over 127.0.0.1 (or via env)
    and are saved to a local config file. The login CODE and 2FA PASSWORD are
    entered ONLY in THIS console during interactive login — never sent over
    HTTP, never seen by the extension.
  * Destructive deletes require an explicit confirm token from the UI, honor a
    dialog whitelist, and support a dry-run that only counts.
  * Batch size + inter-batch delay (the UI "load weight") throttle deletes to
    respect Telegram flood limits (max 100 message ids per delete call).

Run:
    pip install -r requirements.txt
    python backend.py            # first run logs you in interactively (console)

The server listens on http://127.0.0.1:8787 by default (PORT env to override).
"""

import json
import os
import sys
import time
import uuid
import asyncio
import threading
import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    from telethon import TelegramClient
    from telethon.tl.types import (
        MessageMediaPhoto, MessageMediaDocument,
    )
    from telethon.errors import FloodWaitError
except ImportError:
    print("[!] Telethon is not installed. Run: pip install -r requirements.txt")
    sys.exit(1)

# --------------------------------------------------------------------------- #
#  Config / globals
# --------------------------------------------------------------------------- #
HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(HERE, "config.json")
SESSION_NAME = os.path.join(HERE, "tg_harvester")
BACKUP_DIR = os.path.join(HERE, "backups")
PORT = int(os.environ.get("PORT", "8787"))

JOBS = {}            # job_id -> {status, progress, log[], done, abort}
JOBS_LOCK = threading.Lock()

client = None        # Telethon client (created after configure/login)
LOOP = None          # dedicated asyncio loop running in a background thread


def load_config():
    """Load api_id/api_hash from config.json or environment variables."""
    cfg = {}
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                cfg = json.load(f)
        except Exception:
            cfg = {}
    cfg.setdefault("api_id", os.environ.get("TG_API_ID"))
    cfg.setdefault("api_hash", os.environ.get("TG_API_HASH"))
    return cfg


def save_config(api_id, api_hash):
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump({"api_id": api_id, "api_hash": api_hash}, f)


# --------------------------------------------------------------------------- #
#  Job bookkeeping helpers
# --------------------------------------------------------------------------- #
def new_job():
    job_id = uuid.uuid4().hex[:12]
    with JOBS_LOCK:
        JOBS[job_id] = {"status": "starting", "progress": 0, "log": [], "done": False, "abort": False}
    return job_id


def job_log(job_id, msg):
    print(f"[job {job_id}] {msg}")
    with JOBS_LOCK:
        j = JOBS.get(job_id)
        if j:
            j["log"].append(msg)


def job_set(job_id, **kw):
    with JOBS_LOCK:
        j = JOBS.get(job_id)
        if j:
            j.update(kw)


def job_aborted(job_id):
    with JOBS_LOCK:
        j = JOBS.get(job_id)
        return bool(j and j["abort"])


# --------------------------------------------------------------------------- #
#  Telethon client lifecycle (runs on the dedicated asyncio loop)
# --------------------------------------------------------------------------- #
async def ensure_client(api_id=None, api_hash=None):
    """Create + connect the client, logging in interactively if needed."""
    global client
    if client is None:
        cfg = load_config()
        api_id = api_id or cfg.get("api_id")
        api_hash = api_hash or cfg.get("api_hash")
        if not api_id or not api_hash:
            raise RuntimeError("api_id / api_hash not configured")
        client = TelegramClient(SESSION_NAME, int(api_id), str(api_hash))
    if not client.is_connected():
        await client.connect()
    if not await client.is_user_authorized():
        # Interactive login happens in THIS console (input prompts), not over HTTP.
        print("\n[*] Not logged in — starting interactive login in this console.")
        await client.start()  # prompts for phone, code, and 2FA password here
    return client


def run_coro(coro, timeout=None):
    """Schedule a coroutine on the background loop and wait for the result."""
    fut = asyncio.run_coroutine_threadsafe(coro, LOOP)
    return fut.result(timeout=timeout)


# --------------------------------------------------------------------------- #
#  Backup
# --------------------------------------------------------------------------- #
async def do_backup(job_id, cfg):
    """Back up the user's own messages (+ optional media) to structured JSON."""
    await ensure_client()
    os.makedirs(BACKUP_DIR, exist_ok=True)
    dialog = cfg.get("dialog")
    with_media = cfg.get("withMedia", True)
    skip_dup = cfg.get("skipDuplicates", True)

    targets = [dialog] if dialog else [d async for d in client.iter_dialogs()]
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    out_path = os.path.join(BACKUP_DIR, f"backup-{stamp}.json")
    seen_media = set()
    records = []
    total = 0

    job_set(job_id, status="backing up")
    for tgt in targets:
        if job_aborted(job_id):
            job_log(job_id, "aborted by user")
            break
        entity = tgt
        name = getattr(tgt, "name", None) or str(tgt)
        job_log(job_id, f"dialog: {name}")
        async for msg in client.iter_messages(entity, from_user="me"):
            if job_aborted(job_id):
                break
            rec = {
                "id": msg.id,
                "date": msg.date.isoformat() if msg.date else None,
                "text": msg.message or "",
                "dialog": name,
                "has_media": bool(msg.media),
            }
            if with_media and msg.media:
                fname = f"{name}_{msg.id}"
                # Duplicate skip: keyed by file unique id when available.
                key = getattr(getattr(msg, "file", None), "id", None) or msg.id
                if not (skip_dup and key in seen_media):
                    seen_media.add(key)
                    try:
                        saved = await client.download_media(msg, file=os.path.join(BACKUP_DIR, fname))
                        rec["media_file"] = os.path.basename(saved) if saved else None
                    except Exception as e:  # noqa: BLE001
                        rec["media_error"] = str(e)
            records.append(rec)
            total += 1
            if total % 50 == 0:
                job_set(job_id, progress=min(99, total // 10))
                job_log(job_id, f"{total} messages backed up…")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({"exported_at": stamp, "count": len(records), "messages": records},
                  f, ensure_ascii=False, indent=2)
    job_log(job_id, f"saved {len(records)} messages -> {out_path}")
    job_set(job_id, status="done", progress=100, done=True)


# --------------------------------------------------------------------------- #
#  Delete (own messages only)
# --------------------------------------------------------------------------- #
def _media_matches(msg, media_type):
    if media_type == "photo":
        return isinstance(msg.media, MessageMediaPhoto)
    if media_type in ("video", "document", "voice"):
        return isinstance(msg.media, MessageMediaDocument)
    return False


async def _collect_ids(entity, cfg):
    """Collect ids of the user's own messages that match the delete filters."""
    ids = []
    older_than = cfg.get("olderThanDays")
    cutoff = None
    if cfg.get("scope") == "older-than" and older_than is not None:
        cutoff = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=int(older_than))
    media_type = cfg.get("mediaType") if cfg.get("scope") == "media-type" else None

    async for msg in client.iter_messages(entity, from_user="me"):
        if cutoff and msg.date and msg.date > cutoff:
            continue
        if media_type:
            if not (msg.media and _media_matches(msg, media_type)):
                continue
        ids.append(msg.id)
    return ids


async def do_delete(job_id, cfg):
    """Delete the user's own messages per scope, throttled by batch weight."""
    await ensure_client()
    dry = cfg.get("dryRun", True)
    if not dry and cfg.get("confirmToken") != "DELETE":
        job_log(job_id, "refused: missing confirm token")
        job_set(job_id, status="refused", done=True)
        return

    whitelist = {w.lstrip("@").lower() for w in cfg.get("whitelist", [])}
    batch = cfg.get("batch", {})
    batch_size = max(1, min(100, int(batch.get("batchSize", 20))))
    batch_delay = float(batch.get("batchDelayMs", 3000)) / 1000.0
    scope = cfg.get("scope")

    if scope == "wipe-everything":
        targets = [d async for d in client.iter_dialogs()]
    else:
        targets = [cfg.get("dialog")]

    grand_total = 0
    job_set(job_id, status=("dry-run" if dry else "deleting"))
    for tgt in targets:
        if job_aborted(job_id):
            job_log(job_id, "aborted by user")
            break
        if tgt is None:
            job_log(job_id, "no target dialog specified")
            continue
        name = getattr(tgt, "name", None) or str(tgt)
        if name and name.lstrip("@").lower() in whitelist:
            job_log(job_id, f"skip (whitelist): {name}")
            continue
        try:
            ids = await _collect_ids(tgt, cfg)
        except Exception as e:  # noqa: BLE001
            job_log(job_id, f"error reading {name}: {e}")
            continue

        job_log(job_id, f"{name}: {len(ids)} matching own-messages")
        if dry:
            grand_total += len(ids)
            continue

        for i in range(0, len(ids), batch_size):
            if job_aborted(job_id):
                break
            chunk = ids[i:i + batch_size]
            try:
                await client.delete_messages(tgt, chunk)
                grand_total += len(chunk)
                job_set(job_id, progress=min(99, (i + len(chunk)) * 100 // max(1, len(ids))))
                job_log(job_id, f"{name}: deleted {i + len(chunk)}/{len(ids)}")
            except FloodWaitError as e:
                job_log(job_id, f"flood wait {e.seconds}s — sleeping")
                await asyncio.sleep(e.seconds + 1)
            except Exception as e:  # noqa: BLE001
                job_log(job_id, f"delete error: {e}")
            await asyncio.sleep(batch_delay)

    verb = "would delete" if dry else "deleted"
    job_log(job_id, f"{verb} {grand_total} message(s) total")
    job_set(job_id, status="done", progress=100, done=True)


# --------------------------------------------------------------------------- #
#  HTTP server
# --------------------------------------------------------------------------- #
class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):  # silence default logging
        pass

    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json(self, code, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self._cors()
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        if not length:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            return {}

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        if self.path == "/ping":
            me = None
            authorized = False
            try:
                if client is not None:
                    authorized = run_coro(client.is_user_authorized(), timeout=8)
                    if authorized:
                        u = run_coro(client.get_me(), timeout=8)
                        me = (u.username or u.first_name) if u else None
            except Exception as e:  # noqa: BLE001
                return self._json(200, {"ok": True, "authorized": False, "error": str(e)})
            return self._json(200, {"ok": True, "authorized": authorized, "me": me})

        if self.path.startswith("/job/"):
            job_id = self.path.split("/job/", 1)[1]
            with JOBS_LOCK:
                j = JOBS.get(job_id)
                if not j:
                    return self._json(404, {"ok": False, "error": "no such job"})
                return self._json(200, {"ok": True, **j})

        return self._json(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        body = self._read_body()

        if self.path == "/configure":
            api_id = body.get("api_id")
            api_hash = body.get("api_hash")
            if not api_id or not api_hash:
                return self._json(400, {"ok": False, "error": "api_id/api_hash required"})
            save_config(api_id, api_hash)
            try:
                # Connect (and, if needed, prompt for login in THIS console).
                run_coro(ensure_client(api_id, api_hash), timeout=120)
                return self._json(200, {"ok": True})
            except Exception as e:  # noqa: BLE001
                return self._json(200, {"ok": False, "error": f"{e} (check the backend console for a login prompt)"})

        if self.path == "/backup":
            job_id = new_job()
            asyncio.run_coroutine_threadsafe(_guard(job_id, do_backup, body), LOOP)
            return self._json(200, {"ok": True, "jobId": job_id})

        if self.path == "/delete":
            job_id = new_job()
            asyncio.run_coroutine_threadsafe(_guard(job_id, do_delete, body), LOOP)
            return self._json(200, {"ok": True, "jobId": job_id})

        if self.path.startswith("/abort/"):
            job_id = self.path.split("/abort/", 1)[1]
            job_set(job_id, abort=True)
            return self._json(200, {"ok": True})

        return self._json(404, {"ok": False, "error": "not found"})


async def _guard(job_id, fn, cfg):
    """Run a job coroutine, capturing any exception into the job log."""
    try:
        await fn(job_id, cfg)
    except Exception as e:  # noqa: BLE001
        job_log(job_id, f"FATAL: {e}")
        job_set(job_id, status="error", done=True)


def start_loop():
    """Run the dedicated asyncio loop forever in this thread."""
    global LOOP
    LOOP = asyncio.new_event_loop()
    asyncio.set_event_loop(LOOP)
    LOOP.run_forever()


def main():
    # Background asyncio loop for all Telethon work.
    threading.Thread(target=start_loop, daemon=True).start()
    time.sleep(0.2)

    # Try to connect with an existing session/config so /ping is useful at once.
    cfg = load_config()
    if cfg.get("api_id") and cfg.get("api_hash"):
        try:
            run_coro(ensure_client(), timeout=120)
            print("[*] Telethon client connected.")
        except Exception as e:  # noqa: BLE001
            print(f"[!] Could not connect yet: {e}")

    httpd = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"[*] TG Harvester backend listening on http://127.0.0.1:{PORT}")
    print("[*] Leave this window open. Ctrl+C to stop.")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[*] Shutting down.")


if __name__ == "__main__":
    main()

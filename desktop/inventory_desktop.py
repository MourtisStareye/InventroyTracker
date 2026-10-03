"""Inventory Tracker desktop companion and LAN sync server (Python 3.10+)."""
from __future__ import annotations

import json
import base64
import binascii
import hashlib
import urllib.request
import urllib.error
import ctypes
import os
import queue
import secrets
import shutil
import socket
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import uuid
from dataclasses import asdict, dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from urllib.parse import urlencode

import qrcode


APP_DIR = Path(os.getenv("LOCALAPPDATA", Path.home())) / "InventoryTracker"
DB_PATH = APP_DIR / "inventory.sqlite3"
FIELDS = ("id", "name", "barcode", "brand", "quantity", "category", "imageUrl", "location", "notes", "price", "expirationDate", "updatedAt", "version", "deleted")
GITHUB_REPO = "MourtisStareye/InventroyTracker"
DESKTOP_VERSION = "1.1.0"


def _dpapi(data: bytes, protect: bool) -> bytes:
    """Protect a secret for the current Windows user using DPAPI."""
    if os.name != "nt":
        raise OSError("Secure token storage is available only on Windows.")
    from ctypes import wintypes
    class Blob(ctypes.Structure):
        _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_ubyte))]
    source = (ctypes.c_ubyte * len(data)).from_buffer_copy(data)
    in_blob = Blob(len(data), source)
    out_blob = Blob()
    crypt = ctypes.WinDLL("crypt32", use_last_error=True)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    fn = crypt.CryptProtectData if protect else crypt.CryptUnprotectData
    if protect:
        fn.argtypes = [ctypes.POINTER(Blob), wintypes.LPCWSTR, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
        ok = fn(ctypes.byref(in_blob), "Inventory Tracker GitHub token", None, None, None, 1, ctypes.byref(out_blob))
    else:
        fn.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
        ok = fn(ctypes.byref(in_blob), None, None, None, None, 1, ctypes.byref(out_blob))
    if not ok:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        kernel.LocalFree.argtypes = [ctypes.c_void_p]
        kernel.LocalFree.restype = ctypes.c_void_p
        kernel.LocalFree(out_blob.pbData)


def _github_token_path() -> Path:
    return APP_DIR / "github-token.dpapi"


def _read_github_token() -> str:
    path = _github_token_path()
    if not path.exists():
        return ""
    return _dpapi(path.read_bytes(), False).decode("utf-8")


def _save_github_token(token: str) -> None:
    APP_DIR.mkdir(parents=True, exist_ok=True)
    path = _github_token_path()
    if token:
        path.write_bytes(_dpapi(token.encode("utf-8"), True))
    else:
        path.unlink(missing_ok=True)


def _github_latest_release(token: str) -> dict:
    request = urllib.request.Request(
        f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest",
        headers={"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "InventoryTracker"},
    )
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def _version_tuple(value: str) -> tuple[int, ...]:
    parts = value.strip().lstrip("vV").split(".")
    try:
        return tuple(int(part.split("-", 1)[0]) for part in parts)
    except ValueError:
        return ()


def now_ms() -> int:
    return int(time.time() * 1000)


def private_lan_ip() -> str:
    """Find the IPv4 address Windows would use for LAN traffic without sending data."""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("192.0.2.1", 9))
        address = sock.getsockname()[0]
        sock.close()
        parts = [int(part) for part in address.split(".")]
        if parts[0] == 10 or (parts[0] == 192 and parts[1] == 168) or (parts[0] == 172 and 16 <= parts[1] <= 31) or (parts[0] == 169 and parts[1] == 254):
            return address
    except OSError:
        pass
    return "127.0.0.1"


def find_adb_executable() -> str | None:
    """Find Android Debug Bridge from PATH or a standard Android SDK location."""
    adb = shutil.which("adb")
    if adb:
        return adb
    roots = [os.getenv("ANDROID_SDK_ROOT"), os.getenv("ANDROID_HOME")]
    local_app_data = os.getenv("LOCALAPPDATA")
    if local_app_data:
        roots.append(str(Path(local_app_data) / "Android" / "Sdk"))
    for root in roots:
        if root:
            candidate = Path(root) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
            if candidate.is_file():
                return str(candidate)
    return None


class InventoryStore:
    def __init__(self, path: Path = DB_PATH):
        APP_DIR.mkdir(parents=True, exist_ok=True)
        self.photo_dir = APP_DIR / "photos"
        self.photo_dir.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.executescript("""
          CREATE TABLE IF NOT EXISTS items (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, barcode TEXT, brand TEXT, quantity INTEGER NOT NULL DEFAULT 1,
            category TEXT, imageUrl TEXT, location TEXT, notes TEXT, price REAL, expirationDate INTEGER,
            updatedAt INTEGER NOT NULL, version INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0,
            originDevice TEXT NOT NULL DEFAULT 'desktop', localImagePath TEXT);
          CREATE INDEX IF NOT EXISTS items_barcode ON items(barcode);
          CREATE TABLE IF NOT EXISTS changes (cursor INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL);
          CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """)
        columns = {row[1] for row in self.db.execute("PRAGMA table_info(items)")}
        if "localImagePath" not in columns:
            self.db.execute("ALTER TABLE items ADD COLUMN localImagePath TEXT")
        self.db.commit()
        if not self.setting("token"):
            self.set_setting("token", secrets.token_urlsafe(24))

    def photo_path(self, item_id: str) -> Path:
        # Validate IDs before using them in a filesystem path.
        return self.photo_dir / f"{uuid.UUID(item_id)}.jpg"

    def save_photo(self, item_id: str, encoded: str) -> None:
        try:
            raw = base64.b64decode(encoded, validate=True)
        except (binascii.Error, ValueError) as error:
            raise ValueError("Invalid photo encoding.") from error
        if not raw or len(raw) > 10_000_000:
            raise ValueError("Photo must be between 1 byte and 10 MB.")
        destination = self.photo_path(item_id)
        temporary = destination.with_suffix(".tmp")
        temporary.write_bytes(raw)
        temporary.replace(destination)

    def setting(self, key: str) -> str | None:
        row = self.db.execute("SELECT value FROM settings WHERE key=?", (key,)).fetchone()
        return row["value"] if row else None

    def set_setting(self, key: str, value: str) -> None:
        with self.lock, self.db:
            self.db.execute("INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (key, value))

    def list_items(self, query: str = "", category: str = "All") -> list[sqlite3.Row]:
        with self.lock:
            sql = "SELECT * FROM items WHERE deleted=0"
            args: list[object] = []
            if query.strip():
                sql += " AND (name LIKE ? OR barcode LIKE ? OR brand LIKE ? OR category LIKE ? OR location LIKE ?)"
                term = f"%{query.strip()}%"
                args.extend([term] * 5)
            if category != "All":
                sql += " AND category=?"
                args.append(category)
            return self.db.execute(sql + " ORDER BY name COLLATE NOCASE", args).fetchall()

    def categories(self) -> list[str]:
        return [row[0] for row in self.db.execute("SELECT DISTINCT category FROM items WHERE deleted=0 AND category IS NOT NULL AND category<>'' ORDER BY category COLLATE NOCASE")]

    def data_version(self) -> int:
        with self.lock:
            return int(self.db.execute("PRAGMA data_version").fetchone()[0])

    def get(self, item_id: str) -> sqlite3.Row | None:
        return self.db.execute("SELECT * FROM items WHERE id=? AND deleted=0", (item_id,)).fetchone()

    def save(self, data: dict, item_id: str | None = None) -> str:
        name = str(data.get("name", "")).strip()
        if not name:
            raise ValueError("Item name is required.")
        price_text = str(data.get("price", "")).strip()
        price = float(price_text) if price_text else None
        qty = max(0, int(data.get("quantity", 1)))
        existing = self.db.execute("SELECT * FROM items WHERE id=?", (item_id,)).fetchone() if item_id else None
        row_id = item_id or str(uuid.uuid4())
        updated = {"id": row_id, "name": name, "barcode": _none(data.get("barcode")), "brand": _none(data.get("brand")), "quantity": qty,
                  "category": _none(data.get("category")), "imageUrl": _none(data.get("imageUrl")), "location": _none(data.get("location")),
                  "notes": _none(data.get("notes")), "price": price, "expirationDate": _none_int(data.get("expirationDate")),
                  "updatedAt": now_ms(), "version": (int(existing["version"]) + 1 if existing else 1), "deleted": 0, "originDevice": "desktop"}
        with self.lock, self.db:
            self._write_record(updated)
            if "localImagePath" in data:
                self.db.execute("UPDATE items SET localImagePath=? WHERE id=?", (_none(data["localImagePath"]), row_id))
        return row_id

    def adjust_quantity(self, item_id: str, delta: int) -> None:
        row = self.get(item_id)
        if row:
            data = {key: row[key] for key in FIELDS}
            data["localImagePath"] = row["localImagePath"]
            data["quantity"] = max(0, int(row["quantity"]) + delta)
            self.save(data, item_id)

    def delete(self, item_id: str) -> None:
        row = self.db.execute("SELECT * FROM items WHERE id=? AND deleted=0", (item_id,)).fetchone()
        if not row:
            return
        data = {key: row[key] for key in FIELDS}
        data["localImagePath"] = row["localImagePath"]
        data.update(deleted=1, updatedAt=now_ms(), version=int(row["version"]) + 1, originDevice="desktop")
        with self.lock, self.db:
            self._write_record(data)

    def clear_inventory(self) -> int:
        rows = self.db.execute("SELECT * FROM items WHERE deleted=0").fetchall()
        with self.lock, self.db:
            for row in rows:
                record = {key: row[key] for key in FIELDS}
                record["localImagePath"] = row["localImagePath"]
                record.update(deleted=1, updatedAt=now_ms(), version=int(row["version"]) + 1, originDevice="desktop")
                self._write_record(record)
        return len(rows)

    def queue_full_sync(self) -> int:
        """Re-publish every canonical row in the change feed for the next phone sync."""
        with self.lock, self.db:
            rows = self.db.execute("SELECT id FROM items").fetchall()
            self.db.executemany("INSERT INTO changes(id) VALUES(?)", ((row["id"],) for row in rows))
        return len(rows)

    def sync(self, request: dict) -> dict:
        if not isinstance(request, dict) or not isinstance(request.get("changes"), list):
            raise ValueError("Expected a sync request with a changes array.")
        device = str(request.get("deviceId", ""))[:200]
        if not device:
            raise ValueError("deviceId is required.")
        cursor = max(0, int(request.get("cursor", 0)))
        accepted: list[str] = []
        extras: dict[str, dict] = {}
        with self.lock, self.db:
            for incoming in request["changes"]:
                record = _validate_record(incoming)
                record_id = record["id"]
                old = self.db.execute("SELECT * FROM items WHERE id=?", (record_id,)).fetchone()
                wins = old is None or int(record["updatedAt"]) > int(old["updatedAt"]) or (
                    int(record["updatedAt"]) == int(old["updatedAt"]) and device > old["originDevice"])
                if wins:
                    record["version"] = (int(old["version"]) if old else 0) + 1
                    record["originDevice"] = device
                    self._write_record(record)
                else:
                    record = {key: old[key] for key in FIELDS}
                accepted.append(record_id)
                extras[record_id] = self._sync_record(record_id)
            latest_cursor = int(self.db.execute("SELECT COALESCE(MAX(cursor),0) FROM changes").fetchone()[0])
            changed_ids = [r[0] for r in self.db.execute("SELECT id FROM changes WHERE cursor>? ORDER BY cursor", (cursor,))]
            records_by_id = {record_id: self._sync_record(record_id) for record_id in changed_ids}
            records_by_id.update(extras)
            return {"records": list(records_by_id.values()), "acknowledgedIds": accepted, "cursor": latest_cursor}

    def _write_record(self, record: dict) -> None:
        local_photo = record.get("localImagePath")
        if record.get("imageUrl") == f"inventory-photo://{record['id']}":
            local_photo = str(self.photo_path(record["id"]))
        values = [record.get(k) for k in FIELDS] + [record.get("originDevice", "desktop"), local_photo]
        self.db.execute("""INSERT INTO items(id,name,barcode,brand,quantity,category,imageUrl,location,notes,price,expirationDate,updatedAt,version,deleted,originDevice,localImagePath)
          VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
          name=excluded.name,barcode=excluded.barcode,brand=excluded.brand,quantity=excluded.quantity,category=excluded.category,imageUrl=excluded.imageUrl,
          location=excluded.location,notes=excluded.notes,price=excluded.price,expirationDate=excluded.expirationDate,updatedAt=excluded.updatedAt,
          version=excluded.version,deleted=excluded.deleted,originDevice=excluded.originDevice,localImagePath=excluded.localImagePath""", values)
        self.db.execute("INSERT INTO changes(id) VALUES(?)", (record["id"],))

    def _sync_record(self, item_id: str) -> dict:
        row = self.db.execute("SELECT * FROM items WHERE id=?", (item_id,)).fetchone()
        record = {key: row[key] for key in FIELDS}
        record["deleted"] = bool(record["deleted"])
        return record


def _none(value):
    value = str(value).strip() if value is not None else ""
    return value or None


def _none_int(value):
    if value in (None, ""):
        return None
    return int(value)


def _validate_record(record) -> dict:
    if not isinstance(record, dict):
        raise ValueError("Each change must be an object.")
    required = ("id", "name", "quantity", "updatedAt", "version", "deleted")
    if any(key not in record for key in required):
        raise ValueError("A change is missing required fields.")
    return {"id": str(record["id"]), "name": str(record["name"]).strip(), "barcode": record.get("barcode"), "brand": record.get("brand"),
            "quantity": max(0, int(record["quantity"])), "category": record.get("category"), "imageUrl": record.get("imageUrl"),
            "location": record.get("location"), "notes": record.get("notes"), "price": record.get("price"),
            "expirationDate": record.get("expirationDate"), "updatedAt": int(record["updatedAt"]), "version": int(record["version"]),
            "deleted": 1 if record["deleted"] else 0, "originDevice": ""}


class SyncServer:
    def __init__(self, store: InventoryStore, port: int = 8765, on_sync_received=None):
        self.store, self.port = store, port
        self.on_sync_received = on_sync_received
        self.sync_requested = threading.Event()
        self.server = None
        self.servers = []
        self.thread = None
        self.threads = []

    def start(self) -> str:
        if self.server:
            return self.address
        host = private_lan_ip()
        store = self.store
        notify_sync = self.on_sync_received
        sync_requested = self.sync_requested

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def _reply(self, status: int, payload: dict):
                body = json.dumps(payload, separators=(",", ":")).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(body)

            def do_POST(self):
                if not secrets.compare_digest(self.headers.get("Authorization", ""), "Bearer " + (store.setting("token") or "")):
                    return self._reply(401, {"error": "Unauthorized"})
                try:
                    length = int(self.headers.get("Content-Length", "0"))
                    if length <= 0 or length > 14_000_000:
                        raise ValueError("Invalid request size.")
                    payload = json.loads(self.rfile.read(length))
                    if self.path == "/api/v1/sync":
                        if length > 2_000_000:
                            raise ValueError("Sync request is too large.")
                        response = store.sync(payload)
                        if notify_sync:
                            notify_sync()
                        return self._reply(200, response)
                    photo_prefix = "/api/v1/photos/"
                    if self.path.startswith(photo_prefix):
                        if not isinstance(payload, dict):
                            raise ValueError("Expected a photo object.")
                        item_id = str(uuid.UUID(self.path[len(photo_prefix):]))
                        if payload.get("mimeType") != "image/jpeg" or not isinstance(payload.get("data"), str):
                            raise ValueError("Expected a JPEG photo payload.")
                        store.save_photo(item_id, payload["data"])
                        return self._reply(200, {"uploaded": True})
                    return self._reply(404, {"error": "Not found"})
                except (ValueError, TypeError, KeyError, json.JSONDecodeError) as error:
                    return self._reply(400, {"error": str(error)})

            def do_GET(self):
                if not secrets.compare_digest(self.headers.get("Authorization", ""), "Bearer " + (store.setting("token") or "")):
                    return self._reply(401, {"error": "Unauthorized"})
                if self.path == "/api/v1/trigger":
                    requested = sync_requested.wait(timeout=18)
                    if requested:
                        sync_requested.clear()
                    return self._reply(200, {"syncRequested": requested})
                photo_prefix = "/api/v1/photos/"
                if not self.path.startswith(photo_prefix):
                    return self._reply(404, {"error": "Not found"})
                try:
                    item_id = str(uuid.UUID(self.path[len(photo_prefix):]))
                    photo_path = store.photo_path(item_id)
                    if not photo_path.is_file():
                        return self._reply(404, {"error": "Photo not found"})
                    encoded = base64.b64encode(photo_path.read_bytes()).decode("ascii")
                    return self._reply(200, {"mimeType": "image/jpeg", "data": encoded})
                except ValueError as error:
                    return self._reply(400, {"error": str(error)})

        self.server = ThreadingHTTPServer((host, self.port), Handler)
        self.server.daemon_threads = True
        self.servers = [self.server]
        if host != "127.0.0.1":
            try:
                loopback_server = ThreadingHTTPServer(("127.0.0.1", self.server.server_port), Handler)
                loopback_server.daemon_threads = True
                self.servers.append(loopback_server)
            except OSError:
                self.server.server_close()
                self.server = None
                self.servers = []
                raise
        self.address = f"{host}:{self.server.server_port}"
        self.threads = [
            threading.Thread(target=server.serve_forever, name=f"InventorySync-{index}", daemon=True)
            for index, server in enumerate(self.servers)
        ]
        for thread in self.threads:
            thread.start()
        self.thread = self.threads[0]
        return self.address

    def request_phone_sync(self) -> None:
        self.sync_requested.set()

    def stop(self):
        if self.server:
            for server in self.servers:
                server.shutdown()
                server.server_close()
            self.server = None
            self.servers = []
            self.thread = None
            self.threads = []


class InventoryApp(tk.Tk):
    BG, PANEL, INK, MUTED, GREEN = "#f4f7f3", "#ffffff", "#19332b", "#718078", "#247a56"

    def __init__(self):
        super().__init__()
        self.title("Inventory Tracker")
        self.geometry("1180x760")
        self.minsize(900, 600)
        self.configure(bg=self.BG)
        self.store = InventoryStore()
        self.sync_notifications = queue.Queue()
        self.server = SyncServer(self.store, on_sync_received=lambda: self.sync_notifications.put(True))
        self._last_data_version = self.store.data_version()
        self.selected_id: str | None = None
        self._style()
        self._build()
        self.refresh()
        self.after(500, self._poll_sync_updates)
        self.after(1800, self._check_desktop_update_on_launch)
        try:
            address = self.server.start()
            self.status.configure(text=f"Sync server ready at {address}")
        except OSError as error:
            self.status.configure(text=f"LAN sync unavailable: {error}")
        self.protocol("WM_DELETE_WINDOW", self._close)

    def _style(self):
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure("Treeview", rowheight=42, background="white", fieldbackground="white", foreground=self.INK, borderwidth=0, font=("Segoe UI", 10))
        style.configure("Treeview.Heading", background=self.BG, foreground=self.MUTED, relief="flat", font=("Segoe UI", 9, "bold"))
        style.map("Treeview", background=[("selected", "#e3f1e9")], foreground=[("selected", self.INK)])
        style.configure("TCombobox", padding=8)

    def _build(self):
        header = tk.Frame(self, bg=self.PANEL, padx=28, pady=18)
        header.pack(fill="x")
        tk.Label(header, text="INVENTORY TRACKER", bg=self.PANEL, fg=self.GREEN, font=("Segoe UI", 9, "bold")).pack(anchor="w")
        row = tk.Frame(header, bg=self.PANEL)
        row.pack(fill="x", pady=(4, 0))
        tk.Label(row, text="Your inventory", bg=self.PANEL, fg=self.INK, font=("Segoe UI", 22, "bold")).pack(side="left")
        self.count_label = tk.Label(row, text="", bg=self.PANEL, fg=self.MUTED, font=("Segoe UI", 10))
        self.count_label.pack(side="left", padx=14, pady=(8, 0))
        self._button(row, "+  Add item", self.add_item, primary=True).pack(side="right")
        self._button(row, "Settings", self.open_settings).pack(side="right", padx=10)
        self._button(row, "Sync", self.force_sync_database).pack(side="right", padx=10)

        body = tk.Frame(self, bg=self.BG, padx=28, pady=20)
        body.pack(fill="both", expand=True)
        filters = tk.Frame(body, bg=self.BG)
        filters.pack(fill="x", pady=(0, 14))
        self.search_var = tk.StringVar()
        search = tk.Entry(filters, textvariable=self.search_var, font=("Segoe UI", 11), relief="flat", bg=self.PANEL, fg=self.INK, insertbackground=self.INK)
        search.pack(side="left", fill="x", expand=True, ipady=12, padx=(0, 12))
        self.search_var.trace_add("write", lambda *_: self.refresh())
        self.category_var = tk.StringVar(value="All")
        self.category_box = ttk.Combobox(filters, textvariable=self.category_var, state="readonly", width=20)
        self.category_box.pack(side="right", ipady=7)
        self.category_box.bind("<<ComboboxSelected>>", lambda _e: self.refresh())

        card = tk.Frame(body, bg=self.PANEL, highlightbackground="#e3eae5", highlightthickness=1)
        card.pack(fill="both", expand=True)
        columns = ("name", "barcode", "category", "location", "quantity", "price")
        self.table = ttk.Treeview(card, columns=columns, show="headings", selectmode="browse")
        headings = (("name", "ITEM"), ("barcode", "BARCODE"), ("category", "CATEGORY"), ("location", "LOCATION"), ("quantity", "QTY"), ("price", "PRICE"))
        for key, title in headings:
            self.table.heading(key, text=title)
        self.table.column("name", width=240, anchor="w")
        self.table.column("barcode", width=150, anchor="w")
        self.table.column("category", width=140, anchor="w")
        self.table.column("location", width=170, anchor="w")
        self.table.column("quantity", width=70, anchor="center")
        self.table.column("price", width=90, anchor="e")
        scroll = ttk.Scrollbar(card, orient="vertical", command=self.table.yview)
        self.table.configure(yscrollcommand=scroll.set)
        self.table.pack(side="left", fill="both", expand=True, padx=10, pady=10)
        scroll.pack(side="right", fill="y", pady=10)
        self.table.bind("<Double-1>", lambda _e: self.edit_selected())
        self.table.bind("<Return>", lambda _e: self.edit_selected())
        self.table.bind("<Button-3>", self._context_menu)
        footer = tk.Frame(body, bg=self.BG)
        footer.pack(fill="x", pady=(12, 0))
        tk.Label(footer, text="Double-click an item to view or edit. Right-click for quick quantity actions.", bg=self.BG, fg=self.MUTED, font=("Segoe UI", 9)).pack(side="left")
        self.status = tk.Label(footer, text="Stored on this computer", bg=self.BG, fg=self.MUTED, font=("Segoe UI", 9))
        self.status.pack(side="right")

    def _button(self, parent, label, command, primary=False):
        return tk.Button(parent, text=label, command=command, font=("Segoe UI", 10, "bold" if primary else "normal"),
                         bg=self.GREEN if primary else "#e9efea", fg="white" if primary else self.INK,
                         activebackground="#1b6547" if primary else "#dce6df", activeforeground="white" if primary else self.INK,
                         relief="flat", padx=17, pady=10, cursor="hand2")

    def refresh(self):
        if not hasattr(self, "table"):
            return
        selected_category = self.category_var.get() or "All"
        categories = ["All", *self.store.categories()]
        self.category_box.configure(values=categories)
        if selected_category not in categories:
            selected_category = "All"
            self.category_var.set("All")
        rows = self.store.list_items(self.search_var.get(), selected_category)
        self.table.delete(*self.table.get_children())
        for row in rows:
            price = f"${row['price']:.2f}" if row["price"] is not None else "—"
            self.table.insert("", "end", iid=row["id"], values=(row["name"], row["barcode"] or "—", row["category"] or "—", row["location"] or "—", row["quantity"], price))
        total = sum(int(row["quantity"]) for row in self.store.list_items())
        self.count_label.configure(text=f"{len(self.store.list_items())} items  ·  {total} total units")

    def _poll_sync_updates(self):
        sync_received = False
        while True:
            try:
                self.sync_notifications.get_nowait()
                sync_received = True
            except queue.Empty:
                break
        data_version = self.store.data_version()
        if sync_received or data_version != self._last_data_version:
            self._last_data_version = data_version
            self.refresh()
            if sync_received:
                self.status.configure(text="Phone sync received; inventory refreshed")
            else:
                self.status.configure(text="Inventory updated by another desktop window")
        self.after(500, self._poll_sync_updates)

    def _selected(self) -> str | None:
        selection = self.table.selection()
        return selection[0] if selection else None

    def add_item(self):
        self._edit_dialog()

    def edit_selected(self):
        item_id = self._selected()
        if item_id:
            row = self.store.get(item_id)
            if row:
                self._edit_dialog(row)

    def _edit_dialog(self, row=None):
        fields = [("name", "Item name *"), ("barcode", "Barcode / UPC"), ("brand", "Brand"), ("category", "Category"), ("quantity", "Quantity"),
                  ("price", "Price"), ("location", "Location"), ("imageUrl", "Image URL"), ("localImagePath", "Local photo"),
                  ("expirationDate", "Expiration date (Unix ms, optional)"), ("notes", "Notes")]
        dialog = tk.Toplevel(self)
        dialog.title("Edit item" if row else "Add item")
        dialog.configure(bg=self.PANEL)
        dialog.transient(self)
        dialog.grab_set()
        dialog.geometry("700x760")
        tk.Label(dialog, text="Edit item" if row else "Add to inventory", bg=self.PANEL, fg=self.INK, font=("Segoe UI", 18, "bold")).pack(anchor="w", padx=24, pady=(20, 10))
        form = tk.Frame(dialog, bg=self.PANEL, padx=24)
        form.pack(fill="both", expand=True)
        vars = {}
        for index, (key, title) in enumerate(fields):
            tk.Label(form, text=title, bg=self.PANEL, fg=self.MUTED, font=("Segoe UI", 9, "bold")).grid(row=index, column=0, sticky="w", pady=(7, 2))
            if key == "notes":
                entry = tk.Text(form, height=4, font=("Segoe UI", 10), relief="solid", bd=1)
                entry.grid(row=index, column=1, sticky="ew", pady=(7, 2))
                entry.insert("1.0", row[key] or "" if row else "")
                vars[key] = entry
            elif key == "localImagePath":
                var = tk.StringVar(value=str(row["localImagePath"] if row and row["localImagePath"] else ""))
                entry = tk.Entry(form, textvariable=var, font=("Segoe UI", 9), relief="solid", bd=1, state="readonly")
                entry.grid(row=index, column=1, sticky="ew", pady=(7, 2), ipady=5)
                vars[key] = var

                def choose_photo(target=var):
                    selected = filedialog.askopenfilename(
                        parent=dialog,
                        title="Choose an inventory photo",
                        filetypes=[("Image files", "*.png *.jpg *.jpeg *.gif *.bmp *.webp"), ("All files", "*.*")],
                    )
                    if selected:
                        target.set(selected)
                        filename = Path(selected).stem.strip()
                        if filename:
                            vars["name"].set(filename)

                def open_photo(target=var):
                    path = Path(target.get())
                    if path.is_file():
                        os.startfile(str(path))
                    else:
                        messagebox.showwarning("Photo unavailable", "The saved photo file could not be found.", parent=dialog)

                photo_controls = tk.Frame(form, bg=self.PANEL)
                photo_controls.grid(row=index, column=2, sticky="w", padx=(8, 0), pady=(7, 2))
                self._button(photo_controls, "Open", open_photo).pack(side="left", padx=(0, 5))
                self._button(photo_controls, "Choose…", choose_photo).pack(side="left")
                self._button(photo_controls, "Clear", lambda target=var: target.set("")).pack(side="left", padx=(5, 0))
            else:
                var = tk.StringVar(value=str(row[key] if row and row[key] is not None else (1 if key == "quantity" else "")))
                entry = tk.Entry(form, textvariable=var, font=("Segoe UI", 10), relief="solid", bd=1)
                entry.grid(row=index, column=1, sticky="ew", pady=(7, 2), ipady=5)
                vars[key] = var
        form.columnconfigure(1, weight=1)

        def save():
            data = {key: value.get() if isinstance(value, tk.StringVar) else value.get("1.0", "end-1c") for key, value in vars.items()}
            try:
                selected_photo = data.get("localImagePath", "")
                if selected_photo:
                    source = Path(selected_photo).expanduser().resolve()
                    image_dir = (APP_DIR / "images").resolve()
                    image_dir.mkdir(parents=True, exist_ok=True)
                    try:
                        source.relative_to(image_dir)
                        is_already_stored = True
                    except ValueError:
                        is_already_stored = False
                    if not source.is_file():
                        raise ValueError("The selected photo could not be found. Choose it again before saving.")
                    if not is_already_stored:
                        destination = image_dir / f"{uuid.uuid4().hex}_{source.name}"
                        shutil.copy2(source, destination)
                        data["localImagePath"] = str(destination)
                self.store.save(data, row["id"] if row else None)
            except (OSError, ValueError, OverflowError) as error:
                messagebox.showerror("Can't save item", str(error), parent=dialog)
                return
            dialog.destroy()
            self.refresh()
            self.status.configure(text="Saved on this computer")

        buttons = tk.Frame(dialog, bg=self.PANEL, padx=24, pady=18)
        buttons.pack(fill="x")
        if row:
            self._button(buttons, "Delete", lambda: self._delete(row["id"], dialog)).pack(side="left")
        self._button(buttons, "Save item", save, primary=True).pack(side="right")
        self._button(buttons, "Cancel", dialog.destroy).pack(side="right", padx=8)

    def _delete(self, item_id, parent):
        if messagebox.askyesno("Delete item", "Remove this item from your inventory? It will be removed from paired devices on their next sync.", parent=parent):
            self.store.delete(item_id)
            parent.destroy()
            self.refresh()

    def _context_menu(self, event):
        iid = self.table.identify_row(event.y)
        if not iid:
            return
        self.table.selection_set(iid)
        menu = tk.Menu(self, tearoff=False)
        menu.add_command(label="View / edit", command=self.edit_selected)
        menu.add_command(label="Increase quantity", command=lambda: self._adjust(iid, 1))
        menu.add_command(label="Decrease quantity", command=lambda: self._adjust(iid, -1))
        menu.add_command(label="Delete", command=lambda: self._delete(iid, self))
        menu.tk_popup(event.x_root, event.y_root)

    def _adjust(self, item_id, delta):
        self.store.adjust_quantity(item_id, delta)
        self.refresh()

    def open_sync(self):
        dialog = tk.Toplevel(self)
        dialog.title("LAN sync settings")
        dialog.configure(bg=self.PANEL)
        dialog.transient(self)
        dialog.grab_set()
        dialog.resizable(False, False)
        dialog.geometry("560x520")
        tk.Label(dialog, text="Connect your Android app", bg=self.PANEL, fg=self.INK, font=("Segoe UI", 18, "bold")).pack(anchor="w", padx=24, pady=(22, 4))
        tk.Label(dialog, text="Sync over Wi-Fi/Ethernet or connect the phone by USB.", bg=self.PANEL, fg=self.MUTED, font=("Segoe UI", 9)).pack(anchor="w", padx=24)
        address = tk.StringVar(value=self.server.address if self.server.server else "Server stopped")
        token = tk.StringVar(value=self.store.setting("token"))
        tk.Label(dialog, text="Desktop address", bg=self.PANEL, fg=self.MUTED, font=("Segoe UI", 9, "bold")).pack(anchor="w", padx=24, pady=(20, 4))
        tk.Entry(dialog, textvariable=address, state="readonly", width=42, font=("Consolas", 11)).pack(padx=24, ipady=5)
        tk.Label(dialog, text="Pairing token", bg=self.PANEL, fg=self.MUTED, font=("Segoe UI", 9, "bold")).pack(anchor="w", padx=24, pady=(14, 4))
        tk.Entry(dialog, textvariable=token, state="readonly", width=42, font=("Consolas", 10), show="•").pack(padx=24, ipady=5)
        reveal = tk.BooleanVar(value=False)
        token_entry = dialog.winfo_children()[-1]
        tk.Checkbutton(dialog, text="Show token", variable=reveal, command=lambda: token_entry.configure(show="" if reveal.get() else "•"), bg=self.PANEL).pack(anchor="w", padx=22)
        status = tk.Label(dialog, text="Sync server is running." if self.server.server else "Sync server is stopped.", bg=self.PANEL, fg=self.MUTED, font=("Segoe UI", 9))
        status.pack(anchor="w", padx=24, pady=(12, 0))

        def start():
            try:
                address.set(self.server.start())
                status.configure(text="Sync server is running. Use Firewall access below to configure Windows Firewall.", fg=self.GREEN)
                self.status.configure(text=f"LAN sync listening at {address.get()}")
            except OSError as error:
                messagebox.showerror("Couldn't start sync", str(error), parent=dialog)

        def rotate_token():
            new_token = secrets.token_urlsafe(24)
            self.store.set_setting("token", new_token)
            token.set(new_token)
            messagebox.showinfo("Pairing token changed", "Use the new token when pairing your Android app.", parent=dialog)

        def show_pairing_qr(pair_address=None, connection_note=None):
            if not self.server.server:
                messagebox.showwarning("Start sync first", "Start the LAN sync server before pairing.", parent=dialog)
                return
            pair_address = pair_address or address.get()
            payload = "inventorytracker://pair?" + urlencode({"address": pair_address, "token": token.get()})
            qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=1, border=0)
            qr.add_data(payload)
            qr.make(fit=True)
            matrix = qr.get_matrix()
            cell = max(4, min(7, 300 // len(matrix)))
            quiet = cell * 4
            qr_window = tk.Toplevel(dialog)
            qr_window.title("Pair Android app")
            qr_window.configure(bg=self.PANEL)
            qr_window.transient(dialog)
            tk.Label(qr_window, text="Scan to pair", bg=self.PANEL, fg=self.INK, font=("Segoe UI", 15, "bold")).pack(padx=20, pady=(18, 4))
            note = connection_note or "Keep both devices on the same Wi-Fi or Ethernet network."
            tk.Label(qr_window, text=note, bg=self.PANEL, fg=self.MUTED, font=("Segoe UI", 9), wraplength=340).pack(padx=20)
            size = len(matrix) * cell + 2 * quiet
            canvas = tk.Canvas(qr_window, width=size, height=size, bg="white", highlightthickness=0)
            canvas.pack(padx=20, pady=14)
            for y, line in enumerate(matrix):
                for x, dark in enumerate(line):
                    if dark:
                        x0, y0 = quiet + x * cell, quiet + y * cell
                        canvas.create_rectangle(x0, y0, x0 + cell, y0 + cell, fill="#19332b", outline="")
            tk.Label(qr_window, text=pair_address, bg=self.PANEL, fg=self.MUTED, font=("Consolas", 10)).pack(padx=20, pady=(0, 16))

        def enable_usb_sync():
            if not self.server.server:
                try:
                    address.set(self.server.start())
                    status.configure(text="Sync server is running.", fg=self.GREEN)
                except OSError as error:
                    messagebox.showerror("Couldn't start sync", str(error), parent=dialog)
                    return
            adb = find_adb_executable()
            if not adb:
                messagebox.showinfo(
                    "Android USB tools not found",
                    "Install Android SDK Platform-Tools (ADB), or add adb to PATH. Android Studio usually includes it. "
                    "Then connect the phone with a data-capable USB cable, enable USB debugging, and approve this PC on the phone.",
                    parent=dialog,
                )
                return
            try:
                result = subprocess.run(
                    [adb, "devices"], capture_output=True, text=True, timeout=15,
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
                )
            except (OSError, subprocess.TimeoutExpired) as error:
                messagebox.showerror("ADB unavailable", str(error), parent=dialog)
                return
            devices = [line.split()[0] for line in result.stdout.splitlines() if len(line.split()) >= 2 and line.split()[1] == "device"]
            if result.returncode != 0 or len(devices) != 1:
                messagebox.showinfo(
                    "Connect one authorized phone",
                    "ADB must show exactly one device with status 'device'. Check the USB cable, enable USB debugging, "
                    "and approve the computer's RSA prompt on the phone. Disconnect extra Android devices and retry.\n\n"
                    + (result.stdout.strip() or result.stderr.strip() or "No authorized device was found."),
                    parent=dialog,
                )
                return
            port = self.server.server.server_port
            try:
                result = subprocess.run(
                    [adb, "-s", devices[0], "reverse", f"tcp:{port}", f"tcp:{port}"],
                    capture_output=True, text=True, timeout=15,
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
                )
            except (OSError, subprocess.TimeoutExpired) as error:
                messagebox.showerror("USB tunnel failed", str(error), parent=dialog)
                return
            if result.returncode != 0:
                messagebox.showerror("USB tunnel failed", result.stderr.strip() or result.stdout.strip(), parent=dialog)
                return
            status.configure(text="USB sync tunnel is ready. Keep the phone connected by USB.", fg=self.GREEN)
            self.status.configure(text="USB sync tunnel ready")
            show_pairing_qr(
                f"127.0.0.1:{port}",
                "Keep the phone connected by USB. Scan this QR in the Android app.",
            )

        controls = tk.Frame(dialog, bg=self.PANEL, padx=24, pady=20)
        controls.pack(fill="x")
        self._button(controls, "Start server", start, primary=True).pack(side="left")
        self._button(controls, "Show pairing QR", show_pairing_qr).pack(side="left", padx=8)
        self._button(controls, "Rotate token", rotate_token).pack(side="left", padx=8)
        controls_bottom = tk.Frame(dialog, bg=self.PANEL, padx=24, pady=(0, 20))
        controls_bottom.pack(fill="x")
        self._button(controls_bottom, "Firewall access", self.request_firewall_access).pack(side="left")
        self._button(controls_bottom, "Enable USB sync", enable_usb_sync).pack(side="left", padx=8)
        self._button(controls_bottom, "Close", dialog.destroy).pack(side="right")

    def request_firewall_access(self):
        if os.name != "nt":
            messagebox.showinfo("Windows Firewall", "This firewall setup is available on Windows only.", parent=self)
            return
        approved = messagebox.askyesno(
            "Allow local network sync?",
            "Allow Inventory Tracker to accept sync connections from devices on your local subnet?\n\n"
            "Windows may be set to block every incoming connection on Public networks, which overrides individual allow rules. "
            "To support sync when this Wi-Fi is classified as Public, setup will let Windows honor its Public-profile allow rules "
            "while keeping unmatched inbound traffic blocked. This affects other explicitly allowed apps on Public networks too.\n\n"
            "Inventory Tracker's rule remains limited to TCP port 8765 and your local subnet. Use this only on a network you trust. "
            "Windows will ask for administrator approval. No router rule is added.",
            parent=self,
        )
        if not approved:
            self.status.configure(text="Firewall permission was not added. You can retry from LAN sync settings.")
            return

        script = (
            "$ErrorActionPreference='Stop'; "
            "try { "
            "$identity=[Security.Principal.WindowsIdentity]::GetCurrent(); "
            "$p=New-Object -TypeName Security.Principal.WindowsPrincipal -ArgumentList $identity; "
            "if(-not $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){ "
            "throw 'PowerShell did not start with administrator rights.' }; "
            "Set-NetFirewallProfile -Profile Public -AllowInboundRules True -DefaultInboundAction Block; "
            "$r=Get-NetFirewallRule -DisplayName 'Inventory Tracker LAN Sync' -ErrorAction SilentlyContinue; "
            "if($r){$r|Remove-NetFirewallRule}; "
            "New-NetFirewallRule -DisplayName 'Inventory Tracker LAN Sync' -Direction Inbound "
            "-Action Allow -Protocol TCP -LocalPort 8765 -Profile Private,Public -RemoteAddress LocalSubnet | Out-Null; "
            "Write-Host 'SUCCESS: Public profile honors allow rules; unmatched inbound traffic remains blocked.' -ForegroundColor Green; "
            "Write-Host 'SUCCESS: private/public local-subnet sync rule created.' -ForegroundColor Green; "
            "Get-NetFirewallProfile -PolicyStore ActiveStore -Name Public | "
            "Format-List Name,Enabled,DefaultInboundAction,AllowInboundRules,AllowLocalFirewallRules; "
            "Get-NetFirewallRule -DisplayName 'Inventory Tracker LAN Sync' | "
            "Format-List Enabled,Profile,Direction,Action; "
            "Get-NetFirewallRule -DisplayName 'Inventory Tracker LAN Sync' | "
            "Get-NetFirewallPortFilter | Format-List Protocol,LocalPort,RemotePort "
            "} catch { Write-Host ('FAILED: ' + $_.Exception.Message) -ForegroundColor Red }; "
            "Read-Host 'Press Enter to close this window'"
        )
        encoded = base64.b64encode(script.encode("utf-16le")).decode("ascii")
        shell_execute = ctypes.WinDLL("shell32", use_last_error=True).ShellExecuteW
        shell_execute.argtypes = [ctypes.c_void_p, ctypes.c_wchar_p, ctypes.c_wchar_p, ctypes.c_wchar_p, ctypes.c_wchar_p, ctypes.c_int]
        shell_execute.restype = ctypes.c_void_p
        result = shell_execute(
            None,
            "runas",
            "powershell.exe",
            f"-NoProfile -ExecutionPolicy Bypass -NoExit -EncodedCommand {encoded}",
            None,
            1,
        )
        if result and result > 32:
            self.status.configure(text="Firewall setup opened. Check the PowerShell window for its result.")
        else:
            self.status.configure(text="Could not open the Windows Firewall approval prompt.")
            messagebox.showerror(
                "Firewall setup unavailable",
                "Windows did not open the elevated PowerShell window. Check your User Account Control settings, "
                "then retry from LAN sync settings.",
                parent=self,
            )

    def open_settings(self):
        dialog = tk.Toplevel(self)
        dialog.title("Settings")
        dialog.configure(bg=self.PANEL)
        dialog.transient(self)
        dialog.grab_set()
        dialog.resizable(False, False)
        tk.Label(dialog, text="Settings", bg=self.PANEL, fg=self.INK, font=("Segoe UI", 18, "bold")).pack(anchor="w", padx=24, pady=(22, 5))
        tk.Label(dialog, text="Manage inventory and database sync.", bg=self.PANEL, fg=self.MUTED, font=("Segoe UI", 10)).pack(anchor="w", padx=24)

        sync_frame = tk.Frame(dialog, bg="#f4f7f3", padx=16, pady=14)
        sync_frame.pack(fill="x", padx=24, pady=(20, 10))
        tk.Label(sync_frame, text="Phone sync connection", bg="#f4f7f3", fg=self.INK, font=("Segoe UI", 11, "bold")).pack(anchor="w")
        tk.Label(sync_frame, text="Configure pairing, Wi-Fi or USB connection, and firewall access.", bg="#f4f7f3", fg=self.MUTED, font=("Segoe UI", 9), wraplength=390, justify="left").pack(anchor="w", pady=(5, 10))

        def open_lan_sync_settings():
            dialog.destroy()
            self.open_sync()

        self._button(sync_frame, "LAN sync settings", open_lan_sync_settings).pack(anchor="w")

        update_frame = tk.Frame(dialog, bg="#f4f7f3", padx=16, pady=14)
        update_frame.pack(fill="x", padx=24, pady=10)
        tk.Label(update_frame, text="Software updates", bg="#f4f7f3", fg=self.INK, font=("Segoe UI", 11, "bold")).pack(anchor="w")
        tk.Label(update_frame, text="Checks the private GitHub Releases feed. Updates are downloaded only after you approve the release.", bg="#f4f7f3", fg=self.MUTED, font=("Segoe UI", 9), wraplength=390, justify="left").pack(anchor="w", pady=(5, 8))
        token_var = tk.StringVar(value="")
        token_entry = tk.Entry(update_frame, textvariable=token_var, show="•", width=48, relief="flat")
        token_entry.pack(fill="x", ipady=7, pady=(0, 5))
        tk.Label(update_frame, text="Paste a fine-grained GitHub token (Contents: read only). Stored encrypted for this Windows account; a blank field keeps the saved token.", bg="#f4f7f3", fg=self.MUTED, font=("Segoe UI", 8), wraplength=390, justify="left").pack(anchor="w", pady=(0, 8))

        def save_update_token():
            try:
                if not token_var.get().strip():
                    messagebox.showinfo("No new token entered", "The existing saved token was kept. Paste a token to replace it, or use Clear token to remove it.", parent=dialog)
                    return
                _save_github_token(token_var.get().strip())
                token_var.set("")
                messagebox.showinfo("Update access saved", "The GitHub token was saved securely for this Windows account.", parent=dialog)
            except Exception as error:
                messagebox.showerror("Could not save token", str(error), parent=dialog)

        def clear_update_token():
            try:
                _save_github_token("")
                token_var.set("")
                messagebox.showinfo("Update access cleared", "The saved GitHub token was removed from this Windows account.", parent=dialog)
            except Exception as error:
                messagebox.showerror("Could not clear token", str(error), parent=dialog)

        update_buttons = tk.Frame(update_frame, bg="#f4f7f3")
        update_buttons.pack(anchor="w")
        self._button(update_buttons, "Save token", save_update_token).pack(side="left", padx=(0, 8))
        self._button(update_buttons, "Check for updates", lambda: (save_update_token() if token_var.get().strip() else None, self._check_desktop_update(manual=True))).pack(side="left")
        self._button(update_buttons, "Clear token", clear_update_token).pack(side="left", padx=(8, 0))

        clear_frame = tk.Frame(dialog, bg="#fbf1ef", padx=16, pady=14)
        clear_frame.pack(fill="x", padx=24, pady=10)
        tk.Label(clear_frame, text="Clear inventory", bg="#fbf1ef", fg=self.INK, font=("Segoe UI", 11, "bold")).pack(anchor="w")
        tk.Label(clear_frame, text="Remove all items here and send deletion records to the phone on its next sync.", bg="#fbf1ef", fg=self.MUTED, font=("Segoe UI", 9), wraplength=390, justify="left").pack(anchor="w", pady=(5, 10))

        def clear_inventory():
            if not messagebox.askyesno("Clear inventory?", "This removes every desktop inventory item. Paired phones will also remove them on their next sync. Continue?", parent=dialog):
                return
            count = self.store.clear_inventory()
            self.refresh()
            self.status.configure(text=f"Cleared {count} items; deletion sync is queued")
            messagebox.showinfo("Inventory cleared", f"Removed {count} items. The Android app will receive these deletions on its next sync.", parent=dialog)

        self._button(clear_frame, "Clear all inventory", clear_inventory).pack(anchor="w")
        self._button(dialog, "Done", dialog.destroy).pack(anchor="e", padx=24, pady=(8, 20))

    def _check_desktop_update_on_launch(self):
        try:
            if _read_github_token():
                self._check_desktop_update(manual=False)
        except Exception:
            return

    def _check_desktop_update(self, manual: bool):
        token = ""
        try:
            token = _read_github_token()
        except Exception as error:
            if manual:
                messagebox.showerror("Update access unavailable", str(error), parent=self)
            return

        def worker():
            try:
                release = _github_latest_release(token)
                tag = str(release.get("tag_name", ""))
                available = _version_tuple(tag) > _version_tuple(DESKTOP_VERSION)
                self.after(0, lambda: self._show_desktop_update(release) if available else (
                    messagebox.showinfo("No updates", f"Inventory Tracker {DESKTOP_VERSION} is up to date.", parent=self) if manual else None
                ))
            except Exception as error:
                if manual:
                    self.after(0, lambda: messagebox.showerror("Update check failed", f"Could not check the private GitHub release. Confirm the token and repository access.\n\n{error}", parent=self))
        threading.Thread(target=worker, daemon=True).start()

    def _show_desktop_update(self, release: dict):
        tag = str(release.get("tag_name", ""))
        notes = str(release.get("body", "")).strip() or "No release notes were provided."
        notes = notes[:2500]
        if not messagebox.askyesno("Inventory Tracker update available", f"Version {tag} is available (current version {DESKTOP_VERSION}).\n\nRelease notes:\n{notes}\n\nDownload and install this update?", parent=self):
            return
        assets = release.get("assets") or []
        asset = next((item for item in assets if str(item.get("name", "")).lower() == "inventorytracker.exe"), None)
        if not asset:
            messagebox.showerror("Update package missing", "This GitHub release must include an asset named InventoryTracker.exe.", parent=self)
            return
        if not getattr(sys, "frozen", False):
            messagebox.showinfo("Update available", "The update was approved. Run the packaged InventoryTracker.exe to install the desktop update.", parent=self)
            return
        self.status.configure(text=f"Downloading Inventory Tracker {tag}…")
        threading.Thread(target=self._download_and_apply_desktop_update, args=(asset, _read_github_token()), daemon=True).start()

    def _download_and_apply_desktop_update(self, asset: dict, token: str):
        try:
            request = urllib.request.Request(asset.get("url", ""), headers={
                "Accept": "application/octet-stream", "Authorization": f"Bearer {token}",
                "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "InventoryTracker",
            })
            with urllib.request.urlopen(request, timeout=90) as response:
                payload = response.read()
            if not payload.startswith(b"MZ"):
                raise ValueError("GitHub did not return a Windows executable.")
            expected_digest = str(asset.get("digest") or "")
            if expected_digest.startswith("sha256:") and hashlib.sha256(payload).hexdigest().lower() != expected_digest[7:].lower():
                raise ValueError("The downloaded executable failed GitHub's SHA-256 release digest check.")
            install_path = Path(sys.executable).resolve()
            download_path = Path(tempfile.gettempdir()) / f"InventoryTracker-{uuid.uuid4().hex}.exe"
            download_path.write_bytes(payload)
            script_path = Path(tempfile.gettempdir()) / f"InventoryTracker-update-{uuid.uuid4().hex}.ps1"
            script = (
                "$ErrorActionPreference='Stop'\n"
                f"$pidToWait={os.getpid()}\n"
                f"$source={json.dumps(str(download_path))}\n"
                f"$target={json.dumps(str(install_path))}\n"
                "try { Wait-Process -Id $pidToWait -Timeout 60 -ErrorAction SilentlyContinue } catch {}\n"
                "Start-Sleep -Milliseconds 500\n"
                "Copy-Item -LiteralPath $source -Destination $target -Force\n"
                "Start-Process -FilePath $target\n"
                "Remove-Item -LiteralPath $source -Force -ErrorAction SilentlyContinue\n"
                "Remove-Item -LiteralPath $PSCommandPath -Force -ErrorAction SilentlyContinue\n"
            )
            script_path.write_text(script, encoding="utf-8")
            flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
            subprocess.Popen(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script_path)], creationflags=flags)
            self.after(0, self._close)
        except Exception as error:
            self.after(0, lambda: messagebox.showerror("Update failed", f"Could not download or install the desktop update.\n\n{error}", parent=self))

    def force_sync_database(self):
        count = self.store.queue_full_sync()
        self.server.request_phone_sync()
        self.status.configure(text=f"Forced sync queued ({count} records). Keep the paired Android app open for immediate transfer.")

    def _close(self):
        self.server.stop()
        self.destroy()


if __name__ == "__main__":
    InventoryApp().mainloop()

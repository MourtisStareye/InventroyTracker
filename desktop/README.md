# Inventory Tracker desktop companion

This is a lightweight Windows desktop companion for the Android Inventory Tracker. It provides local inventory management and the authenticated LAN sync endpoint described in [`../LAN_SYNC_PROTOCOL.md`](../LAN_SYNC_PROTOCOL.md).

## Run

Install Python 3.10 or later with **Tcl/Tk** enabled, then run:

```powershell
py -3 desktop\inventory_desktop.py
```

The app uses Python's standard library plus the small `qrcode` package for pairing. Install the QR package before running the source directly with `py -3 -m pip install qrcode`. Its SQLite database is stored at `%LOCALAPPDATA%\InventoryTracker\inventory.sqlite3`.

In Add/Edit Item, choose a photo from your computer to copy it into `%LOCALAPPDATA%\InventoryTracker\images`. The filename (without its extension) fills the item name and can still be edited before saving. Photos selected on Android are also transferred over the paired LAN sync connection and stored in `%LOCALAPPDATA%\InventoryTracker\photos`; use **Open** in the item's Local photo row to view one.

## Build a Windows executable

Double-click `package.bat` and choose **Build and install desktop app**. It installs PyInstaller if needed, builds a standalone `InventoryTracker.exe`, installs it under `%LOCALAPPDATA%\Programs\InventoryTracker`, and creates an **Inventory Tracker** desktop shortcut with the app icon. The same menu offers the uninstall option. Uninstall keeps the inventory database unless you explicitly choose to remove it.

## Pair Android

1. Start the desktop app; the LAN sync server starts with it. Click **LAN sync** → **Show pairing QR**.
2. In Android, open **Settings** → **LAN sync settings** → **Scan desktop pairing QR** and scan the code. You can also enter the displayed address and token manually, then choose **Save connection**.
3. If Windows blocks LAN connections, open **Settings** → **LAN sync settings** → **Firewall access**. After you approve the prompt, it adds a rule for TCP port `8765` on **Private** and **Public** profiles, limited to the **local subnet**. It also configures the Public profile to honor explicit allow rules while leaving its default inbound action set to **Block**. This affects other explicit allow rules on Public networks too, so approve it only if you understand and trust the network. Windows then requests administrator approval; the elevated PowerShell window displays the result.
4. Keep both devices on the same Wi-Fi or Ethernet network. No cloud relay or router port forwarding is used.

### Sync over USB

1. Install Android SDK Platform-Tools (ADB); Android Studio usually includes it. Connect the phone using a data-capable USB cable.
2. On Android, enable **Developer options** and **USB debugging**, then approve the computer's USB debugging prompt.
3. In the desktop app, open **LAN sync** → **Enable USB sync**. It detects one authorized phone and creates an ADB reverse tunnel to the desktop server.
4. Scan the displayed QR code from **Settings** → **LAN sync settings** → **Scan desktop pairing QR** on Android. Keep the cable connected while syncing. USB sync does not need the Windows LAN firewall rule or Wi-Fi; unplugging the phone ends the tunnel.

On the main inventory screen, **Sync** queues a forced full database sync and requests an immediate sync from the paired phone. Keep the Android app open in the foreground for immediate transfer; on Wi-Fi, the app must have local network access. If it is closed or unreachable, the queued database is sent the next time the phone syncs manually or on its schedule. Pairing, server, USB, and firewall controls are in **Settings** → **LAN sync settings**.

The desktop server binds to the active private IPv4 address (and falls back to loopback if no private route is available), listens on port `8765`, and authenticates sync and photo transfer requests with the pairing token. Phone photos are stored in `%LOCALAPPDATA%\\InventoryTracker\\photos` by record ID. Use **Rotate token** to revoke the previous pairing token.

The desktop app must remain open for phone syncs to reach it. In **Settings**, **Force sync database** queues all desktop records for the phone's next sync. **Clear all inventory** records deletions for all current items so paired phones remove them on their next sync.

## Desktop features

- Search by name, barcode, brand, category, or location; filter by category.
- Add, edit, delete, and adjust quantities for inventory items.
- Upload local inventory photos, with the filename used to prefill the item name.
- Persist items and sync state locally in SQLite.
- Exchange versioned records and deletion tombstones using the Android sync JSON contract.

The desktop app does not access a camera or perform online barcode catalog lookups; use the Android app for those workflows.

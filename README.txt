Inventory Tracker - Quick Setup

CONTENTS
- InventoryTracker.exe: Windows desktop application
- app-debug.apk: Android application

DESKTOP SETUP
1. Extract the ZIP to a folder on your PC.
2. Run InventoryTracker.exe.
3. Your desktop inventory database is stored separately in:
   %LOCALAPPDATA%\InventoryTracker\inventory.sqlite3

INSTALL THE APK ON ANDROID
1. Extract the ZIP on your PC.
2. Connect your Android phone to the PC with a USB data cable. On the phone, select File transfer (MTP) if asked.
3. Copy app-debug.apk from the extracted InventoryTracker folder to the phone's Download folder.
4. On the phone, open Files (or your file manager), open Downloads, and tap app-debug.apk.
5. If Android asks, allow this file manager to install unknown apps. Return to the installer and tap Install or Update, then Open.
   Android menu names vary. If needed, search Settings for "Install unknown apps" and allow it for the app you used to open the APK.
6. Keep the existing app installed when updating so its local inventory data is preserved.

If Android reports that the app cannot be installed because of a package or signature conflict, stop and ask for help before uninstalling the existing app. Uninstalling can erase inventory data stored on the phone.

Alternative: In Android Studio, open this project, connect the phone with USB debugging enabled and the computer authorized, then press Run to build and install the app.

PAIR OVER WI-FI
1. Keep the desktop app open and connect both devices to the same Wi-Fi network.
2. On the desktop, open Settings > LAN sync settings and show the pairing QR.
3. On Android, open Settings > LAN sync settings, scan the desktop QR, and save the connection.
4. Return to the inventory screen and press Sync on Android.
5. If Windows blocks the connection, use Firewall access under the desktop LAN sync settings.

PAIR OVER USB
1. Connect the phone with a data-capable USB cable.
2. On Android, enable Developer options and USB debugging, then approve this computer.
3. On the desktop, open Settings > LAN sync settings and press Enable USB sync.
4. Scan the displayed QR from Android Settings > LAN sync settings.
5. Keep the USB cable connected. Return to the inventory screen and press Sync on Android.

SYNC BUTTONS
- Android Sync performs a database sync with the desktop.
- Desktop Sync queues the full desktop database and requests an immediate sync when the paired Android app is open in the foreground. If the phone is closed or unreachable, it receives the queued data at its next manual or scheduled sync.
- The desktop app must be running for a sync to complete.

SOFTWARE UPDATES FROM GITHUB
The desktop and Android apps check the public repository's latest published GitHub Release when they start. No token or sign-in is required. They show the version and release notes and wait for your approval before downloading. Android then opens Android's installer, which asks you to approve installation. Desktop closes, replaces its installed EXE, and reopens.

Version 1.1.3 adds explicit sync actions: use Sync to phone in the desktop app, or Sync to desktop in the Android app. Both use the paired connection and exchange pending inventory updates. The desktop also has a Refresh button, and the mobile inventory list supports pull-to-refresh.

1. Confirm MourtisStareye/InventroyTracker and its Releases are public. Anyone can download release assets from this public repository.
2. For each release, update desktop/inventory_desktop.py's DESKTOP_VERSION and app/build.gradle.kts's versionName and versionCode. Build the desktop EXE and Android APK, then publish a GitHub Release with a higher version tag, release notes, and both assets named InventoryTracker.exe and app-debug.apk. The APK must use the same Android signing key as the installed app.
3. The apps check the latest release on launch and offer a newer version for approval. You can also select Check for updates under Settings > Software updates.
4. On Android, if prompted, allow Inventory Tracker to install unknown apps, return to Inventory Tracker, and check for the update again. Android will show its final install/update confirmation.

The updater looks for published Releases, not ordinary commits. If the check returns 404, verify the repository name and publish a release with both assets. If you want to keep the source private, use a separate public releases-only repository and configure both apps to use it.


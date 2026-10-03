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
The desktop and Android apps check the private repository's latest published GitHub Release when they start, after update access is configured. They show the version and release notes and wait for your approval before downloading. Android then opens Android's installer, which asks you to approve installation. Desktop closes, replaces its installed EXE, and reopens.

1. Create a fine-grained personal access token in GitHub, limited to the InventoryTracker repository and the Contents permission set to Read-only. Do not grant write access. Keep the token private.
2. In the desktop app, open Settings > Software updates, paste the token, then press Save token. In the Android app, open Settings > Software updates, paste the same token, then press Save access. Each app encrypts its own copy locally; the token is not included in inventory sync or this ZIP.
3. For each release, update desktop/inventory_desktop.py's DESKTOP_VERSION and app/build.gradle.kts's versionName and versionCode. Build the desktop EXE and Android APK, then publish a GitHub Release in MourtisStareye/InventroyTracker with a matching version tag (for example v1.2.0), release notes, and both assets: InventoryTracker.exe and app-debug.apk. The APK must be built and signed with the same Android signing key as the installed app.
4. On the next launch, each app checks the latest release and offers it for approval. You can also select Check for updates in the app's Software updates settings.
5. On Android, if prompted, allow Inventory Tracker to install unknown apps, return to Inventory Tracker, and check for the update again. Android will show its final install/update confirmation.

Private repository updates require network access and a valid, unexpired token with read-only access. If GitHub reports an authorization error, renew the token and save it in both apps. Do not paste a token into release notes, source code, or a shared ZIP.


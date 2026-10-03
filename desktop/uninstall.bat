@echo off
setlocal
set "INSTALL_DIR=%LOCALAPPDATA%\Programs\InventoryTracker"
set "SHORTCUT=%USERPROFILE%\Desktop\Inventory Tracker.lnk"
set "DATA_DIR=%LOCALAPPDATA%\InventoryTracker"

echo Removing the Inventory Tracker desktop application...
echo Close Inventory Tracker first if it is currently running.
if exist "%SHORTCUT%" del /q "%SHORTCUT%"
if exist "%INSTALL_DIR%\InventoryTracker.exe" del /q "%INSTALL_DIR%\InventoryTracker.exe"
if exist "%INSTALL_DIR%" rmdir "%INSTALL_DIR%" 2>nul

if exist "%DATA_DIR%\inventory.sqlite3" (
  echo.
  choice /c YN /n /m "Also permanently delete inventory data and pairing settings? [Y/N] "
  if errorlevel 2 goto keepdata
  if errorlevel 1 goto deletedata
)
goto done

:deletedata
if exist "%DATA_DIR%\inventory.sqlite3" del /q "%DATA_DIR%\inventory.sqlite3"
if exist "%DATA_DIR%\inventory.sqlite3-wal" del /q "%DATA_DIR%\inventory.sqlite3-wal"
if exist "%DATA_DIR%\inventory.sqlite3-shm" del /q "%DATA_DIR%\inventory.sqlite3-shm"
if exist "%DATA_DIR%\images" rmdir /s /q "%DATA_DIR%\images"
if exist "%DATA_DIR%" rmdir "%DATA_DIR%" 2>nul
echo Inventory data and pairing settings were deleted.
goto done

:keepdata
echo Inventory data and pairing settings were kept at "%DATA_DIR%".

:done
echo.
echo Uninstall complete.
pause

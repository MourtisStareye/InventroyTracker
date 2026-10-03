@echo off
setlocal
cd /d "%~dp0"
title Inventory Tracker Desktop Packager

:menu
cls
echo ==========================================
echo   Inventory Tracker Desktop Packager
echo ==========================================
echo.
echo   1. Build and install desktop app
echo   2. Uninstall desktop app
echo   3. Exit
echo.
choice /c 123 /n /m "Choose an option: "
if errorlevel 3 goto :eof
if errorlevel 2 (
  call "%~dp0uninstall.bat"
  goto menu
)
if errorlevel 1 goto build

:build
echo.
where py >nul 2>nul
if errorlevel 1 (
  echo Python Launcher was not found. Install Python 3.10+ with Tcl/Tk and try again.
  pause
  goto menu
)
py -3 -c "import sys; raise SystemExit(0 if sys.version_info >= (3,10) else 1)"
if errorlevel 1 (
  echo Python 3.10 or later is required.
  pause
  goto menu
)

py -3 -c "import PyInstaller" >nul 2>nul
if errorlevel 1 (
  echo Installing PyInstaller for the current user...
  py -3 -m pip install --user pyinstaller
  if errorlevel 1 (
    echo PyInstaller installation failed. Check your Python and network setup.
    pause
    goto menu
  )
)

py -3 -c "import qrcode" >nul 2>nul
if errorlevel 1 (
  echo Installing the QR code library...
  py -3 -m pip install --user qrcode
  if errorlevel 1 (
    echo QR code library installation failed.
    pause
    goto menu
  )
)

echo Creating application icon...
py -3 "%~dp0make_icon.py"
if errorlevel 1 (
  echo Icon generation failed.
  pause
  goto menu
)

echo Building InventoryTracker.exe...
py -3 -m PyInstaller --noconfirm --clean --onefile --windowed --name InventoryTracker --icon "%~dp0inventory.ico" --distpath "%~dp0dist" --workpath "%~dp0build\pyinstaller" --specpath "%~dp0build" "%~dp0inventory_desktop.py"
if errorlevel 1 (
  echo Build failed.
  pause
  goto menu
)

set "INSTALL_DIR=%LOCALAPPDATA%\Programs\InventoryTracker"
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
copy /y "%~dp0dist\InventoryTracker.exe" "%INSTALL_DIR%\InventoryTracker.exe" >nul
if errorlevel 1 (
  echo Could not copy the application to "%INSTALL_DIR%".
  pause
  goto menu
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$shell = New-Object -ComObject WScript.Shell; $link = $shell.CreateShortcut([Environment]::GetFolderPath('Desktop') + '\Inventory Tracker.lnk'); $link.TargetPath = '%INSTALL_DIR%\InventoryTracker.exe'; $link.WorkingDirectory = '%INSTALL_DIR%'; $link.IconLocation = '%INSTALL_DIR%\InventoryTracker.exe,0'; $link.Description = 'Inventory Tracker desktop companion'; $link.Save()"
if errorlevel 1 echo The app installed, but Windows could not create the desktop shortcut.
echo.
echo Installed: %INSTALL_DIR%\InventoryTracker.exe
echo Desktop shortcut: Inventory Tracker
echo Your inventory database is stored separately and is not affected by uninstall.
pause
goto menu

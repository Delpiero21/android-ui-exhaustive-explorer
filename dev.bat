@echo off
REM android-ui-exhaustive-explorer - dev launcher
REM Runs scripts\dev.ps1 with ExecutionPolicy Bypass.
REM Spawns 2-3 PowerShell windows: server (8000), web (5173), adb reverse.

cd /d "%~dp0"

powershell.exe -ExecutionPolicy Bypass -NoProfile -File "%~dp0scripts\dev.ps1" %*

echo.
echo (dev.bat done. Server windows are running in background.)
echo  Press Ctrl+C in each window to stop.
echo.
pause

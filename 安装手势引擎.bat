@echo off
setlocal
cd /d "%~dp0"

echo Installing the shared dual-hand gesture engine...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\setup_gesture_engine.ps1"
if errorlevel 1 (
    echo.
    echo Installation failed. Review the message above.
    pause
    exit /b 1
)

echo.
echo Gesture engine is ready.
pause

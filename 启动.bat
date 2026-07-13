@echo off
setlocal
cd /d "%~dp0"

echo Starting AI Gesture Game...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\start_game.ps1" 1>"%~dp0startup.log" 2>&1
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Startup failed. Details:
    type "%~dp0startup.log"
    echo.
    echo Error log: %~dp0startup.log
    pause
    exit /b %EXIT_CODE%
)

exit /b 0

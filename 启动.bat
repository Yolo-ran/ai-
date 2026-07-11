@echo off
chcp 65001 >nul
echo ========================================
echo [Step 1] Cleaning zombie Python processes...
echo ========================================
taskkill /F /IM pythonw.exe /T >nul 2>&1
taskkill /F /IM python.exe /T >nul 2>&1

setlocal
set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

echo.
echo ========================================
echo [Step 2] Starting JavaFX Game Lobby...
echo (First run may download Maven dependencies, please wait)
echo ========================================
set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NUL

if exist ali-settings.xml (call mvnw.cmd -s ali-settings.xml javafx:run) else (call mvnw.cmd javafx:run)

echo.
echo Game exited or error occurred.
pause

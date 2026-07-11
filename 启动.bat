@echo off
setlocal
set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"
set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NUL
echo [launcher] start JavaFX lobby (Python auto-launched by Java)
start "" /b mvnw.cmd javafx:run

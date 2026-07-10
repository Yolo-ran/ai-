@echo off
cd /d "%~dp0python"
start "" /b pythonw gesture_server.py 2>nul || start "" /b python gesture_server.py
cd /d "%~dp0"
start "" /b mvnw.cmd javafx:run

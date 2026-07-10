@echo off
echo Starting Python...
cd /d "%~dp0python"
start cmd /c "python gesture_server.py && pause"
echo Done! Now run Java from IDEA (Maven - javafx:run)
pause

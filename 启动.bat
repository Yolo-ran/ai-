@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "PYTHON_DIR=%ROOT_DIR%python"
set "PACKAGED_EXE=%PYTHON_DIR%\dist\gesture_server\gesture_server.exe"
set "VENV_PYTHONW=%ROOT_DIR%.venv\Scripts\pythonw.exe"
set "VENV_PYTHON=%ROOT_DIR%.venv\Scripts\python.exe"

if exist "%PACKAGED_EXE%" (
    echo [launcher] start packaged gesture_server.exe
    start "" /b "%PACKAGED_EXE%"
) else (
    cd /d "%PYTHON_DIR%"
    if exist "%VENV_PYTHONW%" (
        echo [launcher] start gesture_server.py with .venv pythonw
        start "" /b "%VENV_PYTHONW%" gesture_server.py
    ) else (
        if exist "%VENV_PYTHON%" (
            echo [launcher] start gesture_server.py with .venv python
            start "" /b "%VENV_PYTHON%" gesture_server.py
        ) else (
            echo [launcher] packaged exe not found, fallback to system python
            start "" /b pythonw gesture_server.py 2>nul || start "" /b python gesture_server.py
        )
    )
)

timeout /t 2 /nobreak >nul
cd /d "%ROOT_DIR%"
echo [launcher] start JavaFX lobby
start "" /b mvnw.cmd javafx:run

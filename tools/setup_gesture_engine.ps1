$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$venvDir = Join-Path $root ".venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"
$requirements = Join-Path $root "python\requirements.txt"
$model = Join-Path $root "python\models\hand_landmarker.task"

function Find-SystemPython {
    if ($env:LOCALAPPDATA) {
        foreach ($version in @("312", "311", "310", "313")) {
            $candidate = Join-Path $env:LOCALAPPDATA "Programs\Python\Python$version\python.exe"
            if (Test-Path $candidate) {
                return $candidate
            }
        }
    }

    foreach ($name in @("python.exe", "python")) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    throw "Python 3.10-3.13 was not found. Install Python 3.12 first."
}

if (-not (Test-Path $requirements)) {
    throw "Requirements file was not found: $requirements"
}

if (-not (Test-Path $model)) {
    throw "Dual-hand model was not found: $model. Pull the latest repository first."
}

if (-not (Test-Path $venvPython)) {
    $systemPython = Find-SystemPython
    Write-Host "[1/3] Creating the project Python environment..." -ForegroundColor Cyan
    & $systemPython -m venv $venvDir
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create the Python virtual environment."
    }
} else {
    Write-Host "[1/3] Project Python environment found." -ForegroundColor Green
}

Write-Host "[2/3] Installing the shared gesture dependencies..." -ForegroundColor Cyan
& $venvPython -m pip install --disable-pip-version-check --upgrade -r $requirements
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install the gesture dependencies."
}

Write-Host "[3/3] Verifying the dual-hand engine..." -ForegroundColor Cyan
$verifyCode = @'
import cv2
import mediapipe
import websockets
from pathlib import Path
import sys

model = Path(sys.argv[1])
print("MediaPipe", mediapipe.__version__)
print("OpenCV", cv2.__version__)
print("websockets", websockets.__version__)
print("Dual-hand model", model.name, model.stat().st_size, "bytes")
'@
& $venvPython -c $verifyCode $model
if ($LASTEXITCODE -ne 0) {
    throw "The dual-hand gesture engine verification failed."
}

Write-Host "Installation complete. Run startup.bat next." -ForegroundColor Green

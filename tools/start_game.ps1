$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$pythonDir = Join-Path $root "python"
$gestureScript = Join-Path $pythonDir "gesture_server.py"
$gestureProcess = $null
$logRoot = if ($env:LOCALAPPDATA) {
    Join-Path $env:LOCALAPPDATA "AIGestureGame\logs"
} else {
    Join-Path $root "logs"
}
$gestureStdOutLog = Join-Path $logRoot "gesture_engine.log"
$gestureStdErrLog = Join-Path $logRoot "gesture_engine.err.log"

function Find-PythonCommand {
    $packagedExe = Join-Path $pythonDir "dist\gesture_server\gesture_server.exe"
    if (Test-Path $packagedExe) {
        $packageTime = (Get-Item $packagedExe).LastWriteTimeUtc
        $sourceTime = (Get-Item $gestureScript).LastWriteTimeUtc
        if ($packageTime -ge $sourceTime) {
            return @{ File = $packagedExe; Arguments = @() }
        }
        Write-Host "[engine] Ignoring outdated packaged engine; using current source." -ForegroundColor Yellow
    }

    $venvPython = Join-Path $root ".venv\Scripts\python.exe"
    if (Test-Path $venvPython) {
        return @{ File = $venvPython; Arguments = @($gestureScript) }
    }

    $venvPythonW = Join-Path $root ".venv\Scripts\pythonw.exe"
    if (Test-Path $venvPythonW) {
        return @{ File = $venvPythonW; Arguments = @($gestureScript) }
    }

    if ($env:LOCALAPPDATA) {
        foreach ($version in @("312", "311", "313", "310")) {
            $candidate = Join-Path $env:LOCALAPPDATA "Programs\Python\Python$version\python.exe"
            if (Test-Path $candidate) {
                return @{ File = $candidate; Arguments = @($gestureScript) }
            }
            $candidateW = Join-Path $env:LOCALAPPDATA "Programs\Python\Python$version\pythonw.exe"
            if (Test-Path $candidateW) {
                return @{ File = $candidateW; Arguments = @($gestureScript) }
            }
        }
    }

    foreach ($name in @("python.exe", "pythonw.exe")) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) {
            return @{ File = $command.Source; Arguments = @($gestureScript) }
        }
    }

    throw "Python 3.10-3.13 was not found. Install Python or create the project .venv."
}

function Invoke-Game {
    $settings = Join-Path $root "ali-settings.xml"
    $arguments = @("-q")
    if (Test-Path $settings) {
        $arguments += @("-s", $settings)
    }
    $arguments += "javafx:run"

    $maven = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if (-not $maven) {
        $maven = Get-Command "mvn" -ErrorAction SilentlyContinue
    }

    if ($maven) {
        & $maven.Source @arguments
    } else {
        & (Join-Path $root "mvnw.cmd") @arguments
    }

    if ($LASTEXITCODE -ne 0) {
        throw "The Java game failed to start (exit code $LASTEXITCODE)."
    }
}

function Start-GestureEngine($python) {
    New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
    if (Test-Path $gestureStdOutLog) {
        Remove-Item $gestureStdOutLog -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path $gestureStdErrLog) {
        Remove-Item $gestureStdErrLog -Force -ErrorAction SilentlyContinue
    }

    $startArguments = @{
        FilePath = $python.File
        WorkingDirectory = $pythonDir
        PassThru = $true
        RedirectStandardOutput = $gestureStdOutLog
        RedirectStandardError = $gestureStdErrLog
    }
    if ($python.File -like "*.exe") {
        $startArguments.WindowStyle = "Hidden"
    }
    if ($python.Arguments.Count -gt 0) {
        $startArguments.ArgumentList = @($python.Arguments | ForEach-Object {
            if ($_ -match '\s') { '"{0}"' -f $_ } else { $_ }
        })
    }

    $process = Start-Process @startArguments
    Start-Sleep -Milliseconds 1800
    if ($process.HasExited) {
        $tail = @()
        if (Test-Path $gestureStdErrLog) {
            $tail += Get-Content $gestureStdErrLog -Tail 20 -ErrorAction SilentlyContinue
        }
        if ($tail.Count -eq 0 -and (Test-Path $gestureStdOutLog)) {
            $tail += Get-Content $gestureStdOutLog -Tail 20 -ErrorAction SilentlyContinue
        }
        $details = if ($tail.Count -gt 0) { ($tail -join [Environment]::NewLine) } else { "No Python engine log was captured." }
        throw "Gesture engine exited during warm-up. Log: $gestureStdErrLog`n$details"
    }
    Write-Host "[engine] Gesture engine is running. Logs: $gestureStdOutLog" -ForegroundColor Green
    return $process
}

try {
    Set-Location $root
    # Discard the broken trustStore=NUL option used by older launcher versions.
    $env:MAVEN_OPTS = ""
    $python = Find-PythonCommand

    Write-Host "[1/2] Warming up gesture recognition and camera..."
    $gestureProcess = Start-GestureEngine $python

    # Prevent Java from starting another engine that would compete for the camera.
    $env:GESTURE_ENGINE_EXTERNAL = "1"

    Write-Host "[2/2] Opening the game..."
    Invoke-Game
}
finally {
    if ($gestureProcess -and -not $gestureProcess.HasExited) {
        Stop-Process -Id $gestureProcess.Id -Force -ErrorAction SilentlyContinue
    }
}

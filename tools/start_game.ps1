$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$pythonDir = Join-Path $root "python"
$gestureScript = Join-Path $pythonDir "gesture_server.py"
$gestureProcess = $null

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

    $venvPython = Join-Path $root ".venv\Scripts\pythonw.exe"
    if (Test-Path $venvPython) {
        return @{ File = $venvPython; Arguments = @($gestureScript) }
    }

    if ($env:LOCALAPPDATA) {
        foreach ($version in @("312", "311", "313", "310")) {
            $candidate = Join-Path $env:LOCALAPPDATA "Programs\Python\Python$version\pythonw.exe"
            if (Test-Path $candidate) {
                return @{ File = $candidate; Arguments = @($gestureScript) }
            }
        }
    }

    foreach ($name in @("pythonw.exe", "python.exe")) {
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

try {
    Set-Location $root
    # Discard the broken trustStore=NUL option used by older launcher versions.
    $env:MAVEN_OPTS = ""
    $python = Find-PythonCommand

    Write-Host "[1/2] Warming up gesture recognition and camera..."
    $startArguments = @{
        FilePath = $python.File
        WorkingDirectory = $pythonDir
        WindowStyle = "Hidden"
        PassThru = $true
    }
    if ($python.Arguments.Count -gt 0) {
        $startArguments.ArgumentList = $python.Arguments
    }
    $gestureProcess = Start-Process @startArguments

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

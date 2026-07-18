$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$pythonDir = Join-Path $root "python"
$gestureScript = Join-Path $pythonDir "gesture_server.py"
$fastBuildScript = Join-Path $PSScriptRoot "prepare_fast_launch.ps1"
$appJar = Join-Path $root "target\gesture-game-hall-1.0-SNAPSHOT.jar"
$runtimeLib = Join-Path $root "target\runtime-lib"
$gestureProcess = $null
$logRoot = Join-Path $root "logs"
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

function Find-JavaCommand {
    if ($env:JAVA_HOME) {
        $javaHomeCommand = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaHomeCommand) {
            return $javaHomeCommand
        }
    }
    $java = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if (-not $java) {
        $java = Get-Command "java" -ErrorAction SilentlyContinue
    }
    if (-not $java) {
        throw "Java 17 or newer was not found."
    }
    return $java.Source
}

function Assert-JavaVersion($java) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $java
    $startInfo.Arguments = "-version"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $versionOutput = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
    $process.WaitForExit()
    $versionText = ($versionOutput -split "`r?`n" | Select-Object -First 1).ToString()
    if ($versionText -notmatch 'version\s+"(?<first>\d+)(?:\.(?<second>\d+))?') {
        throw "Unable to determine the Java version: $versionText"
    }
    $major = [int]$Matches.first
    if ($major -eq 1 -and $Matches.second) {
        $major = [int]$Matches.second
    }
    if ($major -lt 17) {
        throw "Java 17 or newer is required; current version is $major."
    }
}

function Invoke-Game {
    if (-not (Test-Path $appJar) -or -not (Test-Path $runtimeLib)) {
        throw "Fast-launch files are missing. Run tools\prepare_fast_launch.ps1 first."
    }
    $java = Find-JavaCommand
    Assert-JavaVersion $java
    $classPath = $appJar + [IO.Path]::PathSeparator + (Join-Path $runtimeLib "*")
    & $java "-Dfile.encoding=UTF-8" "-Dprism.order=d3d,sw" "-cp" $classPath "com.gesturegame.FastLauncher"
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
    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(12)
    while ([DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 120
        if ($process.HasExited) {
            break
        }
        foreach ($logFile in @($gestureStdErrLog, $gestureStdOutLog)) {
            if (Test-Path $logFile) {
                $logText = Get-Content -LiteralPath $logFile -Raw -ErrorAction SilentlyContinue
                if ($logText -match "GESTURE_ENGINE_READY") {
                    $ready = $true
                    break
                }
            }
        }
        if ($ready) {
            break
        }
    }
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
    if ($ready) {
        Write-Host "[engine] Camera and gesture model are ready." -ForegroundColor Green
    } else {
        Write-Host "[engine] Gesture engine is still warming up; Java will open while it finishes." -ForegroundColor Yellow
    }
    Write-Host "[engine] Logs: $gestureStdOutLog / $gestureStdErrLog"
    return $process
}

try {
    Set-Location $root
    Write-Host "[1/3] Checking fast-launch build..."
    & $fastBuildScript

    $python = Find-PythonCommand

    Write-Host "[2/3] Warming up gesture recognition and camera..."
    $gestureProcess = Start-GestureEngine $python

    # Prevent Java from starting another engine that would compete for the camera.
    $env:GESTURE_ENGINE_EXTERNAL = "1"

    Write-Host "[3/3] Opening the game directly (Maven is not used)..."
    Invoke-Game
}
finally {
    if ($gestureProcess -and -not $gestureProcess.HasExited) {
        Stop-Process -Id $gestureProcess.Id -Force -ErrorAction SilentlyContinue
    }
}

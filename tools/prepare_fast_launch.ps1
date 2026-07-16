param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $root "target"
$runtimeLib = Join-Path $targetDir "runtime-lib"
$appJar = Join-Path $targetDir "gesture-game-hall-1.0-SNAPSHOT.jar"
$stampDir = Join-Path $targetDir "fast-launch"
$stampFile = Join-Path $stampDir "fingerprint.txt"

function Get-SourceFingerprint {
    $sourceRoot = Join-Path $root "src\main"
    $files = @((Get-Item (Join-Path $root "pom.xml")))
    $files += @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File)

    $metadata = $files |
        Sort-Object FullName |
        ForEach-Object {
            $relative = $_.FullName.Substring($root.Length).TrimStart('\')
            "{0}|{1}|{2}" -f $relative, $_.Length, $_.LastWriteTimeUtc.Ticks
        }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($metadata -join "`n"))
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "")
    }
    finally {
        $sha.Dispose()
    }
}

function Find-MavenCommand {
    $maven = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if (-not $maven) {
        $maven = Get-Command "mvn" -ErrorAction SilentlyContinue
    }
    if ($maven) {
        return $maven.Source
    }

    $wrapper = Join-Path $root "mvnw.cmd"
    if (Test-Path $wrapper) {
        return $wrapper
    }
    throw "Maven was not found and mvnw.cmd is missing."
}

function Test-FastBuild($fingerprint) {
    if ($Force -or -not (Test-Path $appJar) -or -not (Test-Path $runtimeLib) -or -not (Test-Path $stampFile)) {
        return $false
    }
    $dependencyCount = @(Get-ChildItem -LiteralPath $runtimeLib -Filter "*.jar" -File -ErrorAction SilentlyContinue).Count
    if ($dependencyCount -lt 8) {
        return $false
    }
    return (Get-Content -LiteralPath $stampFile -Raw).Trim() -eq $fingerprint
}

$fingerprint = Get-SourceFingerprint
if (Test-FastBuild $fingerprint) {
    Write-Host "[build] Fast-launch files are up to date; Maven skipped." -ForegroundColor Green
    return
}

Write-Host "[build] Source changed or this is the first launch; building once..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

$resolvedTarget = [IO.Path]::GetFullPath($targetDir).TrimEnd('\') + '\'
$resolvedRuntime = [IO.Path]::GetFullPath($runtimeLib)
if (-not $resolvedRuntime.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean a runtime directory outside target: $resolvedRuntime"
}
if (Test-Path $runtimeLib) {
    Remove-Item -LiteralPath $runtimeLib -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $runtimeLib | Out-Null

$settings = Join-Path $root "ali-settings.xml"
$arguments = @("-q")
if (Test-Path $settings) {
    $arguments += @("-s", $settings)
}
$arguments += @(
    "-DskipTests",
    "package",
    "org.apache.maven.plugins:maven-dependency-plugin:3.7.1:copy-dependencies",
    "-DoutputDirectory=$runtimeLib",
    "-DincludeScope=runtime"
)

$maven = Find-MavenCommand
$originalMavenOpts = $env:MAVEN_OPTS
try {
    $safeOpts = if ($originalMavenOpts) {
        $originalMavenOpts -replace '-Djavax\.net\.ssl\.trustStore=NUL\s*', ''
    } else {
        ""
    }
    if ($env:OS -eq "Windows_NT" -and $safeOpts -notmatch 'trustStoreType=') {
        $safeOpts = ($safeOpts + " -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE").Trim()
    }
    $env:MAVEN_OPTS = $safeOpts
    & $maven @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "The one-time Java build failed (exit code $LASTEXITCODE)."
    }
}
finally {
    $env:MAVEN_OPTS = $originalMavenOpts
}

if (-not (Test-Path $appJar)) {
    throw "Build completed but the application jar was not created: $appJar"
}
$dependencyCount = @(Get-ChildItem -LiteralPath $runtimeLib -Filter "*.jar" -File).Count
if ($dependencyCount -lt 8) {
    throw "Build completed but runtime dependencies are incomplete: $runtimeLib"
}

New-Item -ItemType Directory -Force -Path $stampDir | Out-Null
[IO.File]::WriteAllText($stampFile, $fingerprint, [Text.Encoding]::UTF8)
Write-Host "[build] Fast-launch build is ready ($dependencyCount runtime jars)." -ForegroundColor Green

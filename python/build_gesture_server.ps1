$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonExe = Join-Path $projectRoot ".venv\Scripts\python.exe"
$scriptPath = Join-Path $PSScriptRoot "gesture_server.py"
$modelPath = Join-Path $PSScriptRoot "models\hand_landmarker.task"
$buildDir = Join-Path $PSScriptRoot "build"
$distDir = Join-Path $PSScriptRoot "dist"

if (-not (Test-Path $pythonExe)) {
    throw "未找到虚拟环境 Python: $pythonExe"
}

if (-not (Test-Path $scriptPath)) {
    throw "未找到打包入口脚本: $scriptPath"
}

Write-Host "[build] 安装/确认 PyInstaller..." -ForegroundColor Cyan
& $pythonExe -m pip install pyinstaller
if ($LASTEXITCODE -ne 0) {
    throw "PyInstaller 安装失败"
}

Write-Host "[build] 预下载 MediaPipe 模型..." -ForegroundColor Cyan
& $pythonExe -c "import sys; sys.path.insert(0, r'$PSScriptRoot'); import gesture_server; print(gesture_server.ensure_hand_landmarker_model())"
if ($LASTEXITCODE -ne 0) {
    throw "MediaPipe 模型准备失败"
}

if (-not (Test-Path $modelPath)) {
    throw "未找到模型文件: $modelPath"
}

Write-Host "[build] 开始生成 gesture_server.exe ..." -ForegroundColor Cyan
& $pythonExe -m PyInstaller `
    --noconfirm `
    --clean `
    --name gesture_server `
    --onedir `
    --console `
    --specpath $PSScriptRoot `
    --workpath $buildDir `
    --distpath $distDir `
    --collect-all mediapipe `
    --collect-all cv2 `
    --hidden-import websockets `
    --add-data "$modelPath;models" `
    $scriptPath

if ($LASTEXITCODE -ne 0) {
    throw "PyInstaller 打包失败"
}

Write-Host "[build] 打包完成: $(Join-Path $distDir 'gesture_server')" -ForegroundColor Green
Write-Host "[build] Distribute the whole gesture_server directory, not only the exe." -ForegroundColor Yellow

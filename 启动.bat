@echo off
chcp 65001 >nul
echo ========================================
echo [步骤 1] 正在清理残留的后台视觉进程...
echo ========================================
:: 启动前强制杀死所有后台 Python 进程，彻底解决多进程抢摄像头导致的闪烁问题
taskkill /F /IM pythonw.exe /T >nul 2>&1
taskkill /F /IM python.exe /T >nul 2>&1

setlocal
set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

echo.
echo ========================================
echo [步骤 2] 正在启动 JavaFX 游戏大厅...
echo (如果是首次运行，Maven 需要下载依赖，请耐心等待)
echo ========================================
set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NUL

:: 若有阿里云镜像配置则使用，否则使用默认 Maven 配置（兼容队友环境）
if exist ali-settings.xml (
    echo [info] 检测到 ali-settings.xml，使用阿里云镜像加速
    call mvnw.cmd -s ali-settings.xml javafx:run
) else (
    echo [info] 使用默认 Maven 配置
    call mvnw.cmd javafx:run
)

echo.
echo 游戏已退出或发生错误。
pause

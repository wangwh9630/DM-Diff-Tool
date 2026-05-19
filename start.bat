@echo off
setlocal

cd /d "%~dp0"

if not exist "dm-diff-tool.jar" (
    echo 错误：未找到 dm-diff-tool.jar 文件
    pause
    exit /b 1
)

echo 启动 DM-Diff 达梦数据库对比工具...
echo 配置文件路径: %cd%\application.yml
echo.

java -jar dm-diff-tool.jar

pause

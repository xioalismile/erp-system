@echo off
title Smart ERP 一键部署
cd /d "%~dp0"
echo ================================================
echo   Smart ERP - 一键部署到云端
echo ================================================
echo.
echo 正在启动 PowerShell 部署脚本...
echo.
powershell -ExecutionPolicy Bypass -File "%~dp0一键部署.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [错误] 脚本执行失败，退出码: %ERRORLEVEL%
    echo.
)
echo.
pause

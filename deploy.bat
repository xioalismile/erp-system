@echo off
title Smart ERP Deploy
cd /d "%~dp0"
echo ================================================
echo   Smart ERP - Deploy to Cloud
echo ================================================
echo.
echo Starting PowerShell deploy script...
echo.
powershell -ExecutionPolicy Bypass -File "%~dp0deploy.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Script failed, exit code: %ERRORLEVEL%
    echo.
)
echo.
pause

@echo off
title Smart ERP - Quick Start (Dev Mode)
color 0A

echo ================================================================
echo   Smart ERP Quick Start - Dev Mode (No Database Install Needed)
echo ================================================================
echo.
echo This mode uses H2 in-memory database, no PostgreSQL/Redis needed.
echo Data will be lost when you stop the program.
echo.
echo For production, use deploy.ps1 instead.
echo.
echo Press any key to start...
pause >nul

echo.
echo [1/3] Checking Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo   Java not found! Please install JDK 17 first.
    echo   Download: https://learn.microsoft.com/java/openjdk/
    pause
    exit /b 1
)
echo   Java OK.

echo.
echo [2/3] Starting Smart ERP (Dev Mode)...
echo   Database: H2 In-Memory (auto-created)
echo   Cache: In-Memory (no Redis needed)
echo   Port: 8080
echo.
echo   Browser will open automatically...
echo   Login: admin / admin123
echo.
echo   Press Ctrl+C to stop.
echo.

start /b cmd /c "timeout /t 8 /nobreak >nul && start http://localhost:8080"

java -jar smart-erp.jar --spring.profiles.active=dev

pause

@echo off
title GuardianVoice - Starting App
color 0A

echo ================================================
echo   GuardianVoice - One Click Launcher
echo ================================================
echo.
echo [1/3] Checking prerequisites...
echo.

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH.
    echo Please install Java 17 or higher from https://adoptium.net
    pause
    exit /b 1
)

where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Maven is not installed or not in PATH.
    echo Please install Maven from https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

echo Java and Maven found. Good to go!
echo (No MySQL needed - this uses a built-in H2 database automatically)
echo.
echo [2/3] Starting GuardianVoice backend server...
echo.

cd backend
start "GuardianVoice Backend" cmd /k mvn spring-boot:run

echo.
echo [3/3] Waiting for server to start, then opening browser...
timeout /t 25 /nobreak >nul

start http://localhost:8080/login.html

echo.
echo ================================================
echo  GuardianVoice is starting!
echo  If the browser page does not load, wait a few
echo  more seconds and refresh, or check the backend
echo  window for errors.
echo ================================================
echo.
pause

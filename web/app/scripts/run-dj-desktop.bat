@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
title DJ Desktop Studio Launcher

cd /d "%~dp0\.."
set "APP_DIR=%CD%"
set "SCRIPTS_DIR=%APP_DIR%\scripts"
set "PORT=3000"
set "APP_URL=http://127.0.0.1:%PORT%"
set "BROWSER_DATA=%LOCALAPPDATA%\DJ Desktop Studio\BrowserProfile"

if not exist "%LOCALAPPDATA%\DJ Desktop Studio" mkdir "%LOCALAPPDATA%\DJ Desktop Studio" >nul 2>nul

set "SERVER_RUNNING=0"
for /f "tokens=*" %%a in ('netstat -ano ^| findstr /R ":3000.*LISTENING"') do (
    set "SERVER_RUNNING=1"
)

if "!SERVER_RUNNING!"=="0" (
    where node.exe >nul 2>nul
    if !errorlevel! equ 0 (
        if exist "%SCRIPTS_DIR%\server.js" (
            start /b "" node "%SCRIPTS_DIR%\server.js" %PORT%
        )
    ) else (
        where python.exe >nul 2>nul
        if !errorlevel! equ 0 (
            if exist "%APP_DIR%\dist" (
                start /b "" python -m http.server %PORT% --directory "%APP_DIR%\dist"
            )
        ) else (
            if exist "%SCRIPTS_DIR%\server.ps1" (
                start /b "" powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPTS_DIR%\server.ps1" -Port %PORT%
            )
        )
    )
    ping 127.0.0.1 -n 2 >nul
)

:: 1. Prioritize Google Chrome Application Mode
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" --app="%APP_URL%" --user-data-dir="%BROWSER_DATA%" --no-first-run
    exit /b 0
)

if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" --app="%APP_URL%" --user-data-dir="%BROWSER_DATA%" --no-first-run
    exit /b 0
)

if exist "%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe" (
    start "" "%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe" --app="%APP_URL%" --user-data-dir="%BROWSER_DATA%" --no-first-run
    exit /b 0
)

:: 2. Prioritize Microsoft Edge Application Mode (Installed by default on all Windows 10 & 11)
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" --app="%APP_URL%" --user-data-dir="%BROWSER_DATA%" --no-first-run
    exit /b 0
)

if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" --app="%APP_URL%" --user-data-dir="%BROWSER_DATA%" --no-first-run
    exit /b 0
)

:: 3. Fall back to default browser
start "" "%APP_URL%"
exit /b 0

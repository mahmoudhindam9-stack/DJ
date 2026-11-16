@echo off
setlocal
cd /d "%~dp0"
title DJ Desktop Studio Setup

REM Check and launch PowerShell setup engine if available
set "SETUP_PS1=%~dp0app\scripts\setup.ps1"
if not exist "%SETUP_PS1%" set "SETUP_PS1=%~dp0scripts\setup.ps1"

where powershell.exe >nul 2>nul
if %errorlevel% equ 0 (
    if exist "%SETUP_PS1%" (
        powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SETUP_PS1%"
        if not errorlevel 1 exit /b 0
    )
)

REM Fallback when PowerShell is unavailable or had an issue
echo Starting DJ Desktop Studio setup...
set "PATH=%ProgramFiles%\nodejs;%LOCALAPPDATA%\Programs\nodejs;%APPDATA%\npm;%PATH%"

where node.exe >nul 2>nul
if errorlevel 1 (
    where winget.exe >nul 2>nul
    if not errorlevel 1 (
        echo Installing Node.js LTS via winget...
        winget install --id OpenJS.NodeJS.LTS --exact --silent --accept-source-agreements --accept-package-agreements
        set "PATH=%ProgramFiles%\nodejs;%LOCALAPPDATA%\Programs\nodejs;%APPDATA%\npm;%PATH%"
    )
)

where node.exe >nul 2>nul
if errorlevel 1 (
    echo.
    echo Node.js LTS is required to run DJ Desktop Studio.
    echo Please install Node.js from https://nodejs.org
    echo Then run setup.bat again.
    echo.
    pause
    exit /b 1
)

set "APP_DIR=%~dp0"
if exist "%~dp0app\package.json" set "APP_DIR=%~dp0app"

set "PATH=%APP_DIR%\node_modules\.bin;%~dp0node_modules\.bin;%PATH%"

if exist "%~dp0package.json" if not "%APP_DIR%"=="%~dp0" (
    echo Installing root dependencies...
    call npm install --include=dev --no-audit --no-fund
)

cd /d "%APP_DIR%"
echo Installing dependencies...
call npm install --include=dev --no-audit --no-fund
echo Building production assets...
call npm run build
if errorlevel 1 (
    echo Retrying build with npx vite build...
    call npx vite build
)

if exist "%~dp0app\scripts\run-dj-desktop.bat" (
    start "" "%~dp0app\scripts\run-dj-desktop.bat"
) else if exist "%~dp0scripts\run-dj-desktop.bat" (
    start "" "%~dp0scripts\run-dj-desktop.bat"
) else (
    start "" http://127.0.0.1:3000
)

echo Setup completed successfully.
timeout /t 3 >nul 2>nul
exit /b 0

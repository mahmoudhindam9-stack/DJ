@echo off
setlocal
cd /d "%~dp0\.."
title Create DJ Desktop Shortcut

where powershell >nul 2>nul
if %errorlevel% equ 0 (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0create-desktop-shortcut.ps1"
    exit /b %errorlevel%
)

if exist "%~dp0create-desktop-shortcut.vbs" (
    cscript //nologo "%~dp0create-desktop-shortcut.vbs"
)

exit /b 0

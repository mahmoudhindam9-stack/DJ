@echo off
setlocal
cd /d "%~dp0"
title DJ Desktop Studio - Uninstall

set "UNINSTALL_PS1=%~dp0app\scripts\uninstall.ps1"
if not exist "%UNINSTALL_PS1%" set "UNINSTALL_PS1=%~dp0scripts\uninstall.ps1"

where powershell.exe >nul 2>nul
if %errorlevel% equ 0 (
    if exist "%UNINSTALL_PS1%" (
        powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%UNINSTALL_PS1%"
        exit /b %errorlevel%
    )
)

echo Stopping DJ Desktop Studio on port 3000...
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr ":3000" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>nul
)

del /f /q "%USERPROFILE%\Desktop\DJ Desktop Studio.lnk" 2>nul
del /f /q "%USERPROFILE%\OneDrive\Desktop\DJ Desktop Studio.lnk" 2>nul
if exist "%~dp0dist" rd /s /q "%~dp0dist" 2>nul
if exist "%~dp0app\dist" rd /s /q "%~dp0app\dist" 2>nul

echo DJ Desktop Studio files cleaned up successfully.
timeout /t 3 >nul
exit /b 0

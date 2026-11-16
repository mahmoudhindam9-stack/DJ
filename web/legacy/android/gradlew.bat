@echo off
setlocal
set GRADLE_VERSION=9.3.1
set DIST_DIR=%USERPROFILE%\.gradle\dj-wrapper
set GRADLE_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%
set ARCHIVE=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run_gradle

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

where curl.exe >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  curl.exe -fL --retry 3 -o "%ARCHIVE%" "%URL%"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ARCHIVE%'"
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path '%GRADLE_HOME%') { Remove-Item -Recurse -Force '%GRADLE_HOME%' }; Expand-Archive -Force '%ARCHIVE%' '%DIST_DIR%'"

:run_gradle
call "%GRADLE_HOME%\bin\gradle.bat" %*
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
exit /b 0

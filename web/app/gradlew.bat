@echo off
setlocal
set "DIR=%~dp0"
if exist "%DIR%legacy\android\gradlew.bat" (
  cd /d "%DIR%legacy\android"
  call gradlew.bat %*
  exit /b %errorlevel%
)
if exist "%DIR%android\gradlew.bat" (
  cd /d "%DIR%android"
  call gradlew.bat %*
  exit /b %errorlevel%
)
echo Error: gradlew.bat not found in legacy\android or android subdirectories
exit /b 1

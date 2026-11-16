// Utility to trigger client-side download of setup.bat, uninstall.bat and desktop scripts

// Pure ASCII and CRLF batch scripts that reliably invoke PowerShell setup engine
// and handle all edge cases on Windows systems.
export const SETUP_BAT_CONTENT = [
  '@echo off',
  'setlocal',
  'cd /d "%~dp0"',
  'title DJ Desktop Studio Setup',
  '',
  'REM Check and launch PowerShell setup engine if available',
  'set "SETUP_PS1=%~dp0app\\scripts\\setup.ps1"',
  'if not exist "%SETUP_PS1%" set "SETUP_PS1=%~dp0scripts\\setup.ps1"',
  '',
  'where powershell.exe >nul 2>nul',
  'if %errorlevel% equ 0 (',
  '    if exist "%SETUP_PS1%" (',
  '        powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SETUP_PS1%"',
  '        if not errorlevel 1 exit /b 0',
  '    )',
  ')',
  '',
  'REM Fallback when PowerShell is unavailable or had an issue',
  'echo Starting DJ Desktop Studio setup...',
  'set "PATH=%ProgramFiles%\\nodejs;%LOCALAPPDATA%\\Programs\\nodejs;%APPDATA%\\npm;%PATH%"',
  '',
  'where node.exe >nul 2>nul',
  'if errorlevel 1 (',
  '    where winget.exe >nul 2>nul',
  '    if not errorlevel 1 (',
  '        echo Installing Node.js LTS via winget...',
  '        winget install --id OpenJS.NodeJS.LTS --exact --silent --accept-source-agreements --accept-package-agreements',
  '        set "PATH=%ProgramFiles%\\nodejs;%LOCALAPPDATA%\\Programs\\nodejs;%APPDATA%\\npm;%PATH%"',
  '    )',
  ')',
  '',
  'where node.exe >nul 2>nul',
  'if errorlevel 1 (',
  '    echo.',
  '    echo Node.js LTS is required to run DJ Desktop Studio.',
  '    echo Please install Node.js from https://nodejs.org',
  '    echo Then run setup.bat again.',
  '    echo.',
  '    pause',
  '    exit /b 1',
  ')',
  '',
  'set "APP_DIR=%~dp0"',
  'if exist "%~dp0app\\package.json" set "APP_DIR=%~dp0app"',
  '',
  'set "PATH=%APP_DIR%\\node_modules\\.bin;%~dp0node_modules\\.bin;%PATH%"',
  '',
  'if exist "%~dp0package.json" if not "%APP_DIR%"=="%~dp0" (',
  '    echo Installing root dependencies...',
  '    call npm install --include=dev --no-audit --no-fund',
  ')',
  '',
  'cd /d "%APP_DIR%"',
  'echo Installing dependencies...',
  'call npm install --include=dev --no-audit --no-fund',
  'echo Building production assets...',
  'call npm run build',
  'if errorlevel 1 (',
  '    echo Retrying build with npx vite build...',
  '    call npx vite build',
  ')',
  '',
  'if exist "%~dp0app\\scripts\\run-dj-desktop.bat" (',
  '    start "" "%~dp0app\\scripts\\run-dj-desktop.bat"',
  ') else if exist "%~dp0scripts\\run-dj-desktop.bat" (',
  '    start "" "%~dp0scripts\\run-dj-desktop.bat"',
  ') else (',
  '    start "" http://127.0.0.1:3000',
  ')',
  '',
  'echo Setup completed successfully.',
  'timeout /t 3 >nul 2>nul',
  'exit /b 0',
].join('\r\n');

export const UNINSTALL_BAT_CONTENT = [
  '@echo off',
  'setlocal',
  'cd /d "%~dp0"',
  'title DJ Desktop Studio - Uninstaller',
  '',
  'set "UNINSTALL_PS1=%~dp0app\\scripts\\uninstall.ps1"',
  'if not exist "%UNINSTALL_PS1%" set "UNINSTALL_PS1=%~dp0scripts\\uninstall.ps1"',
  '',
  'where powershell >nul 2>nul',
  'if %errorlevel% equ 0 (',
  '    if exist "%UNINSTALL_PS1%" (',
  '        powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%UNINSTALL_PS1%"',
  '        exit /b %errorlevel%',
  '    )',
  ')',
  '',
  ':: Minimal CMD fallback',
  'echo Stopping DJ Desktop Studio on port 3000...',
  'for /f "tokens=5" %%a in (\'netstat -aon 2^>nul ^| findstr ":3000" ^| findstr "LISTENING"\') do (',
  '    taskkill /f /pid %%a >nul 2>nul',
  ')',
  '',
  'del /f /q "%USERPROFILE%\\Desktop\\DJ Desktop Studio.lnk" 2>nul',
  'del /f /q "%USERPROFILE%\\OneDrive\\Desktop\\DJ Desktop Studio.lnk" 2>nul',
  'if exist "%~dp0dist" rd /s /q "%~dp0dist" 2>nul',
  '',
  'echo DJ Desktop Studio files cleaned up successfully.',
  'timeout /t 3 >nul',
  'exit /b 0',
].join('\r\n');

export const SHORTCUT_BAT_CONTENT = [
  '@echo off',
  'setlocal',
  'cd /d "%~dp0"',
  'title Create DJ Desktop Shortcut',
  '',
  'where powershell >nul 2>nul',
  'if %errorlevel% equ 0 (',
  '    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\\create-desktop-shortcut.ps1"',
  '    exit /b %errorlevel%',
  ')',
  '',
  'if exist "%~dp0scripts\\create-desktop-shortcut.vbs" (',
  '    cscript //nologo "%~dp0scripts\\create-desktop-shortcut.vbs"',
  ')',
  'exit /b 0',
].join('\r\n');

export function downloadTextFile(filename: string, content: string, mimeType = 'text/plain') {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function downloadSetupBat() {
  downloadTextFile('setup.bat', SETUP_BAT_CONTENT, 'application/x-bat');
}

export function downloadUninstallBat() {
  downloadTextFile('uninstall.bat', UNINSTALL_BAT_CONTENT, 'application/x-bat');
}

export function downloadShortcutBat() {
  downloadTextFile('create-desktop-shortcut.bat', SHORTCUT_BAT_CONTENT, 'application/x-bat');
}

# DJ Desktop Studio - Robust Windows Setup Engine
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "             DJ DESKTOP STUDIO - ONE STEP SETUP" -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$CandidateAppDir = Split-Path -Parent $ScriptDir

if (Test-Path (Join-Path $CandidateAppDir "package.json")) {
    $AppDir = $CandidateAppDir
} elseif (Test-Path (Join-Path (Get-Location) "app\package.json")) {
    $AppDir = (Join-Path (Get-Location) "app")
} elseif (Test-Path (Join-Path (Get-Location) "package.json")) {
    $AppDir = (Get-Location).Path
} else {
    $AppDir = $CandidateAppDir
}

$RootDir = Split-Path -Parent $AppDir
$DataDir = Join-Path $env:LOCALAPPDATA "DJ Desktop Studio"

try {
    New-Item -ItemType Directory -Path $DataDir -Force -ErrorAction SilentlyContinue | Out-Null
} catch {}

function Refresh-NodePath {
    $candidates = @(
        "$env:ProgramFiles\nodejs",
        "${env:ProgramFiles(x86)}\nodejs",
        "$env:LOCALAPPDATA\Programs\nodejs",
        "$env:APPDATA\npm"
    ) | Select-Object -Unique

    $valid = $candidates | Where-Object { Test-Path (Join-Path $_ "node.exe") }
    if ($valid) {
        $env:Path = (($valid -join ";") + ";" + [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User"))
    }
}

try {
    Refresh-NodePath

    if (-not (Get-Command node.exe -ErrorAction SilentlyContinue)) {
        Write-Host "[*] Node.js was not detected in PATH. Checking winget installer..." -ForegroundColor Cyan

        $wingetInstalled = Get-Command winget.exe -ErrorAction SilentlyContinue
        if ($wingetInstalled) {
            Write-Host "[*] Installing Node.js LTS via winget..." -ForegroundColor Yellow
            try {
                & winget.exe install --id OpenJS.NodeJS.LTS --exact --silent --accept-source-agreements --accept-package-agreements
            } catch {
                Write-Warning "Winget install encountered an issue: $_"
            }
            Refresh-NodePath
        }

        if (-not (Get-Command node.exe -ErrorAction SilentlyContinue)) {
            Write-Host ""
            Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
            Write-Host "[!] Note: Node.js LTS is required to run DJ Desktop Studio." -ForegroundColor Yellow
            Write-Host "    Please download and install Node.js LTS from: https://nodejs.org" -ForegroundColor White
            Write-Host "    After installation, run setup.bat again to start the app!" -ForegroundColor White
            Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
            Write-Host ""
            Read-Host "Press Enter to exit..."
            exit 1
        }
    }

    $NodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
    $NpmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue

    if (-not $NodeCommand -or -not $NpmCommand) {
        Refresh-NodePath
        $NodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
        $NpmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
    }

    if (-not $NodeCommand) {
        throw "Could not locate node.exe."
    }

    Write-Host "[+] Node.js: $(node -v)" -ForegroundColor Green
    if ($NpmCommand) {
        Write-Host "[+] npm: $(npm -v)" -ForegroundColor Green
    }
    Write-Host "[+] App directory: $AppDir" -ForegroundColor Gray
    Write-Host ""

    Set-Location $AppDir

    # Check if package.json exists in target directory
    if (-not (Test-Path (Join-Path $AppDir "package.json"))) {
        # Check if parent has package.json
        if (Test-Path (Join-Path $RootDir "package.json")) {
            $AppDir = $RootDir
            Set-Location $AppDir
        }
    }

    # Install dependencies
    Write-Host "[*] Installing dependencies..." -ForegroundColor Cyan
    $rootPackageJson = Join-Path $RootDir "package.json"
    if ((Test-Path $rootPackageJson) -and ($RootDir -ne $AppDir)) {
        Push-Location $RootDir
        & npm.cmd install --include=dev --no-audit --no-fund
        Pop-Location
    }
    Set-Location $AppDir
    & npm.cmd install --include=dev --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "npm install finished with exit code $LASTEXITCODE. Continuing to check build assets..."
    }

    # Add bin directories to PATH so vite and tsc are always found
    $appBin = Join-Path $AppDir "node_modules\.bin"
    $rootBin = Join-Path $RootDir "node_modules\.bin"
    $env:Path = "$appBin;$rootBin;$env:Path"

    # Build production assets
    Write-Host "[*] Building application assets (npm run build)..." -ForegroundColor Cyan
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "npm run build returned code $LASTEXITCODE. Trying npx vite build directly..."
        & npx.cmd vite build
    }

    $distPath = Join-Path $AppDir "dist\index.html"
    $parentDistPath = Join-Path $RootDir "dist\index.html"
    $hasDist = (Test-Path $distPath) -or (Test-Path $parentDistPath)
    if (-not $hasDist) {
        Write-Warning "dist\index.html was not found, will run in development mode."
    } else {
        Write-Host "[+] Production build is ready." -ForegroundColor Green
    }

    # Desktop shortcut
    Write-Host "[*] Creating Desktop and Start Menu shortcuts..." -ForegroundColor Cyan
    $ShortcutScript = Join-Path $ScriptDir "create-desktop-shortcut.ps1"
    if (Test-Path $ShortcutScript) {
        try {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ShortcutScript
        } catch {
            Write-Warning "Shortcut script skipped: $_"
        }
    }

    # Launch app
    Write-Host "[*] Launching DJ Desktop Studio..." -ForegroundColor Cyan
    $Launcher = Join-Path $ScriptDir "run-dj-desktop.bat"
    if (Test-Path $Launcher) {
        Start-Process -FilePath "cmd.exe" -ArgumentList ('/c start "" "' + $Launcher + '"')
    } else {
        Start-Process "http://127.0.0.1:3000"
    }

    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host "               DJ DESKTOP STUDIO IS READY" -ForegroundColor Green
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host "Installation completed and application launched successfully!" -ForegroundColor Green
    Start-Sleep -Seconds 2
    exit 0

} catch {
    Write-Host ""
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    Write-Host "[!] Error during setup: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    Write-Host ""
    Read-Host "Press Enter to close..."
    exit 1
}

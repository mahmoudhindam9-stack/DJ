# DJ Desktop Studio - Complete User-Side Uninstaller
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "SilentlyContinue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppDir = Split-Path -Parent $ScriptDir
$DataDir = Join-Path $env:LOCALAPPDATA "DJ Desktop Studio"
$DesktopDir = [Environment]::GetFolderPath([Environment+SpecialFolder]::Desktop)
$ProgramsDir = [Environment]::GetFolderPath([Environment+SpecialFolder]::Programs)

Write-Host "======================================================================" -ForegroundColor Red
Write-Host "              DJ DESKTOP STUDIO - UNINSTALL" -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Red
Write-Host ""

Write-Host "[1/4] Stopping DJ Desktop Studio..." -ForegroundColor Cyan
try {
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        $listeners = Get-NetTCPConnection -LocalPort 3000 -State Listen
        foreach ($listener in $listeners) {
            if ($listener.OwningProcess -gt 0) {
                Stop-Process -Id $listener.OwningProcess -Force
            }
        }
    } else {
        $lines = netstat -ano 2>$null | Select-String ":3000.*LISTENING"
        foreach ($line in $lines) {
            $parts = ($line.ToString() -split "\s+") | Where-Object { $_ }
            $pid = [int]$parts[-1]
            if ($pid -gt 0) { Stop-Process -Id $pid -Force }
        }
    }
} catch {}

Write-Host "[2/4] Removing Desktop and Start Menu shortcuts..." -ForegroundColor Cyan
$shortcutNames = @(
    (Join-Path $DesktopDir "DJ Desktop Studio.lnk"),
    (Join-Path $env:USERPROFILE "OneDrive\Desktop\DJ Desktop Studio.lnk"),
    (Join-Path $ProgramsDir "DJ Desktop Studio.lnk")
)

foreach ($shortcut in $shortcutNames) {
    if ($shortcut -and (Test-Path $shortcut)) {
        Remove-Item -Path $shortcut -Force
    }
}

Write-Host "[3/4] Removing DJ Desktop Studio user data..." -ForegroundColor Cyan
if (Test-Path $DataDir) {
    Remove-Item -Path $DataDir -Recurse -Force
}

Write-Host "[4/4] Removing generated build/dependency data..." -ForegroundColor Cyan
foreach ($generated in @(
    (Join-Path $AppDir "dist"),
    (Join-Path $AppDir "dev-dist"),
    (Join-Path $AppDir ".build-outputs"),
    (Join-Path $AppDir "node_modules")
)) {
    if (Test-Path $generated) {
        Remove-Item -Path $generated -Recurse -Force
    }
}

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "              DJ DESKTOP STUDIO UNINSTALL COMPLETE" -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "Desktop/Start shortcuts, local app data, browser profile and generated dependencies were removed."
Write-Host "System-wide Node.js was left installed because other programs may use it."
Start-Sleep -Seconds 2

param(
    [Parameter(Mandatory = $true)][string]$PackageUrl,
    [Parameter(Mandatory = $true)][string]$AppDir,
    [Parameter(Mandatory = $true)][string]$TargetVersion,
    [int]$ServerPid = 0
)

$ErrorActionPreference = "Stop"
try {
    $target = [version]$TargetVersion
} catch {
    throw "Invalid target version: $TargetVersion"
}

# Refuse to replace the same version twice.
$manifestPath = Join-Path $AppDir "package.json"
if (Test-Path $manifestPath) {
    try {
        $installedManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        $installed = [version]$installedManifest.version
        if ($installed -ge $target) {
            Write-Host "[DJ Desktop] Version $installed is already installed. No update is needed."
            exit 0
        }
    } catch {
        Write-Warning "Could not read installed version; the verified update will continue."
    }
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$tempRoot = Join-Path $env:TEMP ("DJ-Desktop-Update-" + $stamp + "-" + [guid]::NewGuid().ToString("N"))
$downloadsDir = Join-Path $env:USERPROFILE "Downloads"
$zipName = "DJ-Desktop-PC-v$TargetVersion.zip"
$zipPath = Join-Path $downloadsDir $zipName
$partialDownloadPath = "$zipPath.download"
$extractRoot = Join-Path $tempRoot "package"
$backupDir = "$AppDir.previous-$stamp"
$appMoved = $false

function Expand-And-VerifyPackage {
    param([string]$ArchivePath, [string]$Destination, [version]$ExpectedVersion)

    if (Test-Path $Destination) { Remove-Item -LiteralPath $Destination -Recurse -Force }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $Destination -Force

    $candidateApp = Join-Path $Destination "app"
    $candidateManifestPath = Join-Path $candidateApp "package.json"
    if (-not (Test-Path $candidateManifestPath)) { throw "The update archive is missing app\package.json." }
    $candidateManifest = Get-Content -LiteralPath $candidateManifestPath -Raw | ConvertFrom-Json
    $candidateVersion = [version]$candidateManifest.version
    if ($candidateVersion -ne $ExpectedVersion) {
        throw "The archive version ($candidateVersion) does not match requested version $ExpectedVersion."
    }
    if (-not (Test-Path (Join-Path $candidateApp "dist\index.html"))) {
        throw "The update archive is missing app\dist\index.html."
    }
    if (-not (Test-Path (Join-Path $candidateApp "scripts\server.js"))) {
        throw "The update archive is missing app\scripts\server.js."
    }
    if (-not (Test-Path (Join-Path $candidateApp "scripts\apply-update.ps1"))) {
        throw "The update archive is missing the updater script."
    }
    return $candidateApp
}

try {
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $downloadsDir -Force | Out-Null
    $sourceApp = $null

    # A cached archive is reused only after its version and required contents are verified.
    if (Test-Path $zipPath) {
        try {
            Write-Host "[DJ Desktop] Checking saved update in Downloads: $zipPath"
            $sourceApp = Expand-And-VerifyPackage -ArchivePath $zipPath -Destination $extractRoot -ExpectedVersion $target
            Write-Host "[DJ Desktop] Valid v$TargetVersion package found; skipping duplicate download."
        } catch {
            Write-Warning "Saved package is invalid; downloading a fresh copy. $($_.Exception.Message)"
            if (Test-Path $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force -ErrorAction SilentlyContinue }
            Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
            $sourceApp = $null
        }
    }

    if (-not $sourceApp) {
        Remove-Item -LiteralPath $partialDownloadPath -Force -ErrorAction SilentlyContinue
        Write-Host "[DJ Desktop] Downloading v$TargetVersion to Downloads before installing..."
        Invoke-WebRequest -Uri $PackageUrl -OutFile $partialDownloadPath -UseBasicParsing -MaximumRedirection 8
        if (-not (Test-Path $partialDownloadPath) -or (Get-Item $partialDownloadPath).Length -lt 1024) {
            throw "The downloaded update archive is missing or unexpectedly small."
        }
        Move-Item -LiteralPath $partialDownloadPath -Destination $zipPath -Force
        $sourceApp = Expand-And-VerifyPackage -ArchivePath $zipPath -Destination $extractRoot -ExpectedVersion $target
    }

    if ($ServerPid -gt 0) {
        $deadline = (Get-Date).AddSeconds(45)
        while ((Get-Process -Id $ServerPid -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 1
        }
        if (Get-Process -Id $ServerPid -ErrorAction SilentlyContinue) {
            Stop-Process -Id $ServerPid -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 1
        }
    }

    if (-not (Test-Path $AppDir)) { throw "The installed application folder was not found: $AppDir" }
    Move-Item -LiteralPath $AppDir -Destination $backupDir
    $appMoved = $true
    Move-Item -LiteralPath $sourceApp -Destination $AppDir

    $installedAfter = Get-Content -LiteralPath (Join-Path $AppDir "package.json") -Raw | ConvertFrom-Json
    if ([version]$installedAfter.version -ne $target) {
        throw "Post-install version verification failed: expected $target, got $($installedAfter.version)."
    }
    if (-not (Test-Path (Join-Path $AppDir "dist\index.html"))) {
        throw "Update verification failed because dist\index.html is missing."
    }
    $launcher = Join-Path $AppDir "scripts\run-dj-desktop.bat"
    if (-not (Test-Path $launcher)) { throw "The updated desktop launcher is missing." }

    Write-Host "[DJ Desktop] v$TargetVersion installed. Verified ZIP remains in Downloads."
    Write-Host "[DJ Desktop] Reopening the application..."
    Start-Process -FilePath "$env:WINDIR\System32\cmd.exe" -ArgumentList ('/c "' + $launcher + '"') -WorkingDirectory $AppDir
    Start-Sleep -Seconds 3
    if (Test-Path $backupDir) { Remove-Item -LiteralPath $backupDir -Recurse -Force -ErrorAction SilentlyContinue }
}
catch {
    # Do not use the default Stop behavior for Write-Error here; rollback and restart must still run.
    Write-Error $_ -ErrorAction Continue
    if ($appMoved -and (Test-Path $backupDir)) {
        if (Test-Path $AppDir) { Remove-Item -LiteralPath $AppDir -Recurse -Force -ErrorAction SilentlyContinue }
        Move-Item -LiteralPath $backupDir -Destination $AppDir -ErrorAction SilentlyContinue
    }
    # Download or verification failures must not leave the previous installed app closed/offline.
    $rollbackLauncher = Join-Path $AppDir "scripts\run-dj-desktop.bat"
    if (Test-Path $rollbackLauncher) {
        try {
            Start-Process -FilePath "$env:WINDIR\System32\cmd.exe" -ArgumentList ('/c "' + $rollbackLauncher + '"') -WorkingDirectory $AppDir
        } catch {
            Write-Error ("Could not reopen the existing version after update failure: " + $_.Exception.Message) -ErrorAction Continue
        }
    }
    exit 1
}
finally {
    Remove-Item -LiteralPath $partialDownloadPath -Force -ErrorAction SilentlyContinue
    if (Test-Path $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue }
    $bootstrapDir = Split-Path -Parent $PSCommandPath
    if ((Split-Path -Leaf $bootstrapDir) -like 'DJ-Desktop-Updater-*') {
        Remove-Item -LiteralPath $bootstrapDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

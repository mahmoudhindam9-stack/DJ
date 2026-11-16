$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$DataRoot = Join-Path $env:LOCALAPPDATA 'DJ Desktop Studio\AI'
$RepoDir = Join-Path $DataRoot 'ACE-Step-1.5'
$ZipPath = Join-Path $env:TEMP ('ACE-Step-1.5-' + [guid]::NewGuid().ToString('N') + '.zip')
$ExtractDir = Join-Path $env:TEMP ('ACE-Step-Extract-' + [guid]::NewGuid().ToString('N'))

Write-Host ''
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host '       DJ DESKTOP - ACE-STEP 1.5 LOCAL MUSIC AI' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host 'First setup needs an internet connection to download runtime/model files.'
Write-Host 'After the model is downloaded, music generation can run offline.'
Write-Host 'Keep this PowerShell window open while the local engine is running.'
Write-Host ''

try {
    New-Item -ItemType Directory -Path $DataRoot -Force | Out-Null

    $uv = Get-Command uv.exe -ErrorAction SilentlyContinue
    if (-not $uv) {
        $localUv = Join-Path $env:USERPROFILE '.local\bin\uv.exe'
        if (Test-Path $localUv) {
            $env:Path = (Split-Path $localUv -Parent) + ';' + $env:Path
            $uv = Get-Command uv.exe -ErrorAction SilentlyContinue
        }
    }

    if (-not $uv) {
        Write-Host '[1/4] Installing the uv Python manager...' -ForegroundColor Yellow
        Invoke-RestMethod https://astral.sh/uv/install.ps1 | Invoke-Expression
        $uv = Get-Command uv.exe -ErrorAction SilentlyContinue
        if (-not $uv) {
            $localUv = Join-Path $env:USERPROFILE '.local\bin\uv.exe'
            if (Test-Path $localUv) {
                $env:Path = (Split-Path $localUv -Parent) + ';' + $env:Path
                $uv = Get-Command uv.exe -ErrorAction SilentlyContinue
            }
        }
        if (-not $uv) { throw 'Could not find uv.exe after installation.' }
    }

    if (-not (Test-Path (Join-Path $RepoDir 'pyproject.toml'))) {
        Write-Host '[2/4] Downloading ACE-Step 1.5 source from GitHub...' -ForegroundColor Yellow
        Invoke-WebRequest -Uri 'https://codeload.github.com/ACE-Step/ACE-Step-1.5/zip/refs/heads/main' -OutFile $ZipPath -UseBasicParsing
        Expand-Archive -LiteralPath $ZipPath -DestinationPath $ExtractDir -Force
        $sourceDir = Join-Path $ExtractDir 'ACE-Step-1.5-main'
        if (-not (Test-Path (Join-Path $sourceDir 'pyproject.toml'))) {
            throw 'The downloaded ACE-Step archive did not contain pyproject.toml.'
        }
        if (Test-Path $RepoDir) { Remove-Item -LiteralPath $RepoDir -Recurse -Force }
        Move-Item -LiteralPath $sourceDir -Destination $RepoDir
    } else {
        Write-Host '[2/4] ACE-Step source is already installed.' -ForegroundColor Green
    }

    Push-Location $RepoDir
    try {
        Write-Host '[3/4] Installing Python dependencies (the first run can take a while)...' -ForegroundColor Yellow
        & $uv.Source sync --python 3.11
        if ($LASTEXITCODE -ne 0) { throw ('uv sync failed with exit code ' + $LASTEXITCODE) }

        Write-Host '[4/4] Starting the local API and preparing model weights...' -ForegroundColor Yellow
        Write-Host 'Local API address: http://127.0.0.1:8001' -ForegroundColor Green
        & $uv.Source run --python 3.11 acestep-api
        if ($LASTEXITCODE -ne 0) { throw ('ACE-Step API exited with code ' + $LASTEXITCODE) }
    } finally {
        Pop-Location
    }
} catch {
    Write-Host ''
    Write-Host ('ACE-Step setup/start failed: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Please check the message above, your GPU/driver, disk space, and internet connection.'
    Read-Host 'Press Enter to close this window'
} finally {
    if (Test-Path $ZipPath) { Remove-Item -LiteralPath $ZipPath -Force -ErrorAction SilentlyContinue }
    if (Test-Path $ExtractDir) { Remove-Item -LiteralPath $ExtractDir -Recurse -Force -ErrorAction SilentlyContinue }
}

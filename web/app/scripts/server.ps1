# Built-in Windows PowerShell HTTP server for DJ Desktop Studio (No Node or Python needed)
param([int]$Port = 3000)

$ErrorActionPreference = "SilentlyContinue"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$appDir = Split-Path -Parent $scriptDir
$distDir = Join-Path $appDir "dist"
if (-not (Test-Path $distDir)) { $distDir = $appDir }

$listener = New-Object System.Net.HttpListener
$prefix = "http://127.0.0.1:$Port/"
$listener.Prefixes.Add($prefix)

try {
    $listener.Start()
    Write-Host "[✓] DJ Desktop PowerShell Server running on $prefix"
} catch {
    # Port already occupied, exit cleanly
    exit 0
}

$mimeTypes = @{
    ".html" = "text/html; charset=utf-8"
    ".js"   = "application/javascript; charset=utf-8"
    ".mjs"  = "application/javascript; charset=utf-8"
    ".css"  = "text/css; charset=utf-8"
    ".json" = "application/json; charset=utf-8"
    ".png"  = "image/png"
    ".jpg"  = "image/jpeg"
    ".jpeg" = "image/jpeg"
    ".ico"  = "image/x-icon"
    ".mp3"  = "audio/mpeg"
    ".wav"  = "audio/wav"
    ".svg"  = "image/svg+xml"
    ".woff2"= "font/woff2"
}

while ($listener.IsListening) {
    try {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response

        $rawUrl = $request.Url.LocalPath
        if ($rawUrl -eq "/" -or [string]::IsNullOrEmpty($rawUrl)) { $rawUrl = "/index.html" }

        $relPath = $rawUrl.TrimStart("/").Replace("/", [System.IO.Path]::DirectorySeparatorChar)
        $filePath = Join-Path $distDir $relPath

        if (-not (Test-Path $filePath) -or (Test-Path $filePath -PathType Container)) {
            $filePath = Join-Path $distDir "index.html"
        }

        if (Test-Path $filePath) {
            $ext = [System.IO.Path]::GetExtension($filePath).ToLower()
            $mime = $mimeTypes[$ext]
            if (-not $mime) { $mime = "application/octet-stream" }
            $response.ContentType = $mime
            $response.AddHeader("Access-Control-Allow-Origin", "*")

            $bytes = [System.IO.File]::ReadAllBytes($filePath)
            $response.ContentLength64 = $bytes.Length
            $response.OutputStream.Write($bytes, 0, $bytes.Length)
        } else {
            $response.StatusCode = 404
        }
        $response.Close()
    } catch {
        # continue loop
    }
}

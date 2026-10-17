# Descarga las herramientas que Mirador para Windows lleva dentro (las ejecuta el CI, no el usuario):
#  - yt-dlp (descargas), ffmpeg/ffprobe (unir y convertir), mpv (reproductor) y QuickJS (lo pide yt-dlp para YouTube).
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$destino = Join-Path $PSScriptRoot '..\recursos\windows'
$tmp = Join-Path $env:RUNNER_TEMP 'herramientas'
New-Item -ItemType Directory -Force $destino, $tmp | Out-Null
$cab = @{ 'User-Agent' = 'mirador-ci' }
if ($env:GITHUB_TOKEN) { $cab['Authorization'] = "Bearer $env:GITHUB_TOKEN" }

Write-Host 'yt-dlp…'
Invoke-WebRequest 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe' -OutFile (Join-Path $destino 'yt-dlp.exe')

Write-Host 'ffmpeg…'
# Versión «lgpl-shared»: ffmpeg y ffprobe comparten las mismas librerías (pesa mucho menos que dos programas completos).
Invoke-WebRequest 'https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-lgpl-shared.zip' -OutFile "$tmp\ffmpeg.zip"
Expand-Archive "$tmp\ffmpeg.zip" "$tmp\ffmpeg" -Force
$binFf = (Get-ChildItem "$tmp\ffmpeg" -Recurse -Filter ffmpeg.exe | Select-Object -First 1).DirectoryName
foreach ($b in 'ffmpeg.exe', 'ffprobe.exe') { Copy-Item (Join-Path $binFf $b) $destino }
Get-ChildItem $binFf -Filter *.dll | ForEach-Object { Copy-Item $_.FullName $destino }

Write-Host 'mpv…'
$rel = Invoke-RestMethod -Headers $cab 'https://api.github.com/repos/shinchiro/mpv-winbuild-cmake/releases/latest'
# La versión «x86_64» común (la «v3» necesita procesadores nuevos con AVX2).
$asset = $rel.assets | Where-Object { $_.name -match '^mpv-x86_64-\d{8}-git-.*\.7z$' } | Select-Object -First 1
if (-not $asset) { throw 'No encontré mpv para Windows en la última versión de shinchiro/mpv-winbuild-cmake' }
Invoke-WebRequest $asset.browser_download_url -OutFile "$tmp\mpv.7z"
& 7z x "$tmp\mpv.7z" "-o$tmp\mpv" -y | Out-Null
Copy-Item "$tmp\mpv\mpv.exe" $destino
Get-ChildItem "$tmp\mpv" -Filter *.dll | ForEach-Object { Copy-Item $_.FullName $destino }

Write-Host 'QuickJS…'
Invoke-WebRequest 'https://github.com/quickjs-ng/quickjs/releases/latest/download/qjs-windows-x86_64.exe' -OutFile (Join-Path $destino 'qjs.exe')

Write-Host 'Comprobando…'
& (Join-Path $destino 'yt-dlp.exe') --version
& (Join-Path $destino 'ffmpeg.exe') -hide_banner -version | Select-Object -First 1
& (Join-Path $destino 'mpv.exe') --version | Select-Object -First 1
Get-ChildItem $destino | Format-Table Name, Length

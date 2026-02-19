param(
  [Parameter(Mandatory=$true)][string]$JarPath
)

$ErrorActionPreference = "Stop"
$JarPath = (Resolve-Path $JarPath).Path

$sevenZip = "C:\Program Files\7-Zip\7z.exe"
if (!(Test-Path $sevenZip)) { throw "7z.exe not found at $sevenZip" }

$work = Join-Path $PSScriptRoot "..\work"
$unpacked = Join-Path $work "unpacked"
New-Item -ItemType Directory -Force -Path $unpacked | Out-Null

# Clean previous
if (Test-Path $unpacked) { Remove-Item -Recurse -Force $unpacked -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $unpacked | Out-Null

& $sevenZip x $JarPath "-o$unpacked" -y | Out-Null

# Remove signature files (jar may be signed)
Remove-Item -Force (Join-Path $unpacked "META-INF\*.SF"), (Join-Path $unpacked "META-INF\*.RSA"), (Join-Path $unpacked "META-INF\*.DSA") -ErrorAction SilentlyContinue

Write-Host "Unpacked to: $unpacked" -ForegroundColor Green
Write-Host "Signature removed (if existed): META-INF\*.SF/*.RSA/*.DSA" -ForegroundColor Green

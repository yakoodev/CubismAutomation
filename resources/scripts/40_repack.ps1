param(
  [Parameter(Mandatory=$true)][string]$WorkDir,
  [Parameter(Mandatory=$true)][string]$OutJar
)

$ErrorActionPreference = "Stop"
$WorkDir = (Resolve-Path $WorkDir).Path

$sevenZip = "C:\Program Files\7-Zip\7z.exe"
if (!(Test-Path $sevenZip)) { throw "7z.exe not found at $sevenZip" }

$outDir = Split-Path -Parent $OutJar
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# Ensure signatures are removed
Remove-Item -Force (Join-Path $WorkDir "META-INF\*.SF"), (Join-Path $WorkDir "META-INF\*.RSA"), (Join-Path $WorkDir "META-INF\*.DSA") -ErrorAction SilentlyContinue

# Repack (zip)
if (Test-Path $OutJar) { Remove-Item -Force $OutJar }
Push-Location $WorkDir
& $sevenZip a -tzip $OutJar ".\*" | Out-Null
Pop-Location

Write-Host "Built: $OutJar" -ForegroundColor Green

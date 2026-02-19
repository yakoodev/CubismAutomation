param()

$ErrorActionPreference = "Stop"

Write-Host "== Check: Java ==" -ForegroundColor Cyan
try {
  java -version 2>&1 | ForEach-Object { $_ }
} catch {
  Write-Host "Java не найден в PATH. Поставь Temurin JDK и перезапусти консоль." -ForegroundColor Yellow
}

Write-Host "`n== Check: jar/jarsigner/javap ==" -ForegroundColor Cyan
foreach ($cmd in @("jar","jarsigner","javap","keytool")) {
  $p = (Get-Command $cmd -ErrorAction SilentlyContinue)
  if ($p) { Write-Host "$cmd => $($p.Source)" -ForegroundColor Green }
  else { Write-Host "$cmd => NOT FOUND (нужен JDK)" -ForegroundColor Yellow }
}

Write-Host "`n== Check: 7-Zip ==" -ForegroundColor Cyan
$sevenZip = "C:\Program Files\7-Zip\7z.exe"
if (Test-Path $sevenZip) { Write-Host "7z => $sevenZip" -ForegroundColor Green }
else { Write-Host "7z.exe не найден по пути: $sevenZip" -ForegroundColor Yellow }

Write-Host "`n== Check: CFR ==" -ForegroundColor Cyan
$cfr = Join-Path $PSScriptRoot "..\tools\cfr\cfr.jar"
$cfr = (Resolve-Path $cfr -ErrorAction SilentlyContinue)
if ($cfr) { Write-Host "cfr.jar => $cfr" -ForegroundColor Green }
else { Write-Host "cfr.jar не найден. Скачай из releases и положи в tools\cfr\cfr.jar" -ForegroundColor Yellow }

Write-Host "`n== Done ==" -ForegroundColor Cyan

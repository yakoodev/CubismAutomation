param(
  [Parameter(Mandatory=$true)][string]$JarPath
)

$ErrorActionPreference = "Stop"
$JarPath = (Resolve-Path $JarPath).Path

$sevenZip = "C:\Program Files\7-Zip\7z.exe"
if (!(Test-Path $sevenZip)) { throw "7z.exe not found at $sevenZip" }

$outDir = Join-Path $PSScriptRoot "..\output"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$log = Join-Path $outDir "inspect.log"

"== Inspect: $JarPath ==" | Out-File -Encoding utf8 $log

# META-INF listing (signature)
& $sevenZip l $JarPath "META-INF\*" | Out-File -Append -Encoding utf8 $log

# Extract manifest to temp
$tmp = Join-Path $env:TEMP ("cubism_manifest_" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
& $sevenZip e $JarPath "-o$tmp" "META-INF\MANIFEST.MF" -y | Out-Null

$mf = Join-Path $tmp "MANIFEST.MF"
if (Test-Path $mf) {
  "`n== MANIFEST.MF ==" | Out-File -Append -Encoding utf8 $log
  Get-Content $mf | Select-Object -First 300 | Out-File -Append -Encoding utf8 $log
} else {
  "`nMANIFEST.MF not found" | Out-File -Append -Encoding utf8 $log
}

# Check for services
"`n== META-INF/services ==" | Out-File -Append -Encoding utf8 $log
& $sevenZip l $JarPath "META-INF\services\*" | Out-File -Append -Encoding utf8 $log

# Quick top-level hints
"`n== Hints (BOOT-INF/WEB-INF/com/org/jp/net) ==" | Out-File -Append -Encoding utf8 $log
& $sevenZip l $JarPath | Select-String -Pattern "META-INF|BOOT-INF|WEB-INF|com\\|org\\|jp\\|net\\|assets|resources" | Select-Object -First 200 `
  | ForEach-Object { $_.ToString() } | Out-File -Append -Encoding utf8 $log

Write-Host "Wrote: $log" -ForegroundColor Green

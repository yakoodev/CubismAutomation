param(
  [Parameter(Mandatory=$true)][string]$JarPath
)

$ErrorActionPreference = "Stop"
$JarPath = (Resolve-Path $JarPath).Path

$cfr = Join-Path $PSScriptRoot "..\tools\cfr\cfr.jar"
$cfr = (Resolve-Path $cfr -ErrorAction SilentlyContinue)
if (!$cfr) { throw "cfr.jar not found at tools\cfr\cfr.jar" }

$out = Join-Path $PSScriptRoot "..\work\decompiled"
if (Test-Path $out) { Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $out | Out-Null

# Decompile entire jar (can be slow for big jars)
java -jar $cfr.Path $JarPath --outputdir $out --silent true

Write-Host "Decompiled to: $out" -ForegroundColor Green

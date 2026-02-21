param(
  [string]$SrcRoot = "src/server-src",
  [string]$BuildRoot = "work/build/server",
  [string]$OutJar = "output/cubism-agent-server.jar"
)

$ErrorActionPreference = "Stop"

if (!(Test-Path $SrcRoot)) {
  throw "Source root not found: $SrcRoot"
}

New-Item -ItemType Directory -Force -Path $BuildRoot | Out-Null
if (Test-Path $OutJar) { Remove-Item -Force $OutJar }

$javaFiles = Get-ChildItem -Path $SrcRoot -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
if (!$javaFiles -or $javaFiles.Count -eq 0) {
  throw "No Java files found under $SrcRoot"
}

javac --release 17 -d $BuildRoot $javaFiles
jar --create --file $OutJar -C $BuildRoot .

Write-Host "Built server jar: $OutJar" -ForegroundColor Green

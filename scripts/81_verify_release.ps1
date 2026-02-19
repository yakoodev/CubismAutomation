param(
  [Parameter(Mandatory=$true)][string]$ReleaseDir
)

$ErrorActionPreference = "Stop"
$releaseDir = (Resolve-Path $ReleaseDir).Path

$patchedJar = Join-Path $releaseDir "Live2D_Cubism_patched.jar"
$serverJar = Join-Path $releaseDir "cubism-agent-server.jar"
$shaFile = Join-Path $releaseDir "SHA256SUMS.txt"

if (!(Test-Path $patchedJar)) { throw "Missing: $patchedJar" }
if (!(Test-Path $serverJar)) { throw "Missing: $serverJar" }
if (!(Test-Path $shaFile)) { throw "Missing: $shaFile" }

# 1) Checksums
$expected = @{}
Get-Content $shaFile | Where-Object { $_ -match '\S' } | ForEach-Object {
  $parts = $_ -split '\s+\*', 2
  if ($parts.Count -eq 2) { $expected[$parts[1]] = $parts[0].Trim().ToLower() }
}

$actualPatched = (Get-FileHash $patchedJar -Algorithm SHA256).Hash.ToLower()
$actualServer = (Get-FileHash $serverJar -Algorithm SHA256).Hash.ToLower()
if ($expected["Live2D_Cubism_patched.jar"] -ne $actualPatched) { throw "Checksum mismatch: Live2D_Cubism_patched.jar" }
if ($expected["cubism-agent-server.jar"] -ne $actualServer) { throw "Checksum mismatch: cubism-agent-server.jar" }

# 2) Bytecode marker
$mainDump = javap -classpath $patchedJar -c -p com.live2d.cubism.CECubismEditorApp | Out-String
if ($mainDump -notmatch "CubismBootstrap\.bootstrap") {
  throw "Patch marker not found in CECubismEditorApp.main"
}

# 3) Signatures removed
$sigEntries = jar tf $patchedJar | Select-String -Pattern '^META-INF/.*\.(SF|RSA|DSA)$'
if ($sigEntries) {
  throw "Signature entries still present in patched jar"
}

# 4) Server entrypoint class
$serverEntries = jar tf $serverJar
if ($serverEntries -notcontains "com/live2d/cubism/agent/ServerBootstrap.class") {
  throw "ServerBootstrap class missing in server jar"
}

Write-Host "Release verification passed: $releaseDir" -ForegroundColor Green

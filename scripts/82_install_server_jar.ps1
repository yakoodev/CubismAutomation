param(
  [Parameter(Mandatory=$true)][string]$ReleaseDir,
  [string]$CubismDir = "C:\Users\Yakoo\source\Live2D Cubism 5.3",
  [switch]$NoBackup
)

$ErrorActionPreference = "Stop"

$releaseDir = (Resolve-Path $ReleaseDir).Path
$src = Join-Path $releaseDir "cubism-agent-server.jar"
if (!(Test-Path $src)) { throw "Source jar not found: $src" }

$dst = Join-Path $CubismDir "cubism-agent-server.jar"
$backupDir = Join-Path $CubismDir "agent-backups"
New-Item -ItemType Directory -Force $backupDir | Out-Null

if ((Test-Path $dst) -and -not $NoBackup) {
  $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $backup = Join-Path $backupDir ("cubism-agent-server." + $stamp + ".jar")
  Copy-Item -Force $dst $backup
  Write-Host "Backup created: $backup" -ForegroundColor Yellow
}

Copy-Item -Force $src $dst
Write-Host "Installed: $dst" -ForegroundColor Green

param(
  [string]$CubismDir = "C:\Program Files\Live2D Cubism 5.3",
  [string]$BackupFile = ""
)

$ErrorActionPreference = "Stop"

$dst = Join-Path $CubismDir "cubism-agent-server.jar"
$backupDir = Join-Path $CubismDir "agent-backups"
if (!(Test-Path $backupDir)) { throw "Backup dir not found: $backupDir" }

if ([string]::IsNullOrWhiteSpace($BackupFile)) {
  $candidate = Get-ChildItem $backupDir -Filter "cubism-agent-server.*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (!$candidate) { throw "No backup files found in $backupDir" }
  $BackupFile = $candidate.FullName
}

$src = (Resolve-Path $BackupFile).Path
Copy-Item -Force $src $dst
Write-Host "Rollback restored: $src -> $dst" -ForegroundColor Green

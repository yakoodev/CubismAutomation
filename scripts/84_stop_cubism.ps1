param(
  [string]$CubismDir = "C:\Users\Yakoo\source\Live2D Cubism 5.3",
  [switch]$Force
)

$ErrorActionPreference = "Stop"

$targets = @(
  (Join-Path $CubismDir "CubismEditor5.exe"),
  (Join-Path $CubismDir "app\jre\bin\java.exe")
)

$procs = Get-Process | Where-Object {
  $p = $_.Path
  if ([string]::IsNullOrWhiteSpace($p)) { return $false }
  foreach ($t in $targets) {
    if ($p -ieq $t -or $p.StartsWith($CubismDir, [System.StringComparison]::OrdinalIgnoreCase)) {
      return $true
    }
  }
  return $false
}

if (!$procs) {
  Write-Host "No Cubism processes found under: $CubismDir" -ForegroundColor Yellow
  exit 0
}

foreach ($p in $procs) {
  try {
    if (!$Force -and $p.MainWindowHandle -ne 0) {
      $null = $p.CloseMainWindow()
    }
  } catch {
    # continue to force phase if needed
  }
}

Start-Sleep -Milliseconds 1200

$alive = @()
foreach ($p in $procs) {
  try {
    $still = Get-Process -Id $p.Id -ErrorAction Stop
    $alive += $still
  } catch {
    # already stopped
  }
}

if ($alive) {
  foreach ($p in $alive) {
    try {
      Stop-Process -Id $p.Id -Force
    } catch {
      Write-Warning ("Failed to stop PID {0}: {1}" -f $p.Id, $_.Exception.Message)
    }
  }
}

Write-Host ("Stopped Cubism processes: {0}" -f $procs.Count) -ForegroundColor Green

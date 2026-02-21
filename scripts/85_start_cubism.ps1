param(
  [string]$CubismDir = "C:\Users\Yakoo\source\Live2D Cubism 5.3",
  [string]$BaseUrl = "http://127.0.0.1:18080",
  [int]$WaitSec = 120
)

$ErrorActionPreference = "Stop"

$exe = Join-Path $CubismDir "CubismEditor5.exe"
if (!(Test-Path $exe)) {
  throw "Cubism executable not found: $exe"
}

function Test-Health {
  param([string]$Url)
  try {
    $r = Invoke-WebRequest -UseBasicParsing "$Url/health" -TimeoutSec 3
    return $r.StatusCode -eq 200
  } catch {
    return $false
  }
}

if (Test-Health -Url $BaseUrl) {
  Write-Host "Cubism API already healthy: $BaseUrl" -ForegroundColor Green
  exit 0
}

Push-Location $CubismDir
try {
  Start-Process -FilePath $exe -WorkingDirectory $CubismDir | Out-Null
} finally {
  Pop-Location
}

$ok = $false
for ($i = 0; $i -lt $WaitSec; $i++) {
  Start-Sleep -Seconds 1
  if (Test-Health -Url $BaseUrl) {
    $ok = $true
    break
  }
}

if (-not $ok) {
  throw "Cubism API did not become healthy within $WaitSec seconds: $BaseUrl/health"
}

Write-Host "Cubism API is healthy: $BaseUrl" -ForegroundColor Green

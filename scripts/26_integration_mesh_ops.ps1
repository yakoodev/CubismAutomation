param(
  [string]$BaseUrl = "http://127.0.0.1:18080",
  [string]$DocumentType = "model"
)

$ErrorActionPreference = "Stop"

function Post-Capture([string]$Path, [string]$Body) {
  try {
    $res = Invoke-WebRequest -UseBasicParsing ($BaseUrl + $Path) -Method POST -Body $Body -ContentType "application/json"
    return @{ Status = $res.StatusCode; Body = $res.Content.Trim() }
  } catch {
    $resp = $_.Exception.Response
    if ($resp -eq $null) { throw }
    $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
    $text = $reader.ReadToEnd().Trim()
    return @{ Status = [int]$resp.StatusCode; Body = $text }
  }
}

function Assert-AgentUp {
  try {
    $null = Invoke-WebRequest -UseBasicParsing ($BaseUrl + "/health") -Method GET -TimeoutSec 5
  } catch {
    throw "Agent is not reachable at $BaseUrl. Start Cubism with cubism-agent-server.jar first."
  }
}

Assert-AgentUp

Write-Host "== Mesh ops integration ==" -ForegroundColor Cyan
Write-Host "Document type tag: $DocumentType" -ForegroundColor Yellow
Write-Host "Run this while corresponding Cubism document is opened." -ForegroundColor Yellow

$dryRunPayload = '{"validate_only":true,"operations":[{"op":"auto_mesh"},{"op":"divide"},{"op":"connect"},{"op":"reset_shape"},{"op":"fit_contour"}]}'
$runPayload = '{"validate_only":false,"operations":[{"op":"auto_mesh"},{"op":"divide"},{"op":"connect"},{"op":"reset_shape"},{"op":"fit_contour"}]}'
$mixedPayload = '{"validate_only":false,"operations":[{"op":"unknown_op"},{"op":"auto_mesh"},{"op":"fit_contour"}]}'
$autoPayload = '{"validate_only":true}'

$dryRun = Post-Capture "/mesh/ops" $dryRunPayload
$run = Post-Capture "/mesh/ops" $runPayload
$mixed = Post-Capture "/mesh/ops" $mixedPayload
$auto = Post-Capture "/mesh/auto_generate" $autoPayload

Write-Host "POST /mesh/ops dry-run => HTTP $($dryRun.Status) $($dryRun.Body)" -ForegroundColor Green
Write-Host "POST /mesh/ops execute => HTTP $($run.Status) $($run.Body)" -ForegroundColor Green
Write-Host "POST /mesh/ops mixed   => HTTP $($mixed.Status) $($mixed.Body)" -ForegroundColor Green
Write-Host "POST /mesh/auto_generate dry => HTTP $($auto.Status) $($auto.Body)" -ForegroundColor Green

param(
  [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"

Write-Host "== State API smoke ==" -ForegroundColor Cyan
$all = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state").Content.Trim()
$project = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state/project").Content.Trim()
$document = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state/document").Content.Trim()
$selection = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state/selection").Content.Trim()
$ui = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state/ui").Content.Trim()

$allObj = $all | ConvertFrom-Json
$uiObj = $ui | ConvertFrom-Json
if (-not $allObj.ok) { throw "/state returned ok=false" }
if (-not $uiObj.ok) { throw "/state/ui returned ok=false" }
if ($null -eq $allObj.ui) { throw "/state missing ui section (post-verify failed)" }
if ($null -eq $uiObj.ui) { throw "/state/ui missing ui payload" }
if ($null -eq $uiObj.ui.documentPresent) { throw "/state/ui missing documentPresent guardrail field" }

$methodCheckCode = $null
try {
  Invoke-WebRequest -UseBasicParsing "$BaseUrl/state/ui" -Method POST -ContentType "application/json" -Body "{}" | Out-Null
  throw "POST /state/ui unexpectedly succeeded (expected 405)"
} catch {
  if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
    $methodCheckCode = [int]$_.Exception.Response.StatusCode
  } elseif ($_.Exception.Message -match '\b405\b') {
    $methodCheckCode = 405
  } else {
    throw
  }
}
if ($methodCheckCode -ne 405) { throw "POST /state/ui expected 405, got $methodCheckCode" }

Write-Host "/state           => $all" -ForegroundColor Green
Write-Host "/state/project   => $project" -ForegroundColor Green
Write-Host "/state/document  => $document" -ForegroundColor Green
Write-Host "/state/selection => $selection" -ForegroundColor Green
Write-Host "/state/ui        => $ui" -ForegroundColor Green
Write-Host "POST /state/ui   => 405 method_not_allowed (expected)" -ForegroundColor Green

param(
  [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"

Write-Host "== State API smoke ==" -ForegroundColor Cyan
$all = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state").Content.Trim()
$project = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state/project").Content.Trim()
$document = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state/document").Content.Trim()
$selection = (Invoke-WebRequest -UseBasicParsing "$BaseUrl/state/selection").Content.Trim()

Write-Host "/state           => $all" -ForegroundColor Green
Write-Host "/state/project   => $project" -ForegroundColor Green
Write-Host "/state/document  => $document" -ForegroundColor Green
Write-Host "/state/selection => $selection" -ForegroundColor Green

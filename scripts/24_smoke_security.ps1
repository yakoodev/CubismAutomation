param(
  [string]$BaseUrl = "http://127.0.0.1:18080",
  [string]$Token = "test-token-070"
)

$ErrorActionPreference = "Stop"

function Invoke-Capture([string]$Method, [string]$Path, [string]$Body, [hashtable]$Headers) {
  try {
    $res = Invoke-WebRequest -UseBasicParsing ($BaseUrl + $Path) -Method $Method -Headers $Headers -Body $Body -ContentType "application/json"
    return @{ Status = $res.StatusCode; Body = $res.Content.Trim() }
  } catch {
    $resp = $_.Exception.Response
    if ($resp -eq $null) { throw }
    $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
    $text = $reader.ReadToEnd().Trim()
    return @{ Status = [int]$resp.StatusCode; Body = $text }
  }
}

Write-Host "== Security smoke ==" -ForegroundColor Cyan
$unauth = Invoke-Capture "GET" "/version" $null @{}
$authHeaders = @{ Authorization = "Bearer $Token" }
$authVersion = Invoke-Capture "GET" "/version" $null $authHeaders
$zoomIn = Invoke-Capture "POST" "/command" '{"command":"cubism.zoom_in"}' $authHeaders
$zoomOut = Invoke-Capture "POST" "/command" '{"command":"cubism.zoom_out"}' $authHeaders

Write-Host "GET /version (unauth) => HTTP $($unauth.Status) $($unauth.Body)" -ForegroundColor Green
Write-Host "GET /version (auth)   => HTTP $($authVersion.Status) $($authVersion.Body)" -ForegroundColor Green
Write-Host "POST zoom_in          => HTTP $($zoomIn.Status) $($zoomIn.Body)" -ForegroundColor Green
Write-Host "POST zoom_out         => HTTP $($zoomOut.Status) $($zoomOut.Body)" -ForegroundColor Green

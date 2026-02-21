param(
  [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

function Invoke-HttpJson {
  param(
    [string]$Method,
    [string]$Path,
    [string]$Body = $null
  )
  $url = $BaseUrl.TrimEnd('/') + $Path
  $handler = New-Object System.Net.Http.HttpClientHandler
  $client = New-Object System.Net.Http.HttpClient($handler)
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(60)
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Method), $url)
    if ($null -ne $Body -and $Method -ne "GET") {
      $req.Content = New-Object System.Net.Http.StringContent($Body, [Text.Encoding]::UTF8, "application/json")
    }
    $res = $client.SendAsync($req).GetAwaiter().GetResult()
    $text = $res.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if ($null -eq $text) { $text = "" }
    return @{
      StatusCode = [int]$res.StatusCode
      Body = $text.Trim()
    }
  } finally {
    $client.Dispose()
    $handler.Dispose()
  }
}

Write-Host "== Metrics API smoke ==" -ForegroundColor Cyan

powershell -ExecutionPolicy Bypass -File scripts/85_start_cubism.ps1 -BaseUrl $BaseUrl -WaitSec 160

$m0 = Invoke-HttpJson -Method "GET" -Path "/metrics"
if ($m0.StatusCode -ne 200) { throw "/metrics failed: $($m0.Body)" }
$o0 = $m0.Body | ConvertFrom-Json
if (-not $o0.ok) { throw "/metrics returned ok=false: $($m0.Body)" }
if ($null -eq $o0.requests.total) { throw "/metrics missing requests.total" }
if ($null -eq $o0.paths) { throw "/metrics missing paths map" }

# generate traffic
$null = Invoke-HttpJson -Method "GET" -Path "/health"
$null = Invoke-HttpJson -Method "GET" -Path "/version"
$null = Invoke-HttpJson -Method "GET" -Path "/state/document"

$m1 = Invoke-HttpJson -Method "GET" -Path "/metrics"
if ($m1.StatusCode -ne 200) { throw "second /metrics failed: $($m1.Body)" }
$o1 = $m1.Body | ConvertFrom-Json
if ($o1.requests.total -le $o0.requests.total) {
  throw "post-verify failed: requests.total did not increase ($($o0.requests.total) -> $($o1.requests.total))"
}

# guardrail/error path
$bad = Invoke-HttpJson -Method "POST" -Path "/metrics" -Body "{}"
if ($bad.StatusCode -ne 405) { throw "POST /metrics expected 405, got $($bad.StatusCode)" }

Write-Host "/metrics happy => OK" -ForegroundColor Green
Write-Host "post-verify requests.total increased => OK ($($o0.requests.total) -> $($o1.requests.total))" -ForegroundColor Green
Write-Host "POST /metrics => 405 (expected)" -ForegroundColor Green

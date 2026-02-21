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
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    try {
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
    } catch {
      return @{
        StatusCode = 599
        Body = [string]$_.Exception.Message
      }
    }
  } finally {
    $client.Dispose()
    $handler.Dispose()
  }
}

function Ensure-ServerHealthy {
  $health = Invoke-HttpJson -Method "GET" -Path "/health"
  if ($health.StatusCode -eq 200) { return }
  Write-Host "API is down, starting Cubism..." -ForegroundColor Yellow
  powershell -ExecutionPolicy Bypass -File scripts/85_start_cubism.ps1 -BaseUrl $BaseUrl -WaitSec 160
}

function Wait-DocumentReady {
  param([int]$TimeoutSec = 90)
  for ($i = 0; $i -lt $TimeoutSec; $i++) {
    $doc = Invoke-HttpJson -Method "GET" -Path "/state/document"
    if ($doc.StatusCode -eq 200 -and $doc.Body) {
      try {
        $o = $doc.Body | ConvertFrom-Json
        if ($o.ok -and $o.document -and $o.document.present -eq $true) { return $true }
      } catch {}
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

Write-Host "== Parameters API smoke ==" -ForegroundColor Cyan
Ensure-ServerHealthy

$stateRes = Invoke-HttpJson -Method "GET" -Path "/parameters/state"
if ($stateRes.StatusCode -eq 409 -and $stateRes.Body -match "no_document") {
  Write-Host "No document, running startup/prepare with retries..." -ForegroundColor Yellow
  $prepared = $false
  for ($attempt = 1; $attempt -le 3; $attempt++) {
    $prepBody = '{"license_mode":"free","create_new_model":true,"wait_timeout_ms":45000}'
    $prepRes = Invoke-HttpJson -Method "POST" -Path "/startup/prepare" -Body $prepBody
    if ($prepRes.StatusCode -eq 200 -or (Wait-DocumentReady -TimeoutSec 70)) {
      $prepared = $true
      break
    }
    Write-Host "startup/prepare attempt $attempt failed: $($prepRes.StatusCode) $($prepRes.Body)" -ForegroundColor Yellow
    Start-Sleep -Seconds 2
  }
  if (-not $prepared) { throw "startup/prepare did not produce ready document" }
  $stateRes = Invoke-HttpJson -Method "GET" -Path "/parameters/state"
}

if ($stateRes.StatusCode -ne 200) { throw "/parameters/state failed: $($stateRes.Body)" }
$state = $stateRes.Body | ConvertFrom-Json
if (-not $state.ok) { throw "/parameters/state returned ok=false: $($stateRes.Body)" }
if ($state.count -lt 1) { throw "/parameters/state returned empty parameter list" }

$first = $state.parameters | Select-Object -First 1
if ($null -eq $first -or [string]::IsNullOrWhiteSpace($first.id)) {
  throw "unable to resolve first parameter id"
}

$targetValue = $first.default
if ($null -eq $targetValue) {
  if ($null -ne $first.value) { $targetValue = $first.value } else { $targetValue = 0.0 }
}

# happy path + post-verify
$setValueStr = ([double]$targetValue).ToString([Globalization.CultureInfo]::InvariantCulture)
$setBody = '{"id":"' + $first.id + '","value":' + $setValueStr + '}'
$setRes = Invoke-HttpJson -Method "POST" -Path "/parameters/set" -Body $setBody
if ($setRes.StatusCode -ne 200) { throw "happy path set failed status=$($setRes.StatusCode): $($setRes.Body)" }
$setObj = $setRes.Body | ConvertFrom-Json
if (-not $setObj.ok) { throw "happy path set failed body: $($setRes.Body)" }

$verifyRes = Invoke-HttpJson -Method "GET" -Path "/parameters/state"
if ($verifyRes.StatusCode -ne 200) { throw "post-verify read failed: $($verifyRes.Body)" }
$verify = $verifyRes.Body | ConvertFrom-Json
$after = $verify.parameters | Where-Object { $_.id -eq $first.id } | Select-Object -First 1
if ($null -eq $after) { throw "post-verify failed: parameter missing after set" }
if ($null -eq $after.value) { throw "post-verify failed: parameter has null value after set" }

# error path: out_of_range
$badValue = [double]($first.max + 1000.0)
$badValueStr = $badValue.ToString([Globalization.CultureInfo]::InvariantCulture)
$badBody = '{"id":"' + $first.id + '","value":' + $badValueStr + '}'
$badRes = Invoke-HttpJson -Method "POST" -Path "/parameters/set" -Body $badBody
if ($badRes.StatusCode -ne 400) { throw "out_of_range expected 400, got $($badRes.StatusCode)" }
if ($badRes.Body -notmatch "out_of_range") { throw "out_of_range body check failed: $($badRes.Body)" }

# guardrail: wrong method
$guardRes = Invoke-HttpJson -Method "GET" -Path "/parameters/set"
if ($guardRes.StatusCode -ne 405) { throw "GET /parameters/set expected 405, got $($guardRes.StatusCode)" }

Write-Host "/parameters/state => OK (count=$($state.count))" -ForegroundColor Green
Write-Host "/parameters/set happy + post-verify => OK (id=$($first.id))" -ForegroundColor Green
Write-Host "/parameters/set out_of_range => 400 (expected)" -ForegroundColor Green
Write-Host "GET /parameters/set => 405 (expected)" -ForegroundColor Green

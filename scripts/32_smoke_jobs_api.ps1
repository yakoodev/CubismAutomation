param(
  [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

function Invoke-HttpJson {
  param(
    [string]$Method,
    [string]$Path,
    [string]$Body = $null,
    [hashtable]$Headers = $null
  )
  $url = $BaseUrl.TrimEnd('/') + $Path
  $handler = New-Object System.Net.Http.HttpClientHandler
  $client = New-Object System.Net.Http.HttpClient($handler)
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Method), $url)
    if ($Headers) {
      foreach ($k in $Headers.Keys) {
        $null = $req.Headers.TryAddWithoutValidation([string]$k, [string]$Headers[$k])
      }
    }
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

function Wait-JobTerminal {
  param([string]$JobId, [int]$TimeoutSec = 60)
  for ($i = 0; $i -lt $TimeoutSec; $i++) {
    $res = Invoke-HttpJson -Method "GET" -Path ("/jobs/" + $JobId)
    if ($res.StatusCode -ne 200) { throw "job poll failed: $($res.StatusCode) $($res.Body)" }
    $obj = $res.Body | ConvertFrom-Json
    $status = $obj.job.status
    if ($status -in @("done","failed","canceled")) {
      return $obj.job
    }
    Start-Sleep -Seconds 1
  }
  throw "job $JobId did not reach terminal state in time"
}

Write-Host "== Jobs API smoke ==" -ForegroundColor Cyan
powershell -ExecutionPolicy Bypass -File scripts/85_start_cubism.ps1 -BaseUrl $BaseUrl -WaitSec 160

# happy path + idempotency
$idemKey = "smoke-jobs-idem-" + (Get-Date -Format "yyyyMMddHHmmss")
$create1 = Invoke-HttpJson -Method "POST" -Path "/jobs" -Body '{"action":"noop"}' -Headers @{ "Idempotency-Key" = $idemKey }
if ($create1.StatusCode -ne 202) { throw "create noop job failed: $($create1.StatusCode) $($create1.Body)" }
$j1 = $create1.Body | ConvertFrom-Json
$id1 = [string]$j1.job.id
if ([string]::IsNullOrWhiteSpace($id1)) { throw "missing job id in create response" }
$term1 = Wait-JobTerminal -JobId $id1 -TimeoutSec 30
if ($term1.status -ne "done") { throw "noop job expected done, got $($term1.status)" }

$create2 = Invoke-HttpJson -Method "POST" -Path "/jobs" -Body '{"action":"noop"}' -Headers @{ "Idempotency-Key" = $idemKey }
if ($create2.StatusCode -ne 200) { throw "idempotent replay expected 200, got $($create2.StatusCode)" }
$j2 = $create2.Body | ConvertFrom-Json
if (-not $j2.idempotent_reused) { throw "idempotent_reused expected true" }
if ($j2.job.id -ne $id1) { throw "idempotent replay returned different job id" }

# cancel path
$sleepCreate = Invoke-HttpJson -Method "POST" -Path "/jobs" -Body '{"action":"sleep","sleep_ms":10000}'
if ($sleepCreate.StatusCode -ne 202) { throw "create sleep job failed: $($sleepCreate.StatusCode) $($sleepCreate.Body)" }
$sleepJob = ($sleepCreate.Body | ConvertFrom-Json).job
$sleepId = [string]$sleepJob.id
$cancel = Invoke-HttpJson -Method "POST" -Path ("/jobs/" + $sleepId + "/cancel") -Body "{}"
if ($cancel.StatusCode -ne 202) { throw "cancel expected 202, got $($cancel.StatusCode)" }
$term2 = Wait-JobTerminal -JobId $sleepId -TimeoutSec 45
if ($term2.status -notin @("canceled","done")) {
  throw "cancel flow expected canceled or done (race), got $($term2.status)"
}

# guardrail: wrong method
$badMethod = Invoke-HttpJson -Method "GET" -Path ("/jobs/" + $sleepId + "/cancel")
if ($badMethod.StatusCode -ne 405) { throw "GET /jobs/{id}/cancel expected 405, got $($badMethod.StatusCode)" }

Write-Host "POST /jobs + poll => OK ($id1 done)" -ForegroundColor Green
Write-Host "Idempotency-Key replay => OK (same job id)" -ForegroundColor Green
Write-Host "Cancel flow => OK ($sleepId status=$($term2.status))" -ForegroundColor Green
Write-Host "GET /jobs/{id}/cancel => 405 (expected)" -ForegroundColor Green

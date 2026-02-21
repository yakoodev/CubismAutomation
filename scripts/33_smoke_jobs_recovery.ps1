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

Write-Host "== Jobs recovery smoke ==" -ForegroundColor Cyan

# Phase 1: create deterministic job with Idempotency-Key
powershell -ExecutionPolicy Bypass -File scripts/85_start_cubism.ps1 -BaseUrl $BaseUrl -WaitSec 160
$idem = "smoke-jobs-recovery-" + (Get-Date -Format "yyyyMMddHHmmss")
$create = Invoke-HttpJson -Method "POST" -Path "/jobs" -Body '{"action":"noop"}' -Headers @{ "Idempotency-Key" = $idem }
if ($create.StatusCode -ne 202) { throw "create job failed: $($create.StatusCode) $($create.Body)" }
$jobId = [string](($create.Body | ConvertFrom-Json).job.id)
if ([string]::IsNullOrWhiteSpace($jobId)) { throw "missing job id" }
$termBefore = Wait-JobTerminal -JobId $jobId -TimeoutSec 30
if ($termBefore.status -ne "done") { throw "expected done before restart, got $($termBefore.status)" }

# Phase 2: restart Cubism and verify recovery + idempotency continuity
powershell -ExecutionPolicy Bypass -File scripts/84_stop_cubism.ps1
powershell -ExecutionPolicy Bypass -File scripts/85_start_cubism.ps1 -BaseUrl $BaseUrl -WaitSec 200

$getAfter = Invoke-HttpJson -Method "GET" -Path ("/jobs/" + $jobId)
if ($getAfter.StatusCode -ne 200) { throw "recovery get failed: $($getAfter.StatusCode) $($getAfter.Body)" }
$jobAfter = ($getAfter.Body | ConvertFrom-Json).job
if ($jobAfter.id -ne $jobId) { throw "recovered id mismatch" }
if ($jobAfter.status -notin @("done","failed","canceled")) { throw "recovered terminal status expected, got $($jobAfter.status)" }

$replay = Invoke-HttpJson -Method "POST" -Path "/jobs" -Body '{"action":"noop"}' -Headers @{ "Idempotency-Key" = $idem }
if ($replay.StatusCode -ne 200) { throw "idempotency continuity expected 200, got $($replay.StatusCode)" }
$replayObj = $replay.Body | ConvertFrom-Json
if (-not $replayObj.idempotent_reused) { throw "idempotent_reused expected true" }
if ($replayObj.job.id -ne $jobId) { throw "idempotency continuity returned different id" }

# Error path: invalid action must fail
$invalid = Invoke-HttpJson -Method "POST" -Path "/jobs" -Body '{"action":"unknown_action_xyz"}'
if ($invalid.StatusCode -ne 202) { throw "invalid action create should return 202, got $($invalid.StatusCode)" }
$invalidId = [string](($invalid.Body | ConvertFrom-Json).job.id)
$invalidTerm = Wait-JobTerminal -JobId $invalidId -TimeoutSec 30
if ($invalidTerm.status -ne "failed") { throw "invalid action must fail, got $($invalidTerm.status)" }
if ($invalidTerm.error -ne "invalid_action") { throw "invalid action error mismatch: $($invalidTerm.error)" }

# Guardrail
$badMethod = Invoke-HttpJson -Method "GET" -Path ("/jobs/" + $jobId + "/cancel")
if ($badMethod.StatusCode -ne 405) { throw "GET /jobs/{id}/cancel expected 405, got $($badMethod.StatusCode)" }

# Post verify
$list = Invoke-HttpJson -Method "GET" -Path "/jobs"
if ($list.StatusCode -ne 200) { throw "GET /jobs failed after restart: $($list.StatusCode)" }
$jobs = ($list.Body | ConvertFrom-Json).jobs
if (-not ($jobs | Where-Object { $_.id -eq $jobId })) { throw "recovered job not found in list" }

Write-Host "Recovery after restart => OK (job=$jobId)" -ForegroundColor Green
Write-Host "Idempotency continuity => OK (same id)" -ForegroundColor Green
Write-Host "Error path invalid_action => OK (failed)" -ForegroundColor Green
Write-Host "Guardrail + post-verify => OK" -ForegroundColor Green

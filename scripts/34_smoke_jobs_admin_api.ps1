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
    if ($null -ne $Body -and $Method -notin @("GET","DELETE")) {
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
    if ($res.StatusCode -eq 404) { return $null }
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

Write-Host "== Jobs admin API smoke ==" -ForegroundColor Cyan
powershell -ExecutionPolicy Bypass -File scripts/85_start_cubism.ps1 -BaseUrl $BaseUrl -WaitSec 160

# Prepare terminal jobs
$j1 = Invoke-HttpJson -Method "POST" -Path "/jobs" -Body '{"action":"noop"}'
if ($j1.StatusCode -ne 202) { throw "create job #1 failed: $($j1.StatusCode) $($j1.Body)" }
$id1 = [string](($j1.Body | ConvertFrom-Json).job.id)
$term1 = Wait-JobTerminal -JobId $id1 -TimeoutSec 30
if ($term1.status -ne "done") { throw "job #1 must be done, got $($term1.status)" }

$j2 = Invoke-HttpJson -Method "POST" -Path "/jobs" -Body '{"action":"noop"}'
if ($j2.StatusCode -ne 202) { throw "create job #2 failed: $($j2.StatusCode) $($j2.Body)" }
$id2 = [string](($j2.Body | ConvertFrom-Json).job.id)
$term2 = Wait-JobTerminal -JobId $id2 -TimeoutSec 30
if ($term2.status -ne "done") { throw "job #2 must be done, got $($term2.status)" }

# Filter list
$filtered = Invoke-HttpJson -Method "GET" -Path "/jobs?status=done&limit=1"
if ($filtered.StatusCode -ne 200) { throw "filtered list failed: $($filtered.StatusCode) $($filtered.Body)" }
$filteredObj = $filtered.Body | ConvertFrom-Json
if ($filteredObj.filters.status -ne "done") { throw "status filter mismatch" }
if ($filteredObj.filters.limit -ne 1) { throw "limit filter mismatch" }
if ($filteredObj.jobs.Count -gt 1) { throw "filtered jobs size should be <= 1" }

# DELETE happy path + post verify
$del = Invoke-HttpJson -Method "DELETE" -Path ("/jobs/" + $id1)
if ($del.StatusCode -ne 200) { throw "delete failed: $($del.StatusCode) $($del.Body)" }
$afterDelete = Invoke-HttpJson -Method "GET" -Path ("/jobs/" + $id1)
if ($afterDelete.StatusCode -ne 404) { throw "post-delete get expected 404, got $($afterDelete.StatusCode)" }

# Error path: delete again should be no_effect
$delAgain = Invoke-HttpJson -Method "DELETE" -Path ("/jobs/" + $id1)
if ($delAgain.StatusCode -ne 404) { throw "delete missing expected 404, got $($delAgain.StatusCode)" }
if (-not ($delAgain.Body -match '"error":"no_effect"')) { throw "delete missing should return no_effect" }

# Cleanup happy path
$cleanup = Invoke-HttpJson -Method "POST" -Path "/jobs/cleanup" -Body '{"status":"done","limit":50}'
if ($cleanup.StatusCode -ne 200) { throw "cleanup failed: $($cleanup.StatusCode) $($cleanup.Body)" }
$cleanupObj = $cleanup.Body | ConvertFrom-Json
if ($cleanupObj.deleted -lt 1) { throw "cleanup should delete at least one terminal job" }

# Guardrail: wrong method for cleanup
$badMethod = Invoke-HttpJson -Method "GET" -Path "/jobs/cleanup"
if ($badMethod.StatusCode -ne 405) { throw "GET /jobs/cleanup expected 405, got $($badMethod.StatusCode)" }

# Post-verify
$checkId2 = Invoke-HttpJson -Method "GET" -Path ("/jobs/" + $id2)
if ($checkId2.StatusCode -eq 200) {
  $status2 = ($checkId2.Body | ConvertFrom-Json).job.status
  if ($status2 -notin @("done","failed","canceled","running","queued")) { throw "unexpected status after cleanup: $status2" }
}

Write-Host "GET /jobs filters => OK" -ForegroundColor Green
Write-Host "DELETE /jobs/{id} + no_effect path => OK" -ForegroundColor Green
Write-Host "POST /jobs/cleanup + guardrail => OK" -ForegroundColor Green

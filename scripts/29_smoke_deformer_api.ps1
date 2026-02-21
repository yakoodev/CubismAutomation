param(
  [string]$BaseUrl = "http://127.0.0.1:18080",
  [string]$ModelDir = "C:\Users\Yakoo\Downloads\vt\hibiki"
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
    $client.Timeout = [TimeSpan]::FromSeconds(300)
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

function Find-OpenCandidate {
  param([string]$Dir)
  if (!(Test-Path $Dir)) { return $null }
  $patterns = @("*.cmo3", "*.can3", "*.model3.json", "*.cmox")
  foreach ($p in $patterns) {
    $f = Get-ChildItem -Path $Dir -Recurse -File -Filter $p -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($f) { return $f.FullName }
  }
  return $null
}

Write-Host "== Deformer API smoke ==" -ForegroundColor Cyan
Ensure-ServerHealthy

$stateRes = Invoke-HttpJson -Method "GET" -Path "/deformers/state"
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
  $stateRes = Invoke-HttpJson -Method "GET" -Path "/deformers/state"
}

if ($stateRes.StatusCode -ne 200) { throw "/deformers/state failed: $($stateRes.Body)" }
$state = $stateRes.Body | ConvertFrom-Json
if (-not $state.ok) { throw "/deformers/state returned ok=false: $($stateRes.Body)" }
if ($state.count -lt 1) {
  $candidate = Find-OpenCandidate -Dir $ModelDir
  if (-not [string]::IsNullOrWhiteSpace($candidate)) {
    Write-Host "No deformers in current doc. Trying project/open: $candidate" -ForegroundColor Yellow
    $payload = '{"path":"' + ($candidate -replace '\\','\\') + '","close_current_first":true}'
    $openRes = Invoke-HttpJson -Method "POST" -Path "/project/open" -Body $payload
    if ($openRes.StatusCode -eq 200 -or $openRes.StatusCode -eq 599) {
      if ($openRes.StatusCode -eq 599) {
        Write-Host "project/open timed out at client side, waiting for async completion..." -ForegroundColor Yellow
        Start-Sleep -Seconds 12
      }
      Start-Sleep -Seconds 2
      $stateRes = Invoke-HttpJson -Method "GET" -Path "/deformers/state"
      if ($stateRes.StatusCode -eq 200) {
        $state = $stateRes.Body | ConvertFrom-Json
      }
    } else {
      Write-Host "project/open failed: $($openRes.StatusCode) $($openRes.Body)" -ForegroundColor Yellow
    }
  }
}
if ($state.count -lt 1) { throw "/deformers/state returned empty deformer list" }

$first = $state.deformers | Select-Object -First 1
if ($null -eq $first -or [string]::IsNullOrWhiteSpace($first.id)) {
  throw "unable to resolve first deformer id"
}

# happy path: select + post-verify
$selectBody = '{"deformer_id":"' + $first.id + '"}'
$selectRes = Invoke-HttpJson -Method "POST" -Path "/deformers/select" -Body $selectBody
if ($selectRes.StatusCode -ne 200) { throw "/deformers/select failed: $($selectRes.Body)" }
$afterSelect = Invoke-HttpJson -Method "GET" -Path "/deformers/state"
if ($afterSelect.StatusCode -ne 200) { throw "post-select state failed: $($afterSelect.Body)" }
$afterSelectObj = $afterSelect.Body | ConvertFrom-Json
if ($afterSelectObj.active_deformer.id -ne $first.id) {
  throw "post-verify active_deformer mismatch: expected $($first.id), got $($afterSelectObj.active_deformer.id)"
}

# happy path: rename + post-verify + revert
$originalName = $first.name
if ([string]::IsNullOrWhiteSpace($originalName)) { $originalName = $first.id }
$newName = $originalName + "_api"
$renameBody = '{"deformer_id":"' + $first.id + '","new_name":"' + $newName + '"}'
$renameRes = Invoke-HttpJson -Method "POST" -Path "/deformers/rename" -Body $renameBody
if ($renameRes.StatusCode -ne 200) { throw "/deformers/rename failed: $($renameRes.Body)" }
$afterRename = Invoke-HttpJson -Method "GET" -Path "/deformers"
if ($afterRename.StatusCode -ne 200) { throw "post-rename list failed: $($afterRename.Body)" }
$afterRenameObj = $afterRename.Body | ConvertFrom-Json
$renamed = $afterRenameObj.deformers | Where-Object { $_.id -eq $first.id } | Select-Object -First 1
if ($null -eq $renamed -or $renamed.name -ne $newName) {
  throw "post-verify rename failed for $($first.id)"
}

$revertBody = '{"deformer_id":"' + $first.id + '","new_name":"' + $originalName + '"}'
$revertRes = Invoke-HttpJson -Method "POST" -Path "/deformers/rename" -Body $revertBody
if ($revertRes.StatusCode -ne 200) { throw "revert rename failed: $($revertRes.Body)" }

# error path: not found
$nfRes = Invoke-HttpJson -Method "POST" -Path "/deformers/select" -Body '{"deformer_id":"__missing__"}'
if ($nfRes.StatusCode -ne 404) { throw "not_found expected 404, got $($nfRes.StatusCode)" }

# guardrail: wrong method
$guardRes = Invoke-HttpJson -Method "GET" -Path "/deformers/select"
if ($guardRes.StatusCode -ne 405) { throw "GET /deformers/select expected 405, got $($guardRes.StatusCode)" }

Write-Host "/deformers/state => OK (count=$($state.count))" -ForegroundColor Green
Write-Host "/deformers/select happy + post-verify => OK (id=$($first.id))" -ForegroundColor Green
Write-Host "/deformers/rename happy + revert => OK" -ForegroundColor Green
Write-Host "/deformers/select not_found => 404 (expected)" -ForegroundColor Green
Write-Host "GET /deformers/select => 405 (expected)" -ForegroundColor Green

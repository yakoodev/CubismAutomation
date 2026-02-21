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

Write-Host "== Deformer API smoke ==" -ForegroundColor Cyan

$stateRes = Invoke-HttpJson -Method "GET" -Path "/deformers/state"
if ($stateRes.StatusCode -eq 409 -and $stateRes.Body -match "no_document") {
  Write-Host "No document, running startup/prepare..." -ForegroundColor Yellow
  $prepBody = '{"license_mode":"free","create_new_model":true,"wait_timeout_ms":30000}'
  $prepRes = Invoke-HttpJson -Method "POST" -Path "/startup/prepare" -Body $prepBody
  if ($prepRes.StatusCode -ne 200) { throw "startup/prepare failed: $($prepRes.Body)" }
  Start-Sleep -Seconds 2
  $stateRes = Invoke-HttpJson -Method "GET" -Path "/deformers/state"
}

if ($stateRes.StatusCode -ne 200) { throw "/deformers/state failed: $($stateRes.Body)" }
$state = $stateRes.Body | ConvertFrom-Json
if (-not $state.ok) { throw "/deformers/state returned ok=false: $($stateRes.Body)" }
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

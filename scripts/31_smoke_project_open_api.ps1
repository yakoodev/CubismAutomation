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

Write-Host "== Project Open API smoke ==" -ForegroundColor Cyan
powershell -ExecutionPolicy Bypass -File scripts/85_start_cubism.ps1 -BaseUrl $BaseUrl -WaitSec 160

# guardrail/error path: invalid path
$badRes = Invoke-HttpJson -Method "POST" -Path "/project/open" -Body '{"path":"?:\\bad\\path"}'
if (($badRes.StatusCode -ne 400) -and ($badRes.StatusCode -ne 404)) { throw "invalid/not_found expected 400|404, got $($badRes.StatusCode)" }

# error path: not found
$nfRes = Invoke-HttpJson -Method "POST" -Path "/project/open" -Body '{"path":"C:\\__definitely_missing__.cmo3"}'
if ($nfRes.StatusCode -ne 404) { throw "not_found expected 404, got $($nfRes.StatusCode)" }

# happy path
$candidate = Find-OpenCandidate -Dir $ModelDir
if ([string]::IsNullOrWhiteSpace($candidate)) {
  Write-Host "No candidate file found in $ModelDir; happy-path skipped." -ForegroundColor Yellow
} else {
  $payload = '{"path":"' + ($candidate -replace '\\','\\') + '","close_current_first":true}'
  $openRes = Invoke-HttpJson -Method "POST" -Path "/project/open" -Body $payload
  if (($openRes.StatusCode -ne 200) -and ($openRes.StatusCode -ne 599)) {
    throw "project/open happy path failed: $($openRes.StatusCode) $($openRes.Body)"
  }
  if ($openRes.StatusCode -eq 599) {
    Write-Host "project/open timed out at client side, waiting for async completion..." -ForegroundColor Yellow
    Start-Sleep -Seconds 12
  }
  $docRes = Invoke-HttpJson -Method "GET" -Path "/state/document"
  if ($docRes.StatusCode -ne 200) { throw "state/document post-verify failed: $($docRes.Body)" }
  $doc = $docRes.Body | ConvertFrom-Json
  if (-not $doc.ok -or -not $doc.document.present) {
    throw "post-verify document not present after project/open"
  }
  Write-Host "/project/open happy + post-verify => OK ($candidate)" -ForegroundColor Green
}

# guardrail method
$methodRes = Invoke-HttpJson -Method "GET" -Path "/project/open"
if ($methodRes.StatusCode -ne 405) { throw "GET /project/open expected 405, got $($methodRes.StatusCode)" }

Write-Host "/project/open invalid_path => 400 (expected)" -ForegroundColor Green
Write-Host "/project/open not_found => 404 (expected)" -ForegroundColor Green
Write-Host "GET /project/open => 405 (expected)" -ForegroundColor Green

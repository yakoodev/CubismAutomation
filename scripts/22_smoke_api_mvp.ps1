param(
  [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"

function Get-Body([string]$Url) {
  return (Invoke-WebRequest -UseBasicParsing $Url).Content.Trim()
}

function Post-AndCapture([string]$Url, [string]$Body) {
  try {
    $res = Invoke-WebRequest -UseBasicParsing $Url -Method POST -Body $Body -ContentType "application/json"
    return @{ Status = $res.StatusCode; Body = $res.Content.Trim() }
  } catch {
    $resp = $_.Exception.Response
    if ($resp -ne $null) {
      $status = [int]$resp.StatusCode
      $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
      $text = $reader.ReadToEnd().Trim()
      if ([string]::IsNullOrWhiteSpace($text) -and $_.ErrorDetails -and $_.ErrorDetails.Message) {
        $text = $_.ErrorDetails.Message.Trim()
      }
      return @{ Status = $status; Body = $text }
    }
    throw
  }
}

Write-Host "== API MVP smoke ==" -ForegroundColor Cyan
$hello = Get-Body "$BaseUrl/hello"
$health = Get-Body "$BaseUrl/health"
$version = Get-Body "$BaseUrl/version"
$command = Post-AndCapture "$BaseUrl/command" '{"command":"cubism.zoom_in"}'
$commandBad = Post-AndCapture "$BaseUrl/command" '{"command":"unsupported.demo"}'

Write-Host "hello   => $hello" -ForegroundColor Green
Write-Host "health  => $health" -ForegroundColor Green
Write-Host "version => $version" -ForegroundColor Green
Write-Host "command (zoom_in) => HTTP $($command.Status) $($command.Body)" -ForegroundColor Green
Write-Host "command (bad)     => HTTP $($commandBad.Status) $($commandBad.Body)" -ForegroundColor Green

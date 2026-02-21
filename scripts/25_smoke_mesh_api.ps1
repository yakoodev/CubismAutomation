param(
  [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"

function Invoke-Capture([string]$Method, [string]$Path, [string]$Body = $null) {
  try {
    $params = @{
      UseBasicParsing = $true
      Uri = ($BaseUrl + $Path)
      Method = $Method
    }
    if ($Body -ne $null -and $Method -ne "GET") {
      $params["Body"] = $Body
      $params["ContentType"] = "application/json"
    }
    $res = Invoke-WebRequest @params
    return @{ Status = $res.StatusCode; Body = $res.Content.Trim() }
  } catch {
    $resp = $_.Exception.Response
    if ($resp -eq $null) { throw }
    $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
    $text = $reader.ReadToEnd().Trim()
    return @{ Status = [int]$resp.StatusCode; Body = $text }
  }
}

function Assert-AgentUp {
  try {
    $null = Invoke-WebRequest -UseBasicParsing -Uri ($BaseUrl + "/health") -Method GET -TimeoutSec 5
  } catch {
    throw "Agent is not reachable at $BaseUrl. Start Cubism with cubism-agent-server.jar first."
  }
}

Assert-AgentUp

$cases = @(
  @{ Name = "GET /mesh/list";              Method = "GET";  Path = "/mesh/list" },
  @{ Name = "GET /mesh/active";            Method = "GET";  Path = "/mesh/active" },
  @{ Name = "GET /mesh/state";             Method = "GET";  Path = "/mesh/state" },
  @{ Name = "POST /mesh/list method";      Method = "POST"; Path = "/mesh/list"; Body = "{}" },
  @{ Name = "POST /mesh/select invalid";   Method = "POST"; Path = "/mesh/select"; Body = "{}" },
  @{ Name = "POST /mesh/rename invalid";   Method = "POST"; Path = "/mesh/rename"; Body = '{"new_name":""}' },
  @{ Name = "POST /mesh/visibility invalid"; Method = "POST"; Path = "/mesh/visibility"; Body = '{}' },
  @{ Name = "POST /mesh/lock invalid";     Method = "POST"; Path = "/mesh/lock"; Body = '{}' },
  @{ Name = "POST /mesh/ops invalid";      Method = "POST"; Path = "/mesh/ops"; Body = '{"validate_only":true}' },
  @{ Name = "POST /mesh/ops dry-run";      Method = "POST"; Path = "/mesh/ops"; Body = '{"validate_only":true,"operations":[{"op":"auto_mesh"},{"op":"divide"},{"op":"connect"},{"op":"reset_shape"},{"op":"fit_contour"}]}' },
  @{ Name = "GET /mesh/points";            Method = "GET";  Path = "/mesh/points" },
  @{ Name = "POST /mesh/points invalid";   Method = "POST"; Path = "/mesh/points"; Body = '{}' },
  @{ Name = "POST /mesh/auto_generate dry"; Method = "POST"; Path = "/mesh/auto_generate"; Body = '{"validate_only":true}' },
  @{ Name = "GET /mesh/screenshot";        Method = "GET";  Path = "/mesh/screenshot" },
  @{ Name = "POST /mesh/select unknown";   Method = "POST"; Path = "/mesh/select"; Body = '{"mesh_name":"__definitely_missing__"}' },
  @{ Name = "POST /mesh/rename unknown";   Method = "POST"; Path = "/mesh/rename"; Body = '{"mesh_name":"__definitely_missing__","new_name":"test_name"}' }
)

Write-Host "== Mesh API smoke ==" -ForegroundColor Cyan
foreach ($case in $cases) {
  $result = Invoke-Capture $case.Method $case.Path $case.Body
  Write-Host ("{0} => HTTP {1} {2}" -f $case.Name, $result.Status, $result.Body) -ForegroundColor Green
}

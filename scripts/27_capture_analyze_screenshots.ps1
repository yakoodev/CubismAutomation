param(
  [string]$BaseUrl = "http://127.0.0.1:18080",
  [string]$MeshId = "ArtMesh78",
  [int]$Count = 5,
  [int]$IntervalMs = 700,
  [bool]$WorkspaceOnly = $true,
  [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"

function Assert-AgentUp {
  try {
    $null = Invoke-WebRequest -UseBasicParsing -Uri ($BaseUrl + "/health") -Method GET -TimeoutSec 5
  } catch {
    throw "Agent is not reachable at $BaseUrl. Start Cubism with cubism-agent-server.jar first."
  }
}

function Ensure-SystemDrawing {
  Add-Type -AssemblyName System.Drawing | Out-Null
}

function Get-PngSignatureHex([byte[]]$bytes) {
  if ($bytes.Length -lt 8) {
    return ""
  }
  return (($bytes[0..7] | ForEach-Object { "{0:X2}" -f $_ }) -join " ")
}

function Get-Sha256Hex([string]$path) {
  $hash = Get-FileHash -Path $path -Algorithm SHA256
  return $hash.Hash.ToLowerInvariant()
}

function Get-ImageInfo([string]$path) {
  $img = [System.Drawing.Image]::FromFile($path)
  try {
    return @{
      Width = $img.Width
      Height = $img.Height
    }
  } finally {
    $img.Dispose()
  }
}

function Get-AHashHex([string]$path) {
  $bmp = New-Object System.Drawing.Bitmap($path)
  $small = New-Object System.Drawing.Bitmap(16, 16)
  $g = [System.Drawing.Graphics]::FromImage($small)
  try {
    $g.DrawImage($bmp, 0, 0, 16, 16)
  } finally {
    $g.Dispose()
    $bmp.Dispose()
  }

  $vals = New-Object 'System.Collections.Generic.List[double]'
  for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
      $c = $small.GetPixel($x, $y)
      $gray = (0.299 * $c.R) + (0.587 * $c.G) + (0.114 * $c.B)
      $vals.Add($gray) | Out-Null
    }
  }

  $small.Dispose()

  $mean = ($vals | Measure-Object -Average).Average
  $bits = New-Object System.Text.StringBuilder
  foreach ($v in $vals) {
    if ($v -ge $mean) {
      [void]$bits.Append("1")
    } else {
      [void]$bits.Append("0")
    }
  }

  $bin = $bits.ToString()
  $hex = New-Object System.Text.StringBuilder
  for ($i = 0; $i -lt $bin.Length; $i += 4) {
    $chunk = $bin.Substring($i, 4)
    $value = [Convert]::ToInt32($chunk, 2)
    [void]$hex.Append($value.ToString("x"))
  }
  return $hex.ToString()
}

function Get-HammingDistance([string]$hexA, [string]$hexB) {
  if ([string]::IsNullOrWhiteSpace($hexA) -or [string]::IsNullOrWhiteSpace($hexB)) {
    return $null
  }
  if ($hexA.Length -ne $hexB.Length) {
    return $null
  }

  $dist = 0
  for ($i = 0; $i -lt $hexA.Length; $i++) {
    $a = [Convert]::ToInt32($hexA.Substring($i, 1), 16)
    $b = [Convert]::ToInt32($hexB.Substring($i, 1), 16)
    $x = $a -bxor $b
    while ($x -ne 0) {
      $dist += ($x -band 1)
      $x = $x -shr 1
    }
  }
  return $dist
}

function Capture-One([int]$index, [string]$runDir) {
  $url = "$BaseUrl/screenshot/current?mesh_id=$([uri]::EscapeDataString($MeshId))&workspace_only=$($WorkspaceOnly.ToString().ToLowerInvariant())"
  $fileName = ("shot-{0:D3}.png" -f $index)
  $path = Join-Path $runDir $fileName

  try {
    Invoke-WebRequest -UseBasicParsing -Uri $url -Method GET -OutFile $path -TimeoutSec 20 | Out-Null
  } catch {
    return @{
      Index = $index
      Path = $path
      Ok = $false
      Error = $_.Exception.Message
    }
  }

  $bytes = [System.IO.File]::ReadAllBytes($path)
  $sig = Get-PngSignatureHex $bytes
  $info = Get-ImageInfo $path
  $sha = Get-Sha256Hex $path
  $ahash = Get-AHashHex $path

  return @{
    Index = $index
    Path = $path
    Ok = $true
    SizeBytes = $bytes.Length
    PngSig = $sig
    IsPngSignatureValid = ($sig -eq "89 50 4E 47 0D 0A 1A 0A")
    Width = $info.Width
    Height = $info.Height
    Sha256 = $sha
    AHash = $ahash
    Error = $null
  }
}

function Write-MarkdownReport([string]$path, [object[]]$items, [object]$summary) {
  $lines = New-Object 'System.Collections.Generic.List[string]'
  $lines.Add("# Screenshot Capture Report")
  $lines.Add("")
  $lines.Add(("- base_url: {0}" -f $summary.BaseUrl))
  $lines.Add(("- mesh_id: {0}" -f $summary.MeshId))
  $lines.Add(("- workspace_only: {0}" -f $summary.WorkspaceOnly))
  $lines.Add(("- captured: {0}" -f $summary.Captured))
  $lines.Add(("- failed: {0}" -f $summary.Failed))
  $lines.Add(("- png_valid: {0}" -f $summary.PngValid))
  $lines.Add(("- min_hamming: {0}" -f $summary.MinHamming))
  $lines.Add(("- max_hamming: {0}" -f $summary.MaxHamming))
  $lines.Add("")
  $lines.Add("| index | ok | width | height | size_bytes | png_sig_ok | hamming_vs_prev | file |")
  $lines.Add("|---:|:---:|---:|---:|---:|:---:|---:|---|")

  foreach ($it in $items) {
    $ham = if ($null -eq $it.HammingVsPrev) { "" } else { [string]$it.HammingVsPrev }
    $ok = if ($it.Ok) { "yes" } else { "no" }
    $sig = if ($it.IsPngSignatureValid) { "yes" } else { "no" }
    $w = if ($it.Width) { [string]$it.Width } else { "" }
    $h = if ($it.Height) { [string]$it.Height } else { "" }
    $sz = if ($it.SizeBytes) { [string]$it.SizeBytes } else { "" }
    $file = [System.IO.Path]::GetFileName($it.Path)
    $lines.Add("| $($it.Index) | $ok | $w | $h | $sz | $sig | $ham | `$file` |")
  }

  Set-Content -Path $path -Value $lines -Encoding UTF8
}

Assert-AgentUp
Ensure-SystemDrawing

if ($Count -lt 1) { throw "Count must be >= 1" }
if ($IntervalMs -lt 0) { throw "IntervalMs must be >= 0" }

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
  $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $OutputDir = Join-Path (Join-Path $PSScriptRoot "..\\temp\\screenshot-runs") ("run-" + $stamp)
}

$runDir = (Resolve-Path (New-Item -ItemType Directory -Force -Path $OutputDir)).Path
$items = New-Object 'System.Collections.Generic.List[hashtable]'

Write-Host "== Screenshot capture + analysis ==" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl" -ForegroundColor Yellow
Write-Host "MeshId: $MeshId" -ForegroundColor Yellow
Write-Host "WorkspaceOnly: $WorkspaceOnly" -ForegroundColor Yellow
Write-Host "OutputDir: $runDir" -ForegroundColor Yellow

for ($i = 1; $i -le $Count; $i++) {
  $shot = Capture-One -index $i -runDir $runDir
  $items.Add($shot) | Out-Null
  if ($shot.Ok) {
    Write-Host ("capture {0}/{1}: OK  {2}x{3}  {4} bytes" -f $i, $Count, $shot.Width, $shot.Height, $shot.SizeBytes) -ForegroundColor Green
  } else {
    Write-Host ("capture {0}/{1}: FAIL {2}" -f $i, $Count, $shot.Error) -ForegroundColor Red
  }
  if ($i -lt $Count -and $IntervalMs -gt 0) {
    Start-Sleep -Milliseconds $IntervalMs
  }
}

# Derive cross-frame metrics.
for ($i = 0; $i -lt $items.Count; $i++) {
  if ($i -eq 0) {
    $items[$i]["HammingVsPrev"] = $null
    continue
  }
  $prev = $items[$i - 1]
  $curr = $items[$i]
  if ($prev.Ok -and $curr.Ok) {
    $items[$i]["HammingVsPrev"] = Get-HammingDistance $prev.AHash $curr.AHash
  } else {
    $items[$i]["HammingVsPrev"] = $null
  }
}

$okItems = @($items | Where-Object { $_.Ok })
$hamVals = @($items | Where-Object { $null -ne $_.HammingVsPrev } | ForEach-Object { [int]$_.HammingVsPrev })

$summary = @{
  BaseUrl = $BaseUrl
  MeshId = $MeshId
  WorkspaceOnly = $WorkspaceOnly
  Captured = $okItems.Count
  Failed = ($items.Count - $okItems.Count)
  PngValid = (@($okItems | Where-Object { $_.IsPngSignatureValid }).Count)
  MinHamming = (if ($hamVals.Count -gt 0) { ($hamVals | Measure-Object -Minimum).Minimum } else { $null })
  MaxHamming = (if ($hamVals.Count -gt 0) { ($hamVals | Measure-Object -Maximum).Maximum } else { $null })
}

$jsonPath = Join-Path $runDir "report.json"
$mdPath = Join-Path $runDir "report.md"
($items | ConvertTo-Json -Depth 8) | Set-Content -Path (Join-Path $runDir "captures.json") -Encoding UTF8
(@{ Summary = $summary; Captures = $items } | ConvertTo-Json -Depth 10) | Set-Content -Path $jsonPath -Encoding UTF8
Write-MarkdownReport -path $mdPath -items $items -summary $summary

Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host ("- Captured: {0}" -f $summary.Captured) -ForegroundColor Green
Write-Host ("- Failed: {0}" -f $summary.Failed) -ForegroundColor Green
Write-Host ("- PNG valid: {0}" -f $summary.PngValid) -ForegroundColor Green
Write-Host ("- Hamming min/max: {0}/{1}" -f $summary.MinHamming, $summary.MaxHamming) -ForegroundColor Green
Write-Host ("- JSON report: {0}" -f $jsonPath) -ForegroundColor Yellow
Write-Host ("- Markdown report: {0}" -f $mdPath) -ForegroundColor Yellow

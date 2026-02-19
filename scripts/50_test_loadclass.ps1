param(
  [Parameter(Mandatory=$true)][string]$JarPath,
  [Parameter(Mandatory=$true)][string]$ClassName
)

$ErrorActionPreference = "Stop"
$JarPath = (Resolve-Path $JarPath).Path

$work = Join-Path $PSScriptRoot "..\work\testrunner"
if (Test-Path $work) { Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $work | Out-Null

$javaFile = Join-Path $work "TestRunner.java"
@"
public class TestRunner {
  public static void main(String[] args) throws Exception {
    Class.forName("$ClassName");
    System.out.println("Loaded: $ClassName");
  }
}
"@ | Out-File -Encoding ascii $javaFile

Push-Location $work
javac TestRunner.java
java -cp ".;$JarPath" TestRunner
Pop-Location

$desktop = Join-Path $env:USERPROFILE "Desktop\cubism_patched_ok.txt"
if (Test-Path $desktop) {
  Write-Host "OK: file exists => $desktop" -ForegroundColor Green
} else {
  Write-Host "NOT FOUND: $desktop (maybe class not loaded / patch didn't run)" -ForegroundColor Yellow
}

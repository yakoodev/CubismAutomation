param(
  [string]$Version = ("v" + (Get-Date -Format "yyyy.MM.dd-HHmm")),
  [string]$InputJar = "input/Live2D_Cubism.jar",
  [string]$ReleaseRoot = "output/release"
)

$ErrorActionPreference = "Stop"

if (!(Test-Path $InputJar)) {
  throw "Input jar not found: $InputJar"
}

$releaseDir = Join-Path $ReleaseRoot $Version
$buildDir = "work/build"
$jarPatchDir = "work/jarpatch"

New-Item -ItemType Directory -Force $releaseDir | Out-Null
New-Item -ItemType Directory -Force "$buildDir/patcher" | Out-Null
New-Item -ItemType Directory -Force "$buildDir/runtime" | Out-Null
New-Item -ItemType Directory -Force "$jarPatchDir/com/live2d/cubism/patch" | Out-Null

Write-Host "== Build release: $Version ==" -ForegroundColor Cyan

# 1) Build server jar
powershell -ExecutionPolicy Bypass -File scripts/21_build_server_jar.ps1
Copy-Item -Force output/cubism-agent-server.jar (Join-Path $releaseDir "cubism-agent-server.jar")

# 2) Build patched cubism jar
javac -d "$buildDir/patcher" work/patcher/PatchMethodStartBootstrap.java
javac --release 17 -d "$buildDir/runtime" work/patcher/CubismBootstrap.java

$patchedJar = Join-Path $releaseDir "Live2D_Cubism_patched.jar"
Copy-Item -Force $InputJar $patchedJar

Push-Location $jarPatchDir
jar xf "..\..\$patchedJar" com/live2d/cubism/CECubismEditorApp.class
Pop-Location

java -cp "$buildDir/patcher" PatchMethodStartBootstrap "$jarPatchDir/com/live2d/cubism/CECubismEditorApp.class" main "([Ljava/lang/String;)V"
Copy-Item -Force "$buildDir/runtime/com/live2d/cubism/patch/CubismBootstrap.class" "$jarPatchDir/com/live2d/cubism/patch/CubismBootstrap.class"

Push-Location $jarPatchDir
jar uf "..\..\$patchedJar" com/live2d/cubism/CECubismEditorApp.class com/live2d/cubism/patch/CubismBootstrap.class
Pop-Location

Start-Sleep -Milliseconds 600
& "C:\Program Files\7-Zip\7z.exe" d $patchedJar META-INF/*.SF META-INF/*.RSA META-INF/*.DSA -y | Out-Null

# 3) Release metadata + checksums
$shaPatched = (Get-FileHash $patchedJar -Algorithm SHA256).Hash.ToLower()
$shaServer = (Get-FileHash (Join-Path $releaseDir "cubism-agent-server.jar") -Algorithm SHA256).Hash.ToLower()

@"
version=$Version
built_at=$(Get-Date -Format s)
live2d_patched_jar=Live2D_Cubism_patched.jar
live2d_patched_sha256=$shaPatched
agent_server_jar=cubism-agent-server.jar
agent_server_sha256=$shaServer
"@ | Set-Content -Encoding ascii (Join-Path $releaseDir "release-manifest.txt")

@"
$shaPatched *Live2D_Cubism_patched.jar
$shaServer *cubism-agent-server.jar
"@ | Set-Content -Encoding ascii (Join-Path $releaseDir "SHA256SUMS.txt")

Write-Host "Release ready: $releaseDir" -ForegroundColor Green

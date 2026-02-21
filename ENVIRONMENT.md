# Environment And Dependencies

## OS
- Windows 10/11 x64

## Required tools
- JDK in PATH: `java`, `javac`, `jar`, `javap`, `jarsigner`, `keytool`
- 7-Zip: `C:\Program Files\7-Zip\7z.exe`
- PowerShell 5+

## Project inputs
- Source jar: `input/Live2D_Cubism.jar`

## Optional tools
- CFR: `tools/cfr/cfr.jar` (for decompilation/inspection)
- .NET SDK 8+ for C# web console (`tools/cubism-api-console`)

## Cubism runtime
- Tested target: `C:\Users\Yakoo\source\Live2D Cubism 5.3\CubismEditor5.exe`
- Default Cubism dir for install/rollback scripts: `C:\Users\Yakoo\source\Live2D Cubism 5.3`
- Test model for mesh/API validation: `C:\Users\Yakoo\Downloads\vt\hibiki`

## Runtime env vars (agent)
- `CUBISM_AGENT_HOST` (default `127.0.0.1`)
- `CUBISM_AGENT_PORT` (default `18080`)
- `CUBISM_AGENT_AUTH_MODE` (default `off`)
- `CUBISM_AGENT_TOKEN` (used if auth mode is `required`)
- `CUBISM_AGENT_ALLOW_COMMANDS` (CSV allowlist)
- `CUBISM_AGENT_DENY_COMMANDS` (CSV denylist)
- `CUBISM_AGENT_SERVER_JAR` (optional absolute/relative override for loader)

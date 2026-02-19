# Installation Guide

## 1) Build release artifacts
```powershell
powershell -ExecutionPolicy Bypass -File scripts/80_build_release.ps1 -Version v0.8.0
powershell -ExecutionPolicy Bypass -File scripts/81_verify_release.ps1 -ReleaseDir output/release/v0.8.0
```

## 2) Install `cubism-agent-server.jar` into Cubism
Run PowerShell as Administrator:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/82_install_server_jar.ps1 -ReleaseDir output/release/v0.8.0
```

This modifies only:
- `C:\Program Files\Live2D Cubism 5.3\cubism-agent-server.jar`

## 3) Ensure patched Cubism jar is used
Use the built:
- `output/release/v0.8.0/Live2D_Cubism_patched.jar`

## 4) Run Cubism and check API
- `GET http://127.0.0.1:18080/health`
- `GET http://127.0.0.1:18080/version`
- `POST http://127.0.0.1:18080/startup/prepare`
  with body:
```json
{"license_mode":"free","create_new_model":true,"wait_timeout_ms":30000}
```

## 5) Optional: C# API Console
```powershell
cd tools/cubism-api-console
dotnet run -c Release
```
Open:
- `http://127.0.0.1:51888`

## Rollback
```powershell
powershell -ExecutionPolicy Bypass -File scripts/83_rollback_server_jar.ps1
```

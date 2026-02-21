# C# Web Console For Cubism API

Path: `tools/cubism-api-console`

## Purpose
Local web UI for manual testing of Cubism Agent API.

## Run
```powershell
cd tools/cubism-api-console
dotnet run -c Release
```

Open:
- `http://127.0.0.1:51888`

## Configure target API
Edit `tools/cubism-api-console/appsettings.json`:
- `CubismApi.BaseUrl` (default `http://127.0.0.1:18080`)
- `CubismApi.Token` (optional; empty by default)
- `CubismApi.TimeoutSeconds` (request timeout)

## UI actions
- GET checks: `/health`, `/version`, `/state*`
- startup automation: `POST /startup/prepare`
- command buttons: `zoom_in`, `zoom_out`, `zoom_reset`, `undo`, `redo`
- custom POST form for arbitrary endpoint/body
- token override field in UI, without restart

# C# Web Console For Cubism API

Path: `tools/cubism-api-console`

## Purpose
Локальный web UI с кнопками для ручной проверки Cubism Agent API.

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
- `CubismApi.Token` (Bearer token)

## UI actions
- GET checks: `/health`, `/version`, `/state*`
- command buttons: `zoom_in`, `zoom_out`, `zoom_reset`, `undo`, `redo`
- custom POST form for arbitrary endpoint/body

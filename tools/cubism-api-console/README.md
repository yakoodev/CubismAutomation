# Cubism API Console

Minimal C# web UI for manual Cubism Agent API checks.

## Run
```powershell
cd tools/cubism-api-console
dotnet run -c Release
```

UI URL:
- `http://127.0.0.1:51888`

## Config
`appsettings.json`:
- `CubismApi.BaseUrl` - Cubism Agent API base URL
- `CubismApi.Token` - default Bearer token (optional)
- `CubismApi.LogFilePath` - path for persisted request/response logs (default: `logs/api-calls.log`)

## API Call Logs
- Every `/api/call` request is saved with `call_id`, request payload and response payload.
- Default file: `tools/cubism-api-console/logs/api-calls.log`
- `call_id` is returned by `/api/call` so you can reference exact failing mesh calls.

## Included Presets
- State/health endpoints
- Startup flow endpoint
- Command shortcuts
- Mesh read endpoints
- Mesh write endpoints
- Mesh edit operations (`/mesh/ops`, dry-run and execute)

## Extending Tests
Presets are now registry-driven:
- edit `ApiCatalog.Default()` in `Program.cs`
- add a new `ApiAction` entry (label, method, path, optional body)
- restart `dotnet run`

You do not need to edit HTML/JS when adding a new preset action.

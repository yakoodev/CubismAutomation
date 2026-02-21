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
- Mesh points (`/mesh/points` read/write)
- Mesh auto generation shortcut (`/mesh/auto_generate`)
- Mesh screenshot (`/mesh/screenshot`)

## Mesh Lab (new)
Built-in interactive panel for mesh-focused testing:
- Refresh and inspect all meshes from `/mesh/list`
- Load active mesh from `/mesh/active`
- Select mesh by `mesh_id`
- Active-first mode (`use active mesh for ops`) for stable workflow:
  list -> set active -> get points -> edit -> apply
- Read mesh points (`GET /mesh/points`)
- Edit points JSON and apply (`POST /mesh/points`)
- Nudge a single point by `index/dx/dy`
- Revert to initially loaded point snapshot
- Run auto-mesh dry-run or execute
- Capture screenshot via streaming route (`GET /screenshot/current`) with preview in UI
- `workspace only` option to capture editor area instead of full app window

This is designed for fast manual validation of `110_mesh-api-mvp` and `120_mesh-api-edit-operations` flows.

## Safety Note
- Topology mutation (adding/removing mesh points) is disabled by default because it can corrupt mesh internals in Cubism 5.3 and cause runtime errors.
- Safe workflow is limited to moving existing points via full `POST /mesh/points` payload.

## Extending Tests
Presets are now registry-driven:
- edit `ApiCatalog.Default()` in `Program.cs`
- add a new `ApiAction` entry (label, method, path, optional body)
- restart `dotnet run`

You do not need to edit HTML/JS when adding a new preset action.

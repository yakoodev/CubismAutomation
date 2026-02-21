# CubismAutomation

Automation stack for Live2D Cubism:
- patched Cubism jar (thin bootstrap),
- external `cubism-agent-server.jar` with local HTTP API,
- release scripts (build/verify/install/rollback),
- optional C# web console for manual API testing.

Repository root (current):
- `C:\Users\Yakoo\source\sandbox cubism`

## Quick Start
1. Build release:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/80_build_release.ps1 -Version v0.8.0
powershell -ExecutionPolicy Bypass -File scripts/81_verify_release.ps1 -ReleaseDir output/release/v0.8.0
```
2. Install server jar to Cubism (Admin PowerShell):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/82_install_server_jar.ps1 -ReleaseDir output/release/v0.8.0
```
3. Use patched jar:
- `output/release/v0.8.0/Live2D_Cubism_patched.jar`

## API
- Health: `/health`
- Version: `/version`
- Commands: `/command`
- State: `/state`, `/state/project`, `/state/document`, `/state/selection`
- Mesh (read): `GET /mesh/list`, `GET /mesh/active`, `GET /mesh/state`
- Mesh (write): `POST /mesh/select`, `POST /mesh/rename`, `POST /mesh/visibility`, `POST /mesh/lock`
- Mesh edit ops: `POST /mesh/ops` (`validate_only=true|false`)
- Mesh points: `GET /mesh/points`, `POST /mesh/points`
- Mesh auto op: `POST /mesh/auto_generate`
- Mesh capture: `GET/POST /mesh/screenshot`
- Live screenshot stream: `GET /screenshot/current`
- Startup automation: `POST /startup/prepare`

`/startup/prepare` flow (current):
1. Wait until Cubism `CEAppCtrl` is ready.
2. Handle license dialog (`free`/`pro`) with Swing-button click, fallback to keyboard navigation.
3. Handle post-license modal dialog (for example `OK`/`Continue`).
4. Close `Home` window if it appears.
5. Create new model:
   - try startup dialog `New`,
   - verify document is actually created (`getCurrentDoc`),
   - fallback to API `command_newModel`,
   - fallback to global keyboard `Ctrl+N` + `Enter`,
   - verify again and return step statuses in JSON.

Default auth mode: `off`.
Set `CUBISM_AGENT_AUTH_MODE=required` and `CUBISM_AGENT_TOKEN=...` to enable token auth.

API logging:
- Server writes per-request API logs to `CUBISM_AGENT_LOG_FILE`.
- Default server log file: `%USERPROFILE%\\cubism-agent-api.log`.

## Manual UI (C#)
```powershell
cd tools/cubism-api-console
dotnet run -c Release
```
Open:
- `http://127.0.0.1:51888`

Stop Cubism from repo script:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/84_stop_cubism.ps1
```

## Screenshot Capture And Analysis
Use automated capture loop + image analysis while developing features:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/27_capture_analyze_screenshots.ps1 -MeshId ArtMesh78 -Count 5 -IntervalMs 700
```
Outputs:
- PNG frames in `temp/screenshot-runs/run-<timestamp>/`
- `captures.json` (raw frame metrics)
- `report.json` (summary + frames)
- `report.md` (human-readable report)

Practical runbook:
1. Ensure Cubism is running and model is loaded.
2. If API returns `{"error":"no_document"}`, call:
   `POST /startup/prepare` with `{"license_mode":"free","create_new_model":true,"wait_timeout_ms":30000}`
3. Then call screenshot endpoint again (`/screenshot/current` or `/mesh/screenshot`).

## Documentation
- `INSTALL.md`
- `ENVIRONMENT.md`
- `HOW_IT_WORKS.md`
- `RELEASE_PLAYBOOK.md`
- `docs/api-mvp.md`
- `docs/state-read-api.md`
- `docs/security-config.md`
- `docs/mesh-api.md`

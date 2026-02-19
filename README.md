# CubismAutomation

Automation stack for Live2D Cubism:
- patched Cubism jar (thin bootstrap),
- external `cubism-agent-server.jar` with local HTTP API,
- release scripts (build/verify/install/rollback),
- optional C# web console for manual API testing.

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

## Manual UI (C#)
```powershell
cd tools/cubism-api-console
dotnet run -c Release
```
Open:
- `http://127.0.0.1:51888`

## Documentation
- `INSTALL.md`
- `ENVIRONMENT.md`
- `HOW_IT_WORKS.md`
- `RELEASE_PLAYBOOK.md`
- `output/api-mvp.md`
- `output/state-read-api.md`
- `output/security-config.md`

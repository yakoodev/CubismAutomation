# 150_project-window-introspection

## Status
Done.

## Implemented
- Added endpoint `GET /state/ui`.
- Extended `GET /state` with section `ui`.
- Added UI snapshot fields:
  - focused/active/capture window class/title,
  - capture/workspace bounds,
  - `documentPresent`,
  - `showingWindowsCount`.
- Added API route in server bootstrap: `/state/ui`.
- Updated C# API console preset catalog with `GET /state/ui`.

## Validation
- Build: `powershell -ExecutionPolicy Bypass -File scripts/21_build_server_jar.ps1` => OK.
- Build: `dotnet build tools/cubism-api-console/CubismApiConsole.csproj -c Release` => OK.
- Smoke: `powershell -ExecutionPolicy Bypass -File scripts/23_smoke_state_api.ps1`:
  - happy path: `GET /state/ui` => `ok:true`,
  - error path: `POST /state/ui` => `405`,
  - guardrail: `ui.documentPresent` present when document is absent,
  - post-verify: `GET /state` contains `ui`.
- Runtime runbook verified:
  - `POST /startup/prepare` with `{"license_mode":"free","create_new_model":true,"wait_timeout_ms":30000}` => `ok:true`,
  - post-check: `GET /state/ui` showed `documentPresent:true`.

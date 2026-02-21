# 240_jobs-admin-and-retention-api

## Status
Done.

## Implemented
- Added `DELETE /jobs/{id}` for terminal job deletion.
  - Active jobs return `job_not_terminal`.
  - Missing/already deleted jobs return `no_effect`.
- Added `POST /jobs/cleanup` for bulk cleanup with optional filters:
  - `status`
  - `before_ms`
  - `limit`
- Extended `GET /jobs` with query filters:
  - `status`
  - `idempotency_key`
  - `limit`
- Updated console catalog presets for new Jobs admin operations.
- Added smoke validation:
  - `scripts/34_smoke_jobs_admin_api.ps1`
- Updated legacy jobs smoke (`scripts/32_smoke_jobs_api.ps1`) to use unique idempotency key per run.

## Validation
- Build: `powershell -ExecutionPolicy Bypass -File scripts/21_build_server_jar.ps1` => OK.
- Build: `dotnet build tools/cubism-api-console/CubismApiConsole.csproj -c Release` => OK.
- Deploy:
  - `powershell -ExecutionPolicy Bypass -File scripts/82_install_server_jar.ps1 -ReleaseDir output` => OK.
  - copied jar to `C:\Users\Yakoo\source\Live2D Cubism 5.3\app\lib\cubism-agent-server.jar` => OK.
- Load test:
  - `powershell -ExecutionPolicy Bypass -File scripts/50_test_loadclass.ps1 -JarPath output/cubism-agent-server.jar -ClassName com.live2d.cubism.agent.ServerBootstrap` => Loaded OK.
- Runtime smoke:
  - `powershell -ExecutionPolicy Bypass -File scripts/34_smoke_jobs_admin_api.ps1` => PASS.
  - `powershell -ExecutionPolicy Bypass -File scripts/32_smoke_jobs_api.ps1` => PASS (after idempotency key fix).

# 170_api-job-runner-and-idempotency

## Status
Done.

## Implemented
- Added in-memory job runner: `CubismJobRunner`.
- Added endpoints:
  - `POST /jobs`
  - `GET /jobs`
  - `GET /jobs/{id}`
  - `POST /jobs/{id}/cancel`
- Added idempotency contract:
  - request header `Idempotency-Key`
  - repeat call returns existing job (`idempotent_reused=true`).
- Added job states:
  - `queued`, `running`, `done`, `failed`, `canceled`
- Added actions:
  - `noop`
  - `sleep` (`sleep_ms`)
  - `project_open` (delegates to project open adapter)
- Added docs and smoke:
  - `docs/jobs-api.md`
  - `scripts/32_smoke_jobs_api.ps1`

## Validation
- Build: `powershell -ExecutionPolicy Bypass -File scripts/21_build_server_jar.ps1` => OK.
- Build: `dotnet build tools/cubism-api-console/CubismApiConsole.csproj -c Release` => OK.
- Runtime smoke:
  - `powershell -ExecutionPolicy Bypass -File scripts/32_smoke_jobs_api.ps1`
  - happy path: create+poll `noop` => `done`
  - idempotency: repeated `Idempotency-Key` returns same `job.id`
  - cancel path: `sleep` job canceled => terminal `canceled`
  - guardrail: `GET /jobs/{id}/cancel` => `405`.

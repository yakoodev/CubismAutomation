# 180_api-observability-and-error-catalog

## Status
Done.

## Implemented
- Added endpoint `GET /metrics` in `ServerBootstrap`.
- Added counters:
  - total requests,
  - status buckets (`2xx/4xx/5xx`),
  - per-path usage,
  - per-command usage (`/command` payloads).
- Added `ERRORS.md` with stable API error codes.
- Added observability guidance: `docs/observability.md`.
- Added smoke script: `scripts/30_smoke_metrics_api.ps1`.
- Added Metrics action to `tools/cubism-api-console` catalog.

## Validation
- Build: `powershell -ExecutionPolicy Bypass -File scripts/21_build_server_jar.ps1` => OK.
- Build: `dotnet build tools/cubism-api-console/CubismApiConsole.csproj -c Release` => OK.
- Runtime smoke:
  - `powershell -ExecutionPolicy Bypass -File scripts/30_smoke_metrics_api.ps1`
  - happy path: `GET /metrics` => 200 and schema present
  - post-verify: `requests.total` increases after synthetic calls
  - guardrail/error path: `POST /metrics` => 405.

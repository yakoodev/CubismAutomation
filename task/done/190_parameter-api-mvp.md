# 190_parameter-api-mvp

## Status
Done.

## Implemented
- Added endpoints:
  - `GET /parameters`
  - `GET /parameters/state`
  - `POST /parameters/set`
- Added `CubismParameterAdapter`:
  - reads parameter catalog from `CModelSource.getParameterSourceSet().getSources()`,
  - reads current values from model instance `CParameterSet`,
  - supports single and batch set requests.
- Added write validation and guardrails:
  - range validation (`out_of_range`),
  - non-modeling document rejection (`guardrail_violation`),
  - read-after-write verification with `no_effect`.
- Added API console presets for Parameter API.
- Added smoke script: `scripts/28_smoke_parameters_api.ps1`.

## Validation
- Build: `powershell -ExecutionPolicy Bypass -File scripts/21_build_server_jar.ps1` => OK.
- Build: `dotnet build tools/cubism-api-console/CubismApiConsole.csproj -c Release` => OK.
- Runtime smoke:
  - `powershell -ExecutionPolicy Bypass -File scripts/28_smoke_parameters_api.ps1`
  - happy path: `/parameters/state` + `/parameters/set` => OK
  - error path: out_of_range => 400
  - guardrail: `GET /parameters/set` => 405
  - post-verify: value present after write.

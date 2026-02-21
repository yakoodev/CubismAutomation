# 230_jobs-persistence-and-recovery

## Status
Done.

## Implemented
- Added disk persistence for Jobs API snapshot (default `temp/jobs-store.tsv`).
- Added configurable storage/runtime knobs:
  - `-Dcubism.agent.jobs.store=<path>`
  - `-Dcubism.agent.jobs.ttl.ms=<milliseconds>`
  - `-Dcubism.agent.jobs.max=<count>`
- Added startup recovery in `CubismJobRunner.initialize()`:
  - loads persisted jobs/idempotency mapping
  - reconciles `queued`/`running` to `failed` with `recovered_interrupted`
  - restores sequence continuity for job id generator
- Added TTL and max-count cleanup policy for terminal jobs.
- Added recovery smoke test:
  - `scripts/33_smoke_jobs_recovery.ps1`

## Validation
- Build: `powershell -ExecutionPolicy Bypass -File scripts/21_build_server_jar.ps1` => OK.
- Build: `dotnet build tools/cubism-api-console/CubismApiConsole.csproj -c Release` => OK.
- Deploy:
  - `powershell -ExecutionPolicy Bypass -File scripts/82_install_server_jar.ps1 -ReleaseDir output` => OK.
  - copied jar to `C:\Users\Yakoo\source\Live2D Cubism 5.3\app\lib\cubism-agent-server.jar` => OK.
- Load test:
  - `powershell -ExecutionPolicy Bypass -File scripts/50_test_loadclass.ps1 -JarPath output/cubism-agent-server.jar -ClassName com.live2d.cubism.agent.ServerBootstrap` => Loaded OK.
- Runtime smoke:
  - `powershell -ExecutionPolicy Bypass -File scripts/32_smoke_jobs_api.ps1` => PASS.
  - `powershell -ExecutionPolicy Bypass -File scripts/33_smoke_jobs_recovery.ps1` => PASS (restart recovery + idempotency continuity).

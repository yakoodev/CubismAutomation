# Cubism Jobs API

Base URL: `http://127.0.0.1:18080`

## Endpoints
- `POST /jobs`
- `GET /jobs`
- `GET /jobs/{id}`
- `POST /jobs/{id}/cancel`
- `DELETE /jobs/{id}`
- `POST /jobs/cleanup`

## Idempotency
- Send header `Idempotency-Key` on `POST /jobs`.
- If a job already exists for the same key, server returns existing job with:
  - `idempotent_reused: true`
  - HTTP `200`

## Job states
- `queued`
- `running`
- `done`
- `failed`
- `canceled`

## Supported actions
- `noop` (default)
- `sleep` with `sleep_ms`
- `project_open` (reuses `POST /project/open` payload schema)

## Persistence and recovery
- Jobs are persisted to disk (default path: `temp/jobs-store.tsv`).
- Configurable Java system properties:
  - `-Dcubism.agent.jobs.store=<path>`: override snapshot path.
  - `-Dcubism.agent.jobs.ttl.ms=<milliseconds>`: TTL for terminal jobs (default `86400000`).
  - `-Dcubism.agent.jobs.max=<count>`: max kept jobs after cleanup (default `2000`).
- On startup, saved jobs are loaded.
  - `queued`/`running` jobs are reconciled to `failed` with error `recovered_interrupted`.
  - Idempotency mapping (`Idempotency-Key`) is restored from snapshot.

## List filters (`GET /jobs`)
- `status` (e.g. `done`, `failed`, `canceled`, `running`, `queued`)
- `idempotency_key`
- `limit` (1..500, default 100)

Example:
```text
/jobs?status=done&limit=20
```

## Admin operations
- `DELETE /jobs/{id}`:
  - Deletes only terminal jobs.
  - Missing/already deleted job returns `no_effect`.
- `POST /jobs/cleanup`:
  - Bulk cleanup of terminal jobs.
  - Body filters:
    - `status` (optional)
    - `before_ms` (optional epoch millis, default `now`)
    - `limit` (optional, default `200`, max `5000`)
  - If nothing matched, returns `no_effect`.

## Request examples
```json
{"action":"noop"}
```

```json
{"action":"sleep","sleep_ms":8000}
```

```json
{"action":"project_open","path":"C:\\path\\to\\model.cmo3","close_current_first":true}
```

```json
{"status":"done","limit":100}
```

## Validation script
```powershell
powershell -ExecutionPolicy Bypass -File scripts/32_smoke_jobs_api.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/33_smoke_jobs_recovery.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/34_smoke_jobs_admin_api.ps1
```

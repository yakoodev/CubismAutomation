# Cubism Jobs API

Base URL: `http://127.0.0.1:18080`

## Endpoints
- `POST /jobs`
- `GET /jobs`
- `GET /jobs/{id}`
- `POST /jobs/{id}/cancel`

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

## Validation script
```powershell
powershell -ExecutionPolicy Bypass -File scripts/32_smoke_jobs_api.ps1
```

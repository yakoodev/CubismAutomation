# Cubism Agent API MVP

Base URL: `http://127.0.0.1:18080`

Auth for protected endpoints (optional when auth mode is off by default):\n- `Authorization: Bearer <CUBISM_AGENT_TOKEN>`\n- or `X-Api-Token: <CUBISM_AGENT_TOKEN>`

## GET /hello
- Purpose: connectivity smoke endpoint.
- Response `200 text/plain`:
  - `hello world`

## GET /health
- Purpose: liveness probe.
- Response `200 text/plain`:
  - `ok`
 - Auth: not required

## GET /version
- Purpose: server version and identity.
- Auth: optional by default (required if CUBISM_AGENT_AUTH_MODE=required)
- Response `200 application/json`:
```json
{"ok":true,"agent":"cubism-agent-server","version":"0.1.0-mvp","commands":["cubism.zoom_in","cubism.zoom_out","cubism.zoom_reset","cubism.undo","cubism.redo"]}
```

## POST /command
- Purpose: execute supported Cubism command through in-process adapter.
- Auth: optional by default (required if CUBISM_AGENT_AUTH_MODE=required)
- Request body:
```json
{"command":"cubism.zoom_in"}
```
- Success response `200 application/json`:
```json
{"ok":true,"command":"cubism.zoom_in","status":"executed"}
```
- Validation failure `400 application/json`:
```json
{"ok":false,"error":"bad_request","message":"unsupported command: ..."}
```
- Runtime failure `500 application/json` (for example if Cubism app state is not ready):
```json
{"ok":false,"error":"command_failed","message":"..."}
```

Supported commands in this stage:
- `cubism.zoom_in`
- `cubism.zoom_out`
- `cubism.zoom_reset`
- `cubism.undo`
- `cubism.redo`

## POST /startup/prepare
- Purpose: startup orchestration entrypoint (task 090, phase 1).
- Request body (optional fields):
```json
{"license_mode":"free","create_new_model":true,"wait_timeout_ms":30000}
```
- Current behavior:
  - waits until `CEAppCtrl` is available,
  - startup/license dialog automation is currently reported as `skipped` placeholder,
  - can call `command_newModel()` to create a new model document.
- Success response `200`:
```json
{"ok":true,"flow":"startup_prepare","steps":[...]}
```
- Failure response `500`:
```json
{"ok":false,"error":"startup_timeout|startup_failed","message":"..."}
```

## Method rules
- `/version` accepts only `GET`; other methods return `405`.
- `/command` accepts only `POST`; other methods return `405`.


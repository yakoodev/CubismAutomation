# 110_mesh-api-mvp

## Status
Done.

## Implemented
- Endpoints:
  - `GET /mesh/list`
  - `GET /mesh/active`
  - `GET /mesh/state`
  - `POST /mesh/select`
  - `POST /mesh/rename`
  - `POST /mesh/visibility`
  - `POST /mesh/lock`
- All read/write mesh operations are executed on Swing EDT (`invokeAndWait`).
- Unified error contract added:
  - `no_document`
  - `no_selected_mesh`
  - `unsupported_action`
  - `invalid_request`
  - `operation_failed`

## Validation
- Smoke script added: `scripts/25_smoke_mesh_api.ps1`.
- Script includes 12 cases (success/error/method checks) for mesh endpoints.

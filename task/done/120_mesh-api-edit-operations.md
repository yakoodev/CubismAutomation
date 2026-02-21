# 120_mesh-api-edit-operations

## Status
Done.

## Implemented
- Endpoint `POST /mesh/ops`.
- Batch operations with per-item status report (`results`).
- Supported operations:
  - `auto_mesh`
  - `divide`
  - `connect`
  - `reset_shape`
  - `fit_contour`
- Dry-run support: `validate_only=true`.
- Guardrails added:
  - request blocked with `guardrail_violation` when mode is clearly non-mesh.

## Validation
- Integration script added: `scripts/26_integration_mesh_ops.ps1`.
- Script supports repeated checks for different opened document types through `-DocumentType` tag.

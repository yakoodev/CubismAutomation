# Cubism Mesh API

Base URL: `http://127.0.0.1:18080`

## Endpoints
- `GET /mesh/list`
- `GET /mesh/active`
- `GET /mesh/state`
- `POST /mesh/select`
- `POST /mesh/rename`
- `POST /mesh/visibility`
- `POST /mesh/lock`
- `POST /mesh/ops`

All endpoints return `application/json`.

## Error Contract
Shared error shape:
```json
{"ok":false,"error":"<code>","message":"..."}
```

Primary error codes:
- `no_document`
- `no_selected_mesh`
- `unsupported_action`
- `invalid_request`
- `guardrail_violation`
- `operation_failed`

## Read Endpoints
### `GET /mesh/list`
Returns meshes of current document with best-effort properties:
- `id`
- `name`
- `visible`
- `locked`
- `className`
- `active`

### `GET /mesh/active`
Returns active mesh only.

### `GET /mesh/state`
Returns combined snapshot:
- `mesh_edit_mode`
- `active_mesh`
- `meshes`

## Write Endpoints
### `POST /mesh/select`
Body:
```json
{"mesh_id":"..."}
```
or
```json
{"mesh_name":"..."}
```
or (aliases)
```json
{"id":"..."}
```

### `POST /mesh/rename`
Body:
```json
{"mesh_id":"...","new_name":"MeshNewName"}
```

### `POST /mesh/visibility`
Body:
```json
{"mesh_name":"...","visible":true}
```

### `POST /mesh/lock`
Body:
```json
{"mesh_name":"...","locked":true}
```

If `mesh_id`/`mesh_name` is omitted, operation tries active mesh.
`rename` is best-effort and may return `unsupported_action` on Cubism builds where mesh rename methods are not exposed.

## Edit Operations
### `POST /mesh/ops`
Batch payload:
```json
{
  "validate_only": true,
  "mesh_id": "ArtMesh78",
  "operations": [
    {"op":"auto_mesh"},
    {"op":"divide"},
    {"op":"connect"},
    {"op":"reset_shape"},
    {"op":"fit_contour"}
  ]
}
```

Supported `op` values:
- `auto_mesh`
- `divide`
- `connect`
- `reset_shape`
- `fit_contour`

Response includes per-item status in `results`.

`validate_only=true` performs dry-run validation without executing operations.
If operation item doesn't contain mesh selector, adapter uses top-level `mesh_id`/`mesh_name` from payload.

Guardrails:
- operation is blocked with `guardrail_violation` when current document mode is clearly non-mesh.

Practical note:
- `auto_mesh` execute may open Cubism UI dialog (`Automatic Mesh generator`) depending on current build and document state.

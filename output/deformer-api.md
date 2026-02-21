# Cubism Deformer API

Base URL: `http://127.0.0.1:18080`

## Endpoints
- `GET /deformers`
- `GET /deformers/state`
- `POST /deformers/select`
- `POST /deformers/rename`

## `GET /deformers`
Returns deformer catalog:
- `id`
- `name`
- `type`
- `visible`
- `locked`
- `target_deformer_id`
- `keyform_count`
- `class_name`
- `active`

## `GET /deformers/state`
Returns:
- `count`
- `active_deformer`
- full `deformers` list

## `POST /deformers/select`
Example:
```json
{"deformer_id":"WarpDeformer1"}
```

Returns `no_effect` when selection request did not become active.

## `POST /deformers/rename`
Example:
```json
{"deformer_id":"WarpDeformer1","new_name":"WarpDeformer1_Renamed"}
```

Returns `no_effect` when name did not change after request.

## Error/guardrail model
- `409 no_document`
- `409 guardrail_violation` (non-modeling document)
- `404 not_found`
- `409 no_effect`
- `405 method_not_allowed`

## Validation script
```powershell
powershell -ExecutionPolicy Bypass -File scripts/29_smoke_deformer_api.ps1
```

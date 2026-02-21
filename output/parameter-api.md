# Cubism Parameter API

Base URL: `http://127.0.0.1:18080`

## Endpoints
- `GET /parameters`
- `GET /parameters/state`
- `POST /parameters/set`

## `GET /parameters`
Returns parameter catalog from model source:
- `id`
- `name`
- `description`
- `param_type`
- `repeat`
- `min` / `max` / `default`
- `value` (if model instance is ready)

## `GET /parameters/state`
Returns:
- `count`
- `values_ready`
- `instance_ready`
- full `parameters` list (same schema as `/parameters`)

## `POST /parameters/set`
Single update:
```json
{"id":"ParamAngleX","value":10.0}
```

Batch update:
```json
{
  "updates": [
    {"id":"ParamAngleX","value":5.0},
    {"id":"ParamAngleY","value":-5.0}
  ]
}
```

## Error/guardrail model
- `409 no_document` when no active document.
- `409 guardrail_violation` for non-modeling document or when parameter instance is not ready.
- `400 out_of_range` when value is outside `min/max`.
- `409 no_effect` when write request is accepted but post-verify read does not match requested value.
- `405 method_not_allowed` for wrong HTTP method.

## Validation script
```powershell
powershell -ExecutionPolicy Bypass -File scripts/28_smoke_parameters_api.ps1
```

Script checks:
- happy path (`/parameters/state`, `/parameters/set`)
- error path (`out_of_range` => `400`)
- guardrail (`GET /parameters/set` => `405`)
- post-verify (read-after-write on `/parameters/state`)

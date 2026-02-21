# Cubism State Read API

Base URL: `http://127.0.0.1:18080`

## Endpoints
- `GET /state`
- `GET /state/project`
- `GET /state/document`
- `GET /state/selection`

All endpoints are read-only and return `application/json`.

## Response shape (summary)
- `project`: current project presence/class/toString.
- `document`: current document presence/class/toString.
- `selection`: selection object/container class and estimated count.

## Consistency guarantees
- Snapshot is taken on Swing EDT (`invokeAndWait`) to avoid reading mixed UI state from background thread.
- `GET /state` returns one combined EDT snapshot (`project + document + selection`) from the same read cycle.
- `/state/project`, `/state/document`, `/state/selection` are independent reads and may differ between calls if UI state changes.

## Known limitations
- Selection count is best-effort via reflection and may be `null` for unsupported document/view types.
- `toString` values are diagnostic, not stable identifiers.

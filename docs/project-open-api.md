# Cubism Project Open API

Base URL: `http://127.0.0.1:18080`

## Endpoint
- `POST /project/open`

Request body:
```json
{
  "path": "C:\\path\\to\\file.cmo3",
  "close_current_first": true
}
```

Accepted extensions:
- `.cmo3`
- `.can3`
- `.cmox`
- `.model3.json`

## Responses
- `200 ok` on successful open.
- `200 ok` with `warning=opened_path_mismatch` if Cubism resolves to a different underlying path.
- `400 invalid_path` for malformed/empty path or non-file target.
- `404 not_found` if file does not exist.
- `400 unsupported_extension` for unsupported file extension.
- `409 no_effect` when open did not produce current document.

## Validation script
```powershell
powershell -ExecutionPolicy Bypass -File scripts/31_smoke_project_open_api.ps1
```

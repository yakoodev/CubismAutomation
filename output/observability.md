# API Observability

## Metrics endpoint
- `GET /metrics`

Response includes:
- `uptime_ms`
- `requests.total`
- `requests.status_2xx`
- `requests.status_4xx`
- `requests.status_5xx`
- `paths` (per-path request counters)
- `commands` (per-command usage counters from `/command`)

## Error catalog
- See `ERRORS.md` for stable error codes and meanings.

## Health checks
- Liveness: `GET /health` must return `200`.
- Basic readiness: `GET /version` must return `200`.
- Observability readiness: `GET /metrics` must return `200` and include `requests.total`.

## Suggested alerts
- `health` non-200 for 60s.
- `requests.status_5xx` increase > 0 in 5-minute window.
- `requests.status_4xx / requests.total` ratio above threshold (API misuse signal).
- `uptime_ms` reset unexpectedly (process restart) outside maintenance windows.

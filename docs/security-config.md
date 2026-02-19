# Cubism Agent Security and Config

## Defaults
- Default auth mode: `CUBISM_AGENT_AUTH_MODE=off`.
- In `off/disabled/none` mode, auth is not required.
- In `required` mode, `CUBISM_AGENT_TOKEN` must be set.

## Environment variables
- `CUBISM_AGENT_HOST` (default `127.0.0.1`)
- `CUBISM_AGENT_PORT` (default `18080`)
- `CUBISM_AGENT_AUTH_MODE`:
  - `off` / `disabled` / `none` (default)
  - `required`
- `CUBISM_AGENT_TOKEN`:
  - `Authorization: Bearer <token>`
  - or `X-Api-Token: <token>`
- `CUBISM_AGENT_ALLOW_COMMANDS`:
  - CSV allowlist, for example `cubism.zoom_in,cubism.undo`
- `CUBISM_AGENT_DENY_COMMANDS`:
  - CSV denylist, has priority over allowlist

## Auth coverage
Protected when auth is `required`:
- `/version`
- `/command`
- `/startup/prepare`
- `/state`
- `/state/project`
- `/state/document`
- `/state/selection`

Always public:
- `/health`
- `/hello`

## Example
```powershell
$env:CUBISM_AGENT_AUTH_MODE="required"
$env:CUBISM_AGENT_TOKEN="my-token"
$env:CUBISM_AGENT_ALLOW_COMMANDS="cubism.zoom_in,cubism.zoom_out,cubism.zoom_reset"
$env:CUBISM_AGENT_DENY_COMMANDS="cubism.undo,cubism.redo"
```

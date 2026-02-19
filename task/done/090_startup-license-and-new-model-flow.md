# 090 startup-license-and-new-model-flow

## Status
Done.

## Implemented
- `POST /startup/prepare` endpoint added.
- Startup flow orchestrator implemented in `StartupAutomationAdapter`.
- Request options supported:
  - `license_mode` (`free` or `pro`)
  - `create_new_model` (`true/false`)
  - `wait_timeout_ms`

## Runtime behavior
1. Wait for `CEAppCtrl` readiness.
2. Select license mode.
3. Handle post-license dialog.
4. Close `Home` window if shown.
5. Create new model and verify via `getCurrentDoc`.

## Validation
- Confirmed in real Cubism run: license can be selected and a new model document is created.

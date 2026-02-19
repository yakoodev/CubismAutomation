# How It Works

1. Patched Cubism entrypoint (`CECubismEditorApp.main`) calls `CubismBootstrap.bootstrap()` at startup.
2. Bootstrap:
   - creates marker file on Desktop,
   - locates external `cubism-agent-server.jar`,
   - loads `com.live2d.cubism.agent.ServerBootstrap` via classloader,
   - calls `start()` in fail-safe mode.
3. External server opens local HTTP API (`/health`, `/version`, `/command`, `/state*`, `/startup/prepare`).
4. `/command` routes to reflection-based adapter on `CEAppCtrl`, executed on Swing EDT.
5. Security/config layer controls auth and allow/deny command policy via env vars.

## Startup Automation (`POST /startup/prepare`)

Startup automation is implemented in `StartupAutomationAdapter` + `StartupWindowAutomator`.

Current sequence:
1. Wait for `CEAppCtrl`.
2. Choose license mode (`free`/`pro`) in the startup license window.
3. Confirm/close post-license modal popup.
4. Detect and close `Home` window if shown.
5. Create new model (when `create_new_model=true`):
   - click `New` in startup dialog when available,
   - verify that `getCurrentDoc` is not null,
   - if not created, call `command_newModel` on EDT,
   - if still not created, send global keyboard `Ctrl+N` and confirm,
   - verify result and return detailed per-step status.

Design goal:
- keep Cubism patch minimal (bootstrap only),
- move API/runtime logic to independently replaceable server jar.

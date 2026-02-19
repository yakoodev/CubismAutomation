# How It Works

1. Patched Cubism entrypoint (`CECubismEditorApp.main`) calls `CubismBootstrap.bootstrap()` at startup.
2. Bootstrap:
   - creates marker file on Desktop,
   - locates external `cubism-agent-server.jar`,
   - loads `com.live2d.cubism.agent.ServerBootstrap` via classloader,
   - calls `start()` in fail-safe mode.
3. External server opens local HTTP API (`/health`, `/version`, `/command`, `/state*`).
4. `/command` routes to reflection-based adapter on `CEAppCtrl`, executed on Swing EDT.
5. Security/config layer controls auth and allow/deny command policy via env vars.

Design goal:
- keep Cubism patch minimal (bootstrap only),
- move API/runtime logic to independently replaceable server jar.

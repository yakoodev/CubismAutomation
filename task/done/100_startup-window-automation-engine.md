# 100 startup-window-automation-engine

## Status
Done.

## Implemented module
- `StartupWindowAutomator` (in-process Java automation).

## Implemented capabilities
- Window discovery via `Window.getWindows()`.
- Control discovery via Swing component tree traversal (`AbstractButton`).
- License/startup/post-license dialog handling.
- `Home` window close handling.
- Keyboard fallbacks (`Robot`) for custom-drawn dialogs:
  - license selection fallback
  - startup `New` fallback
  - generic confirm/close fallback
  - global `Ctrl+N` fallback
- Diagnostic window snapshot in step details when target window is not found.

## Integration
- Wired into `POST /startup/prepare`.
- Step-by-step statuses are returned in JSON response for debugging.

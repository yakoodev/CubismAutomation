# 100_startup-window-automation-engine

## Goal
Build a robust startup UI automation engine for Cubism modal windows on Windows.

## Scope
- In-process window automation backend (Swing/AWT component tree + window scanning).
- Fallback text matching for unstable dialog variants.
- Unified primitives: `wait_window`, `click_button`, `select_option`, `close_dialog`.
- Diagnostic snapshots of visible windows when automation cannot find target controls.

## Deliverables
- Internal module `StartupWindowAutomator`.
- Test scenarios against real Cubism startup dialogs.
- Documentation of known dialog variants and selector limitations.

## Progress
- Added `StartupWindowAutomator` module.
- Integrated into `POST /startup/prepare` to perform real clicks for license/startup dialogs.
- Remaining work: harden selectors for all Cubism locale/skin variants and finalize coverage matrix.

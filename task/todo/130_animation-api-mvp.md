# 130_animation-api-mvp

## Goal
Добавить API для базового управления анимациями.

## Scope
- Управление playback: play/stop, next/prev frame.
- Навигация: jump to frame/keyframe.
- Workspace controls: set/get workspace bounds.

## Deliverables
- Endpoints `POST /animation/playback`, `POST /animation/nav`, `GET /animation/state`.
- Чёткие коды ошибок по неподходящему документу/режиму.
- Smoke и регресс-тесты на timeline сценариях.

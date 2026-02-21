# 100_startup-window-automation-engine

## Goal
Сделать устойчивый движок UI-автоматизации для модальных окон Cubism на Windows.

## Scope
- Backend на Win32 UI Automation (по заголовкам/кнопкам/automation id).
- Fallback по image/text matching для нестабильных окон.
- Унифицированные примитивы: `wait_window`, `click_button`, `select_radio`, `close_dialog`.
- Диагностические снимки состояния окна при ошибках.

## Deliverables
- Внутренний модуль `startup automator`.
- Набор тестовых сценариев на реальных диалогах Cubism.
- Док с ограничениями и списком известных вариаций окон.

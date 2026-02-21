# 210_parameter-presets-and-keyforms

## Goal
Расширить parameter API операциями keyform/preset уровня production.

## Scope
- Read keyform map для параметра.
- Write: apply preset наборов параметров атомарно.
- Guardrails: валидация существования keyform и rollback при частичном применении.

## Deliverables
- Endpoints `GET /parameters/keyforms`, `POST /parameters/preset/apply`.
- Контракт атомарности и отчет per-parameter.
- Интеграционные тесты на rollback и `no_effect`.

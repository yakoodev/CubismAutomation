# 200_deformer-api-mvp

## Goal
Добавить API для introspection и базовых операций с деформерами.

## Scope
- Read: дерево деформеров, типы (warp/rotation), parent-child связи.
- Write: select/focus деформер, rename, visible/lock где поддерживается.
- Guardrails: отказ при неподдерживаемом типе документа или неактивном объекте.

## Deliverables
- Endpoints `GET /deformers`, `GET /deformers/state`, `POST /deformers/select`, `POST /deformers/rename`.
- Валидация эффектов (`no_effect`) и стабильность id в пределах сессии.
- Обновление `tools/cubism-api-console` и документации по сценариям.

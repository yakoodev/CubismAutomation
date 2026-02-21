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

## Progress
- Реализованы endpoint и адаптер:
  - `GET /deformers`
  - `GET /deformers/state`
  - `POST /deformers/select`
  - `POST /deformers/rename`
- Добавлены проверки `no_effect` и `not_found`.
- Обновлены:
  - `tools/cubism-api-console` (preset actions),
  - `docs/deformer-api.md`,
  - `scripts/29_smoke_deformer_api.ps1`.

## Remaining
- Дожать live happy-path/post-verify на стабильном runtime запуске Cubism:
  - `select` с подтверждением `active_deformer`,
  - `rename` с post-verify и rollback.

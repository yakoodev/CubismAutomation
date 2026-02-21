# 190_parameter-api-mvp

## Goal
Добавить API для чтения и записи параметров модели (Param API) с валидацией диапазонов.

## Scope
- Read: список параметров, активные значения, min/max/default.
- Write: set value для одного и batch параметров.
- Guardrails: режим документа, проверка диапазона, `no_effect` при неприменении.

## Deliverables
- Endpoints `GET /parameters`, `GET /parameters/state`, `POST /parameters/set`.
- Тесты: happy/error/guardrail/post-verify с фактическим чтением после записи.
- Обновление `tools/cubism-api-console` для новых endpoint.

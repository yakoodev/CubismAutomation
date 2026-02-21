# 220_project-open-and-model-load-api

## Goal
Добавить endpoint для открытия проекта/модели по пути, чтобы не зависеть от UI-диалогов startup.

## Scope
- `POST /project/open` c `path`.
- Валидация пути и расширения.
- Guardrails: sandbox для путей (allowlist/readonly checks).
- Post-verify: проверка `state/document` после открытия.

## Deliverables
- Endpoint `POST /project/open`.
- Ошибки `invalid_path`, `not_found`, `unsupported_extension`, `no_effect`.
- Интеграционный smoke со сценарием загрузки тестовой модели.

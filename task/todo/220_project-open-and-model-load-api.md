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

## Progress
- Реализован endpoint `POST /project/open` через `CEAppCtrl.command_open(File, boolean)` + fallback сигнатуры.
- Реализованы ошибки:
  - `invalid_path`
  - `not_found`
  - `unsupported_extension`
  - `no_effect`
- Добавлены:
  - `scripts/31_smoke_project_open_api.ps1`
  - `docs/project-open-api.md`

## Remaining
- Дожать стабильный live happy-path smoke в текущей среде (есть периодические long-hang при open/EDT в Cubism runtime).
- Добавить allowlist/sandbox ограничитель путей для production policy.

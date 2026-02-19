# 120_mesh-api-edit-operations

## Goal
Расширить API мешей до операционных действий редактирования.

## Scope
- Команды: auto mesh, divide/connect, reset shape, fit contour (по поддержке Cubism).
- Batch-вызовы с отчётом статусов по каждой операции.
- Guardrails по режиму документа (не выполнять в неподходящем режиме).

## Deliverables
- Endpoint `POST /mesh/ops`.
- Поддержка dry-run (`validate_only=true`).
- Интеграционные тесты на нескольких типах документов.

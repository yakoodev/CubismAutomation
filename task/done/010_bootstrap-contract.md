# 010_bootstrap-contract

## Goal
Зафиксировать стабильный bootstrap-контракт в патче Cubism.

## Scope
- Оставить в patched Cubism только:
  - marker creation,
  - external server bootstrap call.
- Все ошибки bootstrap должны гаситься без падения Cubism.

## Deliverables
- Минимальный bootstrap-код в entrypoint.
- Обновлённый log + описание rollback.

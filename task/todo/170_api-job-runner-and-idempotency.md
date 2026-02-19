# 170_api-job-runner-and-idempotency

## Goal
Сделать долгие и потенциально небезопасные операции управляемыми через job runner.

## Scope
- Очередь job-ов с состояниями (`queued/running/done/failed/canceled`).
- Идемпотентные ключи для повторных вызовов.
- Таймауты и отмена операций.

## Deliverables
- Endpoints `/jobs`, `/jobs/{id}`, `/jobs/{id}/cancel`.
- Контракт idempotency (`Idempotency-Key`).
- Тесты на гонки и повторные запросы.

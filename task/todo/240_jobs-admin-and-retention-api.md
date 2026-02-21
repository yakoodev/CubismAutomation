# 240_jobs-admin-and-retention-api

## Goal
Расширить Jobs API административными операциями для обслуживания очереди.

## Scope
- Add `DELETE /jobs/{id}` for terminal jobs.
- Add `POST /jobs/cleanup` with optional filters (`status`, `before`, `limit`).
- Add list filters on `GET /jobs` (`status`, `idempotency_key`, `limit`).
- Return strict `no_effect` where operation target is missing/already cleaned.

## Deliverables
- Updated server routes + adapter logic.
- Console presets for cleanup/delete/filter requests.
- Smoke tests: happy path + error path + guardrails + post-verify.

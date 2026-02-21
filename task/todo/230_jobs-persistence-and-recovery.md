# 230_jobs-persistence-and-recovery

## Goal
Сделать Jobs API устойчивым к рестартам процесса.

## Scope
- Persist queue and finished job metadata on disk.
- Recovery on startup with status reconciliation.
- TTL и cleanup policy для старых job-ов.

## Deliverables
- Конфигируемый storage path для jobs.
- Recovery report в логах при старте.
- Smoke-тест на restart + idempotency continuity.

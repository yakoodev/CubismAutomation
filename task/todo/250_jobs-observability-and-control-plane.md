# 250_jobs-observability-and-control-plane

## Goal
Усилить управляемость Jobs API для долгой автономной работы.

## Scope
- Add `GET /jobs/stats` (queue depth, running count, terminal totals, oldest age).
- Add `POST /jobs/pause` and `POST /jobs/resume` for scheduler control.
- Add `POST /jobs/retry` for failed jobs with same payload and new id.
- Add clear guardrails for paused mode (`guardrail_violation`).

## Deliverables
- Server implementation and docs.
- Console presets for stats/pause/resume/retry.
- Smoke coverage: happy + error + guardrails + post-verify.

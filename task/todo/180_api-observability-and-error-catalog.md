# 180_api-observability-and-error-catalog

## Goal
Сделать API наблюдаемым и предсказуемым в прод-использовании.

## Scope
- Единый error catalog (коды, причины, remediation hints).
- Correlation id и audit trail запросов.
- Технические метрики: latency, success/fail rate, command usage.

## Deliverables
- Endpoint `GET /metrics` (или экспорт в лог).
- Документ `ERRORS.md` с кодами ошибок.
- Набор алертов/health-check критериев для эксплуатации.

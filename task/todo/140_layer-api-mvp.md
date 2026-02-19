# 140_layer-api-mvp

## Goal
Добавить API для управления слоями/объектами сцены.

## Scope
- Read: дерево слоёв/объектов, текущая видимость/lock.
- Write: visible on/off, lock on/off, solo/unsolo, reorder (где доступно).
- Массовые операции по списку id.

## Deliverables
- Endpoints `GET /layers`, `POST /layers/visibility`, `POST /layers/lock`, `POST /layers/reorder`.
- Стабильная модель id (временные и постоянные id с оговорками).
- Тесты на консистентность после последовательности операций.

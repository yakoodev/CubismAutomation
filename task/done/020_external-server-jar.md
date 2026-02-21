# 020_external-server-jar

## Goal
Вынести API-сервер в отдельный `cubism-agent-server.jar`.

## Scope
- Отдельный модуль/сборка server jar.
- singleton lifecycle (`start/stop`).
- `/hello` + `/health`.

## Deliverables
- Артефакт server jar.
- Инструкция, куда его класть рядом с Cubism.

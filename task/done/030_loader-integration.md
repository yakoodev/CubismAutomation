# 030_loader-integration

## Goal
Подключить внешний server jar из patched Cubism.

## Scope
- Загрузка jar через classloader.
- Поиск bootstrap-класса/метода (`start`).
- Fail-safe: при ошибке Cubism продолжает запуск.

## Deliverables
- Рабочий loader path.
- Лог диагностики загрузки.

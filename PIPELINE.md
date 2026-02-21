# PIPELINE

## 0) Foundation
1. Проверка инструментов и окружения.
2. Инспекция входного JAR.
3. Выбор стабильной точки патча.

## 1) Bootstrap patch
1. Патч entrypoint в Cubism.
2. Вставка marker creation.
3. Вставка вызова bootstrap-loader.
4. Удаление signature-файлов.
5. Smoke-тест старта Cubism.

## 2) External server runtime
1. Собрать отдельный `cubism-agent-server.jar`.
2. Реализовать singleton server lifecycle.
3. Поднять HTTP API (`/hello`).
4. Интегрировать загрузку server jar из bootstrap.
5. Обработать graceful fail (Cubism не должен падать, если сервер не стартовал).

## 3) Automation API MVP
1. `/health`, `/version`, `/hello`.
2. Базовый command endpoint (например `/command`).
3. Безопасная валидация входа.
4. Аудит-лог вызовов.

## 4) Cubism integration adapters
1. Каталог команд Cubism -> API actions.
2. Адаптер очереди действий (EDT/thread-safe).
3. Read-only state endpoints.

## 5) Hardening
1. Таймауты и circuit breakers.
2. Port collision handling.
3. Feature flags / env config.
4. Regression tests на запуск Cubism.

## 6) Release cycle
1. Версионирование patch + server jar.
2. Repro build script.
3. Rollback script.
4. Документация миграций.

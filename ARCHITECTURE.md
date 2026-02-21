# ARCHITECTURE

## Целевой подход (рекомендуемый)

### A. Cubism patch (тонкий bootstrap)
- Патчится `com.live2d.cubism.CECubismEditorApp.main([Ljava/lang/String;)V`.
- В начале выполнения:
  1) создаётся `%USERPROFILE%\Desktop\cubism_patched_ok.txt`,
  2) вызывается загрузчик server runtime.

### B. Отдельный server jar
- Содержит API-server, роутинг, orchestration, бизнес-логику.
- Может жить рядом с Cubism jar (`plugins/agent/` или `lib/agent/` стратегия).
- Загружается через:
  - URLClassLoader (быстрый путь), либо
  - заранее встроенный classpath hook (долгосрочно стабильнее).

### C. API surface
- Базовый маршрут: `GET /hello -> hello world`.
- Далее расширение до команд автоматизации:
  - open/save/export,
  - execute action by id,
  - read state/selection,
  - timeline/model operations.

## Почему так
- Минимальный риск поломки Cubism.
- Сервер обновляется независимо от патча.
- Упрощается rollback и релизный цикл.

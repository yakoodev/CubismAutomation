# INSTRUCTIONS

## Базовые правила
1. Все изменения должны быть воспроизводимы и логироваться.
2. Приоритет — минимально инвазивные патчи.
3. По возможности патчить один и тот же entrypoint-файл (`com.live2d.cubism.CECubismEditorApp`).
4. Любой новый runtime-функционал (API, роутинг, сериализация) выносить во внешний server jar.
5. В Cubism-патче оставлять только bootstrap-код:
   - создать маркерный файл,
   - загрузить и запустить server runtime.

## Организация задач
- Новая задача создаётся файлом в `task/todo`.
- После завершения файл переносится в `task/done` с итогом/артефактами.
- Формат имени: `NNN_<short-name>.md`.

## Definition of Done для каждой задачи
- Описан scope.
- Есть проверяемый результат (команда/тест).
- Обновлён `output/patch.log`.
- Если это влияет на pipeline — обновлён `PIPELINE.md`.

## Актуальные локальные пути
- Cubism runtime dir: `C:\Users\Yakoo\source\Live2D Cubism 5.3`
- Cubism editor exe: `C:\Users\Yakoo\source\Live2D Cubism 5.3\CubismEditor5.exe`
- Тестовая модель для API/mesh: `C:\Users\Yakoo\Downloads\vt\hibiki`

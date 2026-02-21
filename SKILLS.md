# SKILLS

Набор прикладных skills для следующих чатов/итераций.

## skill: jar-inplace-patch
Цель: безопасно патчить JAR без полной распаковки дерева.

Шаги:
1. Скопировать input jar в output.
2. Извлечь только целевой class.
3. Пропатчить class helper-утилитой.
4. Обновить class обратно через `jar uf`.
5. Удалить signatures через `7z d`.
6. Проверить `jar tf`/`javap`.

## skill: cubism-bootstrap-minimal
Цель: держать Cubism-патч минимальным.

Правила:
1. Патчить только entrypoint (`CECubismEditorApp.main`).
2. В bootstrap оставлять только:
   - marker file creation,
   - запуск внешнего server runtime.
3. Любые исключения bootstrap не должны валить Cubism.

## skill: api-runtime-separation
Цель: разделять patch и server runtime.

Правила:
1. API-логика живёт в отдельном server jar.
2. Патч Cubism не содержит бизнес-логики API.
3. Сервер обновляется отдельно от patched jar.

## skill: agents-md-compliance
Цель: не нарушать AGENTS-процедуру.

Чек:
1. Прогнать `00_check_tools`.
2. Прогнать `10_inspect_jar`.
3. Сделать `12_unpack_and_unsign` (минимум для обязательного шага и логов).
4. Зафиксировать кандидата патча.
5. Зафиксировать метод патча.
6. Собрать выходной jar.
7. Провести тест и записать команду+результат.
8. Обновить `output/patch.log`.

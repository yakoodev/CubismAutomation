# AGENTS

Этот файл фиксирует, как workflow соблюдает требования корневого `AGENTS.md`.

## Обязательные этапы
1. `scripts/00_check_tools.ps1`
2. `scripts/10_inspect_jar.ps1`
3. `scripts/12_unpack_and_unsign.ps1`
4. выбор кандидата патча
5. внедрение патча
6. сборка `output/Live2D_Cubism_patched.jar`
7. тест загрузки/инициализации
8. фиксация в `output/patch.log`

## Как выполняем этапы в этом проекте
- Для соответствия AGENTS шаги 1/2/3 всегда выполняются и логируются.
- Финальная сборка делается безопасным способом (in-place update), чтобы не потерять файлы.
- Подписи (`.SF/.RSA/.DSA`) всегда удаляются.

## Команды AGENTS-by-step
```powershell
powershell -ExecutionPolicy Bypass -File scripts/00_check_tools.ps1
powershell -ExecutionPolicy Bypass -File scripts/10_inspect_jar.ps1 -JarPath input/Live2D_Cubism.jar
powershell -ExecutionPolicy Bypass -File scripts/12_unpack_and_unsign.ps1 -JarPath input/Live2D_Cubism.jar
```

Дальше — patch/build/test по `TOOLS_AND_COMMANDS.md`.

## Критичный practical note
Для этого JAR нельзя полагаться на full-repack после полного `7z x` на Windows: возможна потеря части файлов из-за path constraints. Поэтому production-путь: только in-place jar update.

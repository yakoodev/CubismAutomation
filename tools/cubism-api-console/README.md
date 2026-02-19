# Cubism API Console

Минимальный C# web UI для ручной проверки Cubism Agent API.

## Запуск
```powershell
cd tools/cubism-api-console
dotnet run -c Release
```

UI доступен на:
- `http://127.0.0.1:51888`

## Конфиг
`appsettings.json`:
- `CubismApi.BaseUrl` - адрес Cubism Agent API
- `CubismApi.Token` - дефолтный Bearer token (опционально)

## Что можно проверить
- `GET /health`, `/version`, `/state*`
- `POST /startup/prepare` (task 090)
- команды (`zoom_in`, `undo`, и т.д.)
- кастомный POST payload к любому path
- token override прямо в UI (без перезапуска)

## `/startup/prepare` (актуальное поведение)
Рекомендуемый payload:
```json
{"license_mode":"free","create_new_model":true,"wait_timeout_ms":30000}
```

Что делает агент:
1. выбирает лицензию (`free` или `pro`);
2. закрывает пост-лицензионное окно (`OK`/`Continue`, если есть);
3. закрывает окно `Home` (если появилось);
4. создает новую модель (через `New`, затем fallback: API-команда и `Ctrl+N`);
5. проверяет, что документ реально создан, и возвращает подробные steps в JSON.

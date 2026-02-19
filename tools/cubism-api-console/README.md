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

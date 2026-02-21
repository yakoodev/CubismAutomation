# Cubism API Console

Минимальный C# web UI для ручной проверки Cubism Agent API.

## Запуск
```powershell
cd tools/cubism-api-console
dotnet run
```

UI будет доступен на:
- `http://127.0.0.1:51888`

## Конфиг
`appsettings.json`:
- `CubismApi.BaseUrl` - адрес Cubism Agent API
- `CubismApi.Token` - Bearer token для авторизации

## Что можно проверить
- Кнопки `GET /health`, `/version`, `/state*`
- Кнопки команд (`zoom_in`, `undo`, ...)
- Кастомный POST payload к любому path

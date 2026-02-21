# Cubism Agent Security & Config

## Secure-by-default
- РџРѕ СѓРјРѕР»С‡Р°РЅРёСЋ auth РІРєР»СЋС‡РµРЅ: `CUBISM_AGENT_AUTH_MODE=off`.
- Р”Р»СЏ Р·Р°С‰РёС‰РµРЅРЅС‹С… endpoint-РѕРІ РЅСѓР¶РµРЅ Bearer token.
- Р•СЃР»Рё token РЅРµ Р·Р°РґР°РЅ, API РІРѕР·РІСЂР°С‰Р°РµС‚ `503 auth_misconfigured`.

## Env-РєРѕРЅС„РёРі
- `CUBISM_AGENT_HOST` (default `127.0.0.1`)
- `CUBISM_AGENT_PORT` (default `18080`)
- `CUBISM_AGENT_AUTH_MODE`:
  - `required` (default)
  - `off` / `disabled` / `none`
- `CUBISM_AGENT_TOKEN`:
  - token РґР»СЏ `Authorization: Bearer <token>`
  - Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅРѕ `X-Api-Token: <token>`
- `CUBISM_AGENT_ALLOW_COMMANDS`:
  - CSV allowlist РєРѕРјР°РЅРґ, РЅР°РїСЂРёРјРµСЂ `cubism.zoom_in,cubism.undo`
  - РµСЃР»Рё РїСѓСЃС‚Рѕ, СЂР°Р·СЂРµС€РµРЅС‹ РІСЃРµ Р·Р°СЂРµРіРёСЃС‚СЂРёСЂРѕРІР°РЅРЅС‹Рµ РєРѕРјР°РЅРґС‹ (РєСЂРѕРјРµ deny)
- `CUBISM_AGENT_DENY_COMMANDS`:
  - CSV denylist РєРѕРјР°РЅРґ, РёРјРµРµС‚ РїСЂРёРѕСЂРёС‚РµС‚ РЅР°Рґ allow

## Auth coverage
РўСЂРµР±СѓСЋС‚ auth:
- `/version`
- `/command`
- `/state`
- `/state/project`
- `/state/document`
- `/state/selection`

Public (Р±РµР· auth):
- `/health`
- `/hello`

## РџСЂРёРјРµСЂС‹
```powershell
$env:CUBISM_AGENT_TOKEN="my-token"
$env:CUBISM_AGENT_ALLOW_COMMANDS="cubism.zoom_in,cubism.zoom_out,cubism.zoom_reset"
$env:CUBISM_AGENT_DENY_COMMANDS="cubism.undo,cubism.redo"
```


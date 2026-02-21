# TOOLS_AND_COMMANDS

Ниже — утверждённые инструменты и точные команды для воспроизводимого патча.

## Shell
- PowerShell (Windows)

## Бинарники
- 7-Zip: `C:\Program Files\7-Zip\7z.exe`
- JDK tools (PATH): `java`, `javac`, `jar`, `javap`, `jarsigner`, `keytool`
- CFR: `tools/cfr/cfr.jar`

## Скрипты проекта
- `scripts/00_check_tools.ps1`
- `scripts/10_inspect_jar.ps1`
- `scripts/12_unpack_and_unsign.ps1`
- `scripts/40_repack.ps1`
- `scripts/50_test_loadclass.ps1`
- `scripts/27_capture_analyze_screenshots.ps1`
- `scripts/28_smoke_parameters_api.ps1`
- `scripts/29_smoke_deformer_api.ps1`
- `scripts/30_smoke_metrics_api.ps1`
- `scripts/31_smoke_project_open_api.ps1`
- `scripts/32_smoke_jobs_api.ps1`
- `scripts/33_smoke_jobs_recovery.ps1`
- `scripts/34_smoke_jobs_admin_api.ps1`
- `scripts/84_stop_cubism.ps1`
- `scripts/85_start_cubism.ps1`

## Точные команды (baseline)
1. Проверка инструментов:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/00_check_tools.ps1
```

2. Инспекция jar:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/10_inspect_jar.ps1 -JarPath input/Live2D_Cubism.jar
```

3. Распаковка + unsign (для анализа/редактирования):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/12_unpack_and_unsign.ps1 -JarPath input/Live2D_Cubism.jar
```

4. Безопасная сборка патча (in-place jar update, рекомендуемый путь):
```powershell
Copy-Item input/Live2D_Cubism.jar output/Live2D_Cubism_patched.jar -Force
```

5. Извлечь только нужный класс:
```powershell
jar xf output/Live2D_Cubism_patched.jar com/live2d/cubism/CECubismEditorApp.class
```

6. Пропатчить entrypoint helper-ом:
```powershell
javac work/patcher/PatchMethodStart.java
java -cp work/patcher PatchMethodStart work/jarpatch/com/live2d/cubism/CECubismEditorApp.class main "([Ljava/lang/String;)V"
```

7. Вернуть класс в jar:
```powershell
jar uf output/Live2D_Cubism_patched.jar com/live2d/cubism/CECubismEditorApp.class
```

8. Добавить server runtime classes (если используется встроенный runtime):
```powershell
jar uf output/Live2D_Cubism_patched.jar com/live2d/cubism/patch/*.class
```

9. Удалить подписи:
```powershell
"C:\Program Files\7-Zip\7z.exe" d output/Live2D_Cubism_patched.jar META-INF/GMO-CODE.SF META-INF/GMO-CODE.RSA META-INF/*.DSA -y
```

10. Проверка инъекции:
```powershell
javap -classpath output/Live2D_Cubism_patched.jar -c -p com.live2d.cubism.CECubismEditorApp
```

11. Smoke-test class load:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/50_test_loadclass.ps1 -JarPath output/Live2D_Cubism_patched.jar -ClassName com.live2d.cubism.HelloCubism30
```

## Runtime API команды для live-проверок
Если API отвечает `no_document`, сначала выполнить:
```powershell
Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:18080/startup/prepare" -Method POST -ContentType "application/json" -Body '{"license_mode":"free","create_new_model":true,"wait_timeout_ms":30000}'
```

Снять текущий кадр (PNG stream):
```powershell
Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:18080/screenshot/current?workspace_only=true" -OutFile temp/live-shot-now.png
```

Серия скриншотов + автоанализ:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/27_capture_analyze_screenshots.ps1 -MeshId ArtMesh78 -Count 5 -IntervalMs 700
```

Smoke для Parameter API:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/28_smoke_parameters_api.ps1
```

Smoke для Deformer API:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/29_smoke_deformer_api.ps1
```

Smoke для Metrics API:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/30_smoke_metrics_api.ps1
```

Smoke для Project Open API:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/31_smoke_project_open_api.ps1
```

Smoke для Jobs API:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/32_smoke_jobs_api.ps1
```

Smoke для Jobs recovery (restart + continuity):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/33_smoke_jobs_recovery.ps1
```

Smoke для Jobs admin/retention API:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/34_smoke_jobs_admin_api.ps1
```

Остановить Cubism (graceful close + force fallback):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/84_stop_cubism.ps1
```

Запустить Cubism и дождаться API health:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/85_start_cubism.ps1
```

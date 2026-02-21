# Release Playbook

## Version format
- `vMAJOR.MINOR.PATCH` for stable releases.
- Example: `v0.8.0`.

## Build
```powershell
powershell -ExecutionPolicy Bypass -File scripts/80_build_release.ps1 -Version v0.8.0
```

Output:
- `output/release/v0.8.0/Live2D_Cubism_patched.jar`
- `output/release/v0.8.0/cubism-agent-server.jar`
- `output/release/v0.8.0/SHA256SUMS.txt`
- `output/release/v0.8.0/release-manifest.txt`

## Verify
```powershell
powershell -ExecutionPolicy Bypass -File scripts/81_verify_release.ps1 -ReleaseDir output/release/v0.8.0
```

## Install server jar to Cubism
```powershell
powershell -ExecutionPolicy Bypass -File scripts/82_install_server_jar.ps1 -ReleaseDir output/release/v0.8.0
```

This script updates only:
- `C:\Users\Yakoo\source\Live2D Cubism 5.3\cubism-agent-server.jar`
and creates backup in:
- `C:\Users\Yakoo\source\Live2D Cubism 5.3\agent-backups\`

## Rollback
Latest backup:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/83_rollback_server_jar.ps1
```

Specific backup:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/83_rollback_server_jar.ps1 -BackupFile "C:\Users\Yakoo\source\Live2D Cubism 5.3\agent-backups\cubism-agent-server.20260219-163000.jar"
```

## Validation model
- Default model for release verification: `C:\Users\Yakoo\Downloads\vt\hibiki`

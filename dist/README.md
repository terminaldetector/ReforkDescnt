# Evening test — ready files

After CI (or local `./scripts/package_all.sh`):

| Artifact | Where | For |
|----------|-------|-----|
| `drmd-6dof-1.1.2.jar` | `dist/` / Actions **drmd-6dof-pc** | PC Fabric 1.21.1 |
| `drmd-6dof-fast-test-1.0.0.mcaddon` | `dist/` / Actions **drmd-6dof-mcpe** | Bedrock / MCPE |
| **drmd-evening-test** | Actions combined bundle | both |

## Local one-shot

```bash
./scripts/package_all.sh
# → dist/drmd-6dof-1.1.2.jar
# → dist/drmd-6dof-fast-test-1.0.0.mcaddon
```

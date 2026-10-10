# DRMD 6DOF

Descent/Prey-inspired 6DoF game project built on Minecraft as a technical base. Minecraft is not the
design target: flight, combat, traversal and rendering are being reshaped around the project.

Target: **Minecraft 1.21.1, Fabric, Java 21**. Minecraft 26.3 is outside the declared compatibility
range. The current implementation and its limits are tracked in
[`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md); design and migration notes are linked there.

## Build

Use JDK 21:

```bash
./gradlew test runGametest build
```

The jar is written to `build/libs/`. GitHub Actions also runs a real-client render check and publishes
build artifacts under **Actions → Build DRMD 6DOF**.

## Install

Install Fabric Loader **1.21.1**, Fabric API for 1.21.1, and the same DRMD jar on client and server.
Use a fresh save when testing world-generation settings; existing saves retain their generated height
and seeded world type.

## In-game settings

- **DRMD World Generation**, from Create World: world height, world template, generated-content
  switches and the experimental cubic-snapshot recorder. See
  [`docs/WORLDGEN_MENU.md`](docs/WORLDGEN_MENU.md) for apply timing and limits.
- **DRMD Settings**, from the pause screen or `,`: cockpit/HUD, world view, portal view, flight feel,
  ship and diagnostic controls.

The cubic snapshots are supplemental recordings of loaded vanilla sections. They do not provide a
CubicChunks world engine or replace vanilla chunk generation, lighting or rendering.

## Flight controls

| Input | Action |
|---|---|
| `H` | Toggle 6DoF |
| `WASD` | Thrust |
| `Space` / `Ctrl` | Move up / down in ship-local space |
| `Q` / `E` | Roll |
| `Shift` | Dash |
| `R` | Afterburner |
| `F` | Flight assist |
| `M` | Weapon workshop |

See [`docs/CONTROLS.md`](docs/CONTROLS.md) for the current bindings and configuration.

## Current systems

- Inertial 6DoF flight and the Pyro GX as a separate vehicle entity.
- Local gravity, gravity torches, magnetic anomalies and targeted structural destruction.
- Descent weapons, enemy roles, industrial landmarks and optional world templates.
- Experimental portal and mirror views, hybrid voxel rendering for marked materials, and distant
  surface projection. These systems have separate completion limits documented in project status.

The Pyro uses a procedural model until an exported DXX model asset is available. The design target and
exact current boundary are recorded in [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md).

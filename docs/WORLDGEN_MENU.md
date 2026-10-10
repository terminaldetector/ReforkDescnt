# World creation settings

The DRMD button on Minecraft's Create World screen opens one settings page grouped into world height,
world template, generated content and experimental storage. Controls have localized descriptions and
the page scrolls on short windows.

## What the controls change

| Group | Setting | Effect and timing |
|---|---|---|
| World height | Advanced / Vanilla | Selects the tall DRMD dimension data pack or Minecraft's normal Overworld height. Restart before creating a world after changing this choice. |
| World template | Stock / Psychedelic / Infinite Megacity | Selects the template stored with the next save. Existing saves keep their seeded world kind. |
| Generated content | Nether/Core, End, Klondike, Macro structures, Surface districts, Orbit junk | These are saved global defaults and update the active feature switches immediately. Vanilla height suppresses all six, but keeps their saved selections for when Advanced is restored. Orbit content is independent of surface districts. |
| Experimental storage | Cubic snapshots | Optionally records loaded vanilla terrain into supplemental 16³ snapshots. It does not change terrain generation or ownership. |

The controls edit `config/drmd-server.properties`. The world-height option chooses a built-in data
pack at game startup; world type and content flags are global defaults rather than per-world editor
controls. The height of an already-created save remains its source of truth when it loads.

## Cubic snapshot boundary

Snapshots are a transitional storage adapter for already-loaded vanilla sections. They are not a
CubicChunks engine: chunk loading, tickets, generation, lighting, block ticks, networking, height
limits and primary rendering remain vanilla. The setting is off by default and does not enable a
cube renderer or large-coordinate world. The actual migration gaps are listed in
[`architecture/CUBIC_MIGRATION.md`](architecture/CUBIC_MIGRATION.md).

## Tests

The config tests cover legacy properties, cubic snapshot fallback, and retaining selected world
features while Vanilla mode temporarily suppresses them. The separate project status is in
[`PROJECT_STATUS.md`](PROJECT_STATUS.md).

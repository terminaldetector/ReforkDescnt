# Territories, surface events, 6DoF dungeons

## Rule: hub ≠ campaign

**Lunar Base at spawn is a pad only** — Pyro, gravity, SUPPORT drones. It does **not** seed UFO fights or under-spawn reactors.

UFOs and nearby builds are **random surface events** (`SurfaceEventWorldgen`), ≥400 m from spawn.

## Territories (`DungeonTerritory`)

| Kind | Discovery | Primary dungeon styles | Contents |
|------|-----------|------------------------|----------|
| `SPAWN_HUB` | World seed | — | Lunar pad only |
| `SURFACE_EVENT` | Sparse cell grid (~768, 1/7) | `ABANDONED_RESEARCH` / `DRIFT_LAB` / `WARPED_CAVERN` / `AUTO_FACTORY` / `CRYSTAL_REACTOR` | Crashed UFO, Sky UFO, ruin complex, small locator, outpost pair |
| `MEGACITY` | `drmd:megacity` plate | `TECH_RUINS` / `HOLLOW_RING` / `VERTICAL_SPIRE` / `ORBITAL_SHELL` (+ satellite) | City + 2× 6DoF + rift/ring/arch |
| `TECHNOGENIC_SEA` | `drmd:technogenic_sea` | `SUBSEA_LOCATOR` / `SIGNAL_ARRAY` / `HOLLOW_RING` (+ satellite) | Mega Locator + 5 small + 2 underwater dungeons |
| `SCORCHED_LANDS` | `drmd:scorched_lands` | `SMELTERY` / `ANCIENT_POWER` / `WARPED_CAVERN` | Town + groves + under-plate dungeon |

Style pick is salt-stable per plate/event anchor.

Vitality (`DEAD` / `SEMI_ALIVE` / `ALIVE`) is also salt-stable — living sites get traps/turrets + `unstable_reactor` breach fights. See `REACTOR_FIGHT_AND_ORBIT.md`.

## 6DoF dungeon variants (`ComplexStyle`)

Layouts (not only core block):

| Style | Layout |
|-------|--------|
| Sphere family | `ABANDONED_RESEARCH`, `CRYSTAL_REACTOR`, `ANCIENT_POWER`, `SUBSEA_LOCATOR` |
| Cluster | `TECH_RUINS`, `AUTO_FACTORY`, `SMELTERY`, `DRIFT_LAB` |
| Torus | `HOLLOW_RING` |
| Spire | `VERTICAL_SPIRE` |
| Shell | `ORBITAL_SHELL` |
| Warped web | `WARPED_CAVERN` |
| Signal arms | `SIGNAL_ARRAY` (+ locator decor) |

Force: `/d6 industrial <STYLE>`.

## Locator blocks

| Block | Role |
|-------|------|
| `drmd:locator_core` | Mega heart — BE ping, glow, night vision for 6DoF, plate tip on use |
| `drmd:locator_resonator` | Small nodes — speed pulse, sparks |
| `drmd:locator_panel` | Tower/dish skin sparkle |

Placed by `MegaLocatorGenerator` (and SIGNAL/SUBSEA dungeon decor).

## Fresh worlds

Already-seeded worlds keep old spawn UFOs if they were built before this change. New worlds: pad-only spawn + explore for events/plates.

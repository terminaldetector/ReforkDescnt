# Controls

All rebindable under Options → Controls → **DRMD 6DOF**. The options screen (Esc → *DRMD 6DOF…*)
shows the same list as a reminder.

## Flight

| Key | Action |
|--|--|
| `H` | Toggle 6DoF. Off hands creative flight back for building |
| `W` `S` | Thrust along the nose |
| `A` `D` | Strafe along the hull's right axis |
| `Space` / `Ctrl` | Slide along the hull's up axis — translation, not pitch |
| `Q` / `E` | Roll about the nose. Unbounded: keep rolling in one direction forever |
| Mouse | Pitch about local right, yaw about local up. No world-up reference anywhere in the path, so the nose crosses vertical without gimbal lock |
| `X` | Level the bank |
| `Shift` | Dash |
| `R` | Afterburner (Always-Run). Drains engine energy |
| `F` | Flight Assist / dampeners |
| `Z` | Grapple |

## Combat and systems

| Key | Action |
|--|--|
| `G` | Cycle rocket sub-mode |
| `T` | Radar |
| `M` | Weapon Workshop |
| `H` + `Tab` | 3D terrain map |

## Commands

| Command | Effect |
|--|--|
| `/d6 level` | Report the current band and the column extent |
| `/d6 level <name>` | Lift to a band — `nether`, `abyss`, `industrial`, `surface`, `sky`, `orbital`, `end` |
| `/d6 mega city` | Build a megacity at your position |
| `/d6 mega <kind>` | Other megastructures: `arch`, `ring`, `canyon`, `rift`, `continent`, `spiral`, `inverted`, `lunar`, `crashed`, `saucer` |

## Options

Esc → **DRMD 6DOF…**

| Option | Default |
|--|--|
| 3D cockpit | on |
| Instruments | on |
| Telemetry HUD | on |
| Weapon view | on |
| Level skies | on |
| Camera motion — lag, vibration, FOV stretch | on |
| Cockpit opacity | 100% |
| Look gain | 1.00× |
| Roll rate | 175 °/s |

Two creative-only buttons give the Pyro GX and an engineer kit. They are gated on the server, in the
action handler — a client-side gamemode check would be decoration, since the sender decides nothing.

Settings live in `config/drmd.properties` and are written when the screen closes.

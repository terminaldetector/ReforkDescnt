# Construction lasers (green → purple)

Four logical tiers of the engineer construction laser. Material always comes from the **off-hand** block. Placement follows ship aim / local UP (6DoF).

## Tiers

| Tier | Item | Shapes | Continuous |
|------|------|--------|------------|
| **Green** | `construction_laser_green` (+ legacy `construction_laser`) | Line | — |
| **Yellow** | `construction_laser_yellow` | Line · Wall · Box frame | — |
| **Blue** | `construction_laser_blue` | + Solid · Cylinder · **Stream** | Hold RMB in Stream |
| **Purple** | `construction_laser_purple` | + Ring · Platform · Hangar · Torus presets | Hold Stream |

## Construction Mode — frame → build

When Construction Mode is on (`/d6 construct` or after Pyro land):

1. Aim — live wireframe ghost of the current shape  
2. Click — **lock scaffold** (каркас)  
3. Click again — place blocks from off-hand into the frame  
4. Sneak + click — cancel scaffold  

Without Construction Mode, shapes place immediately (no frame).

## Controls

| Input | Action |
|-------|--------|
| Off-hand block | Material |
| Sneak + click (no scaffold) | Cycle shape |
| Click | Draft scaffold / confirm build |
| Sneak + click (with scaffold) | Cancel |
| Hold RMB (Blue/Purple · Stream) | Continuous freehand laying |

## Kit

`/d6 kit` gives all four lasers.

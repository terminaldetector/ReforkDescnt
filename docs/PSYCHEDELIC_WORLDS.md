# Psychedelic fractal worlds

Stock generation option for **void fractal campaigns** with a **weightless** start.

## Enable

Before creating / first-loading a world, set in `config/drmd-server.properties`:

```properties
psychedelicWorlds=true
```

Or force at compile time via `WorldFeatures.PSYCHEDELIC_WORLDS` (default `false`).

The flag is read once at mod init. Worlds that already stock-seeded as lunar/megacity stay that way; psychedelic is baked into `DescentWorldState` on first seed.

## What you get

- **18 fractal kinds** (design range 10–20): Menger, Sierpinski, Mandelbulb, Julia, Apollonian, Gyroid, Spiral Galaxy, Torus Knot, Koch, Hilbert, Dragon, Lorenz, Fibonacci, Cantor, Plasma, Flower of Life, Hyperbolic, Quaternion.
- Seed picks a **primary** variant; five satellite fractals of other kinds queue around the dock.
- **Void dock** at `(0, 688, 0)` — orbital band, glass / end-rod platform, **no gravity pads**.
- First join: teleport to dock, `gravityFactor=0`, 6DoF on. Idle gravity stays off while the world is marked psychedelic.

## Commands

| Command | Effect |
|---------|--------|
| `/d6 psychedelic` | Config + world variant status |
| `/d6 psychedelic seed` | OP: force-seed if not yet stock-seeded |
| `/d6 psychedelic dock` | Teleport to void dock, weightless |

## Notes

- Skips lunar hub and surface districts when enabled at first seed.
- Landmarks still use the distance seed queue — fly near a fractal to raise it.

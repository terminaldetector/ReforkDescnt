# World concept — the layer stack (post-column architecture)

The user's own working concept for what replaces the single stretched Overworld column, once
`spicy-jumping-anchor.md`'s Phase R6 (native portal rendering wired into a real multi-dimension world)
exists. This doc is the concept as stated, plus the arithmetic needed to build it without repeating a
mistake this project already made once. Scope here stops at the end of the main-world orbit band (through
"Void 1") — the Moon is a real, named part of the concept but explicitly future work, kept separate below.

## The core idea, in the user's own words

> Всё, что я делал с колонной — это растягивание чанков сверх масштаба, на который способно измерение;
> тут подход другой, но схожий: измерение просто превращается в другую форму чанка, спасибо Immersive
> Portals.

Each named layer below becomes its own real Minecraft dimension — normal height, normal chunk loading,
normal light engine — instead of a Y-slice of one mega-tall column. A "layer" is the new unit of vertical
chunking, the same way a 16×16 column is the unit of horizontal chunking. Dimensions connect to their
neighbors above/below via Phase R3's portals once that phase exists; until then this is a naming and
budgeting exercise, not a build order.

## Layer table (main world, Core through Void 1)

| # | Layer | Height (blocks) | Dimensions |
|---|---|---|---|
| 1–2 | Ядро (Core) | 1000 (2×500) | 2 |
| 3–4 | Магма (Magma) | 1000 (2×500) | 2 |
| 5–6 | Глубокий мир + Средний (Deep World + Middle) | 1000 (2×500) | 2 |
| 7 | Поверхность (Surface) — all of today's standard world-surface generation, down to the bedrock zone | 500 | 1 |
| 8 | Стратосфера (Stratosphere) | 500 | 1 |
| 9 | Нижняя орбита (Lower Orbit — the lower End layer) | 500 | 1 |
| 10–11 | Основная орбита + ЦРУ (Main Orbit + Central Reactor Installation — upper End) | 1000 (2×500) | 2 |
| 12 | Void 1 — first empty buffer past the orbit, marks the edge of "the main world" | thin/empty, TBD | 1 |

**Total: 12 real dimensions**, ≈6000+ blocks of combined conceptual vertical extent for rows 1–11 alone
(Void 1 is a boundary marker, not a content layer, so it isn't counted toward that sum).

Two rows are inferred, not directly stated, and are worth confirming later rather than treated as fixed:
row 3–4 (Магма) is assumed to mirror Core's own "2×500" shape since the user said "тоже два слоя" without
giving a number; row 9 (Нижняя орбита) is assumed to be a single 500-block layer since only "Основная
орбита с ЦРУ" was given an explicit "1000, 2×500" figure.

**Rows 10–11 already exist, but as one dimension, not two.** This session's own earlier work (Stream B —
tasks #79's Phases B0–B3) built exactly "Main Orbit + CRU": Layer 1 is the Citadel reactor arena, Layer 2
is the `EndSpaceWorldgen` vertical tile-platform extension — but both currently live inside the *same*
real End dimension (`the_end.json`, `min_y=0, height=1888`, just corrected — see the fix below), separated
by Y-offset (`EndSpaceWorldgen.TILE_BASE_Y=512`) rather than by a dimension boundary. That's today's Path-B
habit (one dimension, Y-sliced) surviving inside an otherwise Path-A-shaped feature, because it was built
*before* this session's pivot to native portal rendering. Splitting it into two real dimensions connected
by a Phase R3 portal is the natural target, but shouldn't happen before Phase R1–R3 (the actual rendering
machinery) exist — re-migrating already-shipped, CI-green content ahead of having anywhere to migrate it
*to* would be working out of order. Named here as an explicit open item for Phase R6, not resolved now.

## The height budget: 500 blocks/dimension is a design choice, not an engine requirement

**The real hard ceiling, confirmed from this project's own working config:** any single `dimension_type`
must satisfy `min_y + height ≤ 2032` (also `height ≤ 4064`, `min_y ≥ −2032`, but the sum constraint binds
first for every layer here since each one's `min_y` is meant to be `0`). The existing Overworld override
(`-784 + 2672 = 1888`) sits 144 blocks under that ceiling on purpose, per `WORLD_LAYERS_AUDIT.md`'s own
words: "headroom kept deliberately rather than pushed to the edge." **500 is nowhere near this ceiling** —
a single layer could go up to roughly 1900 and still keep the same margin the Overworld override already
uses safely.

**What actually caused "chunk mush" before wasn't the declared height itself.** Re-reading
`WORLD_LAYERS_AUDIT.md` directly (not from memory): every historical bug in the stretched-column era —
the vertical-stripe chunk-seed leak, the streaming budget not scaling with the bands, the queue-starvation
"generation locks onto a small area" bug, the Core-band "chunks visibly crawling in" — was a bug in
**DRMD's own hand-rolled background block-writer** (`LevelBuilder`, a custom `setBlockState` loop with a
per-tick write budget) not having *its own* tuning re-derived after a height rescale. Vanilla's actual
chunk generator is completely unaffected by declared dimension height beyond its own working range
(`ABYSS_TOP`/`SURFACE_TOP` are pinned to vanilla's real −64…320 generation band for exactly this reason —
moving them moves zero blocks of real terrain). None of this is an argument against a tall single
dimension being *safe* — it's the reason DRMD's *own custom generators*, if any layer needs one, should
each stay modestly sized rather than needing their own from-scratch budget-tuning pass every time.

**So why land on 500, not push closer to 1900?** Two reasons, both about *this* project's own shape, not
the engine:
- **Necessity, not caution — a single dimension physically cannot reach the goal anyway.** Rows 1–11
  alone already total ~6000 blocks of conceptual height, three times the single-dimension ceiling. The
  Moon (below) sits at 20,000–30,000 blocks up. There has never been a version of this concept a single
  dimension could hold — multi-dimension layering isn't a defensive choice here, it's the only way the
  stated goal is reachable at all, regardless of any past bug.
- **Every layer stays "one vanilla world's worth" of vertical room**, roughly matching vanilla's own
  384-block span (−64…320). If a layer ever needs its own `LevelBuilder`-style custom background
  generator, its row-count-per-chunk stays small and easy to reason about by construction, instead of
  needing a `GenerationBudgetTest`-style re-tuning pass every time a number here changes — the exact
  class of bug the column era paid for once already.

**Recommendation: keep 500 blocks/dimension as the standard unit**, with the already-built "Main Orbit +
CRU" tier's 1888 kept as a named, deliberate, documented exception (real content needing real vertical
room — the Citadel arena plus stacked space platforms) rather than a precedent to generalize from.

## Beyond Void 1 — the Moon (explicitly future work, not designed here)

Named for completeness, per the user's own concept, but **not in scope for any near-term phase**:

```
Void 1 (boundary of current scope)
Void 2
Void 3
Lunar atmosphere
THE MOON — a 10,000×10,000-block volumetric area, its own local centre, full 6DoF architecture,
           reachable by flight only during an in-game full moon, positioned 20,000–30,000 blocks
           above the main world
Void 4
Void 5
Infinite empty space, unrendered
```

The user's own framing: *"Луну это в далёкое будущее, сейчас архитектура выглядит до void 1, т.е. до
конца орбиты. Теоретически всю линию генерации мира при правильной модификации можно тянуть бесконечно,
но технически и в планах игровой условности мира достаточно высокая общая высота будет более чем
достаточно."* Pure positioning math (getting a dimension stack to reach a point 20,000+ blocks above
another one) is trivial once Phase R3 exists — the actual hard part named for the Moon specifically is
that it wants "the full potential of 6DoF" as its own from-scratch design, not a reuse of any existing
layer's shape. Left undesigned here on purpose.

## Existing systems this concept already leans on

Not new work — named so the layer table above reads as "mostly already has the pieces," not "starting from
zero":
- **Voxel-horizon mesh** (`docs/VOXEL_HORIZON.md`, this session's own Stream A) — the distant-terrain view
  needed to make a vertical, multi-layer world read as a real horizon from orbit or from altitude, not a
  void. Ships already; Phase A2's altitude-aware cell sizing is exactly the kind of fix a genuinely tall
  world needs.
- **Sky UFO / Citadel-style physical structures** — the "Create: Aeronautics-like airborne installations
  you can storm" the user names is the existing Sky UFO render-layer work (task #66/#78) plus the Citadel
  station generator (`CitadelStationGenerator`/`CitadelDeckShape`) already used for the Main Orbit/CRU
  layer's own reactor arena.
- **Psychedelic mode, reframed** — `PsychedelicWorldgen`/`docs/PSYCHEDELIC_WORLDS.md` already exists; the
  user now names it specifically as the tech-demo vehicle for infinite-vertical-rendering claims ("a
  Menger sponge used directly as the generator for the whole world"), and a venue for gravity-play
  (`GravityFields`/`GravityTorchBlock`) demonstrations. Not a new feature — a new stated *purpose* for an
  existing one, worth keeping in mind if that mode gets touched again.

## What this document is not

Not a phase plan — `spicy-jumping-anchor.md` (Phase R0–R6) is that, and Phase R6 is where this table
would actually get built, gated on R1–R5's rendering machinery existing first. Not a final, locked
spec — the two inferred rows (Магма, Нижняя орбита) and the Main-Orbit/CRU split-vs-keep question are
named above as open, not resolved. This is the reference to build R6 against once its time comes, kept
here rather than only in chat so it survives past this conversation.

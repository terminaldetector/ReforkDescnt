# Immersive Portals (ImmPtl) — optional dimension stack

DRMD Path B (default): one Overworld column (−512…1024) with diggable mantle → Core.
Path A (optional soft-dep): Immersive Portals **Dimension Stack** for true see-through seams.

## Soft dependency

`fabric.mod.json` **suggests** ImmPtl. The mod runs without it.
`PortalComplexity.hasImmersivePortals()` detects common mod ids for HUD only.

### Build-side trap: ImmPtl's transitive access wideners

DRMD compiles against the vendored ImmPtl jar (`modImplementation files("libs/immersiveportals-…")`),
and **Loom applies a mod dependency's `transitive-*` access wideners to this project's compile
environment.** So every vanilla member ImmPtl widens is silently widened for DRMD's compile too. Code
relying on one of those widenings compiles cleanly and CI passes — then crashes at class-load on any
client *without* ImmPtl, because Fabric Loader only applies wideners from **installed** mods.

This is not hypothetical. `SkyUfoEntity.getBoundingBox()` overrides a method vanilla declares `final`;
it compiled only because ImmPtl's own widener covers it (`transitive-extendable class_1297
method_5829`), and a real client without ImmPtl died at startup with `IncompatibleClassChangeError`
before the title screen. Fixed by declaring the widening in DRMD's own `drmd.accesswidener` — which
Fabric Loader *does* apply at runtime, since `fabric.mod.json` declares it.

**Rule: anything DRMD needs widened must be in `src/main/resources/drmd.accesswidener`, never inherited
from ImmPtl.** `AccessWidenerTest` pins the known case; the general risk (a compile that only succeeds
because of an optional dependency's widener) has no compiler or CI signal at all, so check
`imm_ptl.accesswidener` inside the vendored jar before assuming a `final`/`private` vanilla member is
legitimately reachable.

Vanilla Nether / End portals still need **gate catalysts** (complex crafts) even with ImmPtl installed — dig-through remains the seamless survival path.

## Recommended stack (Fabric 1.21.1)

```
End (Oblivion)   ↑
Orbit / sky      ↑   ← optional separate dim, or OW sky band
Overworld        ↑
Nether (Core)    ↑
```

Align ImmPtl floor/ceiling links with `WorldLevels`:

| Seam | OW Y | Notes |
|------|------|--------|
| OW floor ↔ Nether ceiling | ≈ −240 (`NETHER_CEILING`) | Mantle dig path meets Core |
| OW top ↔ End floor | ≈ 880 (`ORBITAL_TOP`) | Techno-ring vista / Oblivion |

## Portal crafts (always on)

1. `plasma_granite` + `energy_cell` → **Portal Stabilizer**
2. Stabilizer + netherite scrap + obsidian + cells → **Nether Gate Catalyst**
3. Stabilizer + end crystals + pearls + plasma granite → **End Gate Catalyst**

Igniting a nether frame or placing an eye consumes the matching catalyst (creative exempt).

## SeamWarmup (Path B + optional ImmPtl)

`SeamWarmup` opens real Nether/End with short-lived chunk tickets when the pilot is within 72 blocks of OW faces −240 / 880 (critical intensify at 10). With ImmPtl installed this pre-warms remote sides of the stack; without it, Path B still streams the Nether column seamlessly via `MantleStream` / `LevelBuilder`.

## Escape

Digging up through mantle / shafts, `/d6 level`, or DimensionSync aftermath cues keep the surface path readable after Core events.

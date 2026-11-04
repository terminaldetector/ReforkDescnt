# CITY TEST MAP / ASSET BANK / AUTHORING PIPELINE

## Goal
Large city for ragdoll/destruction is a measurable stress fixture and later a playable urban zone. Do not hand-build entire map from nothing. Use procedurally repeated modules, legitimate public/open-licensed maps or own schematic, material segmentation and industrial architecture templates. A screen/reference image alone cannot become a correct navigable 3D map without reconstruction.

## Input types and constraints
- Own pixel art/sketches and concept sheets: design references, not replacement for Blender/block mesh collision.
- External city schematics/maps: record creator, source URL, license, permission, original version and attribution; never silently include copyrighted third-party map.
- Image-to-structure: require human/layout guide and algorithm generating streets, buildings, material layers, path graph, support skeleton, destructible supports; do not blindly extrude pixel colors into unusable tower forest.
- Multi-resolution output: near block/collision; mid simplified merged mesh; far impostors/PS1 silhouette. Keep mechanical volumes for doors, lifts, reactor rooms.

## Procedure
1. Select a 256×256 or 512×512 pilot zone, not a continent.
2. Import or procedurally generate 8–20 distinct building archetypes, repeated with deterministic variation, road/rail and underground access.
3. Test one explosive, one rail-thrown block, 32 drones, 128 drones, 2 rocket infantry and full destruction rebuild.
4. Compare before/after FPS p50/p95, server MSPT, debris count, chunk/cube serialization; verify after save/reload.
5. Only then scale city to strategic district. Use `CITY_FIXTURE.json` for seed/coordinates/material categories.

## Requirements for artistic vocabulary
Readable large low-poly forms, intentional rough silhouettes, clear faction technology difference, not endless small decals. PS1 profile can distort only presentation. Organic terrain remains smooth while architecture preserves hard edges; dropped slabs may become physical bodies with appropriate collision.

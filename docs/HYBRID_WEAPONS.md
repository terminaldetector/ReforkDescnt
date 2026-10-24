# Hybrid weapon samples — 24 October 2026

The current request selects three playable samples: paired impulse cannon (`laser_pulse`),
plasma emitter (`plasma`), and heavy gravity rail (`gravy_railgun`). The recovered 1.1.12
binary remains a separate, immutable release. This work targets 1.1.13.

## References and interface

The supplied `Настройка ReforkDescnt.mht` describes polygon shells, rails, coils and moving
mechanisms combined with Minecraft voxel fittings, batteries and replaceable cassettes.
It calls for a shared saved construction in preview, first person, external ship view and
physical muzzle calculation. The 22 October handoff documents the existing arsenal and
three gravity roles; it does not certify this revision's tests.

Five direct Desecrators screenshots supplied on 24 October resolve the earlier missing
visual reference. Images 4219/4222 establish paired lower-corner barrels and clear central
aim; 4223 shows chamfered olive housings, dark collars and orange status strips; 4220
shows slotted rails and exposed energy mechanisms; 4221 shows blue-grey heavy shells,
bright muzzle events and readability under bank. Geometry here is original procedural
geometry inspired by these features, without copying game assets.

Claude's Java Workshop remains the entry point: Stats / Projectile / Flak / Guidance /
Clusters, five construction zones, Load Held, Apply Build, Reset Layout and Copy Java.
The construction workflow gains named hybrid samples, local preset save/load, transform
editing and a real mesh preview. Existing serialized module fields remain compatible.
Apply Build continues to apply construction/muzzles; behavior schema export is explicitly
separate from the existing live weapon definitions.

## Acceptance

- Impulse: slender paired olive chamfered breeches, octagonal barrels, slotted cooling
  jackets, voxel ammunition cells and moving bolts.
- Plasma: broad blue-grey emitter, separated coil rings, cyan chamber and voxel power pack.
- Rail: long separated rails, open acceleration channel, violet load elements and block
  capacitor; it retains the existing physical gravity rail behavior.
- One mesh definition feeds workshop preview, first person and external ship mounts.
  New model origin is the actual muzzle; all geometry extends behind it. Rotation and
  nonuniform scaling cannot move that anchor. Mechanical recoil occurs inside the housing.
- Fresh automated client frames cover each sample, preview/save/load/apply and banked/
  vertical views, alongside the existing projectile and world regressions. Screenshots
  are real client output. Manual evening gameplay remains a separate user test.
- Every coherent milestone is committed on the recovery branch and saved in GHA;
  distribution requires the exact revision's successful tests and SHA-256.

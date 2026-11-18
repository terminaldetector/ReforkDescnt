# Drone loadout inspection and altitude-cloud compatibility: combined verification

Exact production source **`7c502b49e9ca131c649280ca425f7dc0e4264827`** passes
**935 unit tests, 456 ordinary GameTests / 75 entrypoints and two actual clients**.
All fresh test reports have zero failures, errors or skips. The complete Gradle
run exits 0 in 5m54s. Both clients use the same remapped game and observer JARs;
no source, helper or artifact changes occur between them.

| Artifact | SHA-256 |
| --- | --- |
| Game, 2,856,498 bytes | `791f53ad27100cabd310babba486c5bc77e7f825e61a485741e9678346d8f1a8` |
| Observer, 650,537 bytes | `c7a7602a526ad1fc870a4682b2af6db0625509e673f0a13984978ffa033395a4` |

The actual tested game is retained at
`work/multi-agent/integrated-7c502b4/artifacts/drmd-6dof-1.1.35-ufo-dev.1.jar`.
This is local development verification, without publication or full-spec acceptance.

## Changes and preserved boundaries

The menu displays the actual selected synchronized drone slot, its complete saved
weapon layout, or the real component-aware item model when that layout cannot be
rendered. A private hull mesh replaces the fake menu entity. Preview collection,
fitting and caching are bounded and do not mutate equipment or physical objects.

The altitude-cloud extension keeps the native source geometry and near-height
behavior, extends visibility gradually above Y384–512 and orders clouds against
the existing far mesh through a separate true-ray witness. Exact Sodium 16-byte
and vanilla 28-byte vertex layouts use separate typed shaders. Sampler handling
uses available OpenGL APIs, including the tested GL3.2 path. The original world
target, mesh cache and gameplay geometry retain their ownership.

The integration contains 27 files: the reviewed 23-file cloud patch and four-file
menu patch. Twenty-six postimages match their producers exactly. The remaining
mixin JSON preserves every canonical entry and its order, adding only three cloud
client entries; seven newer common mixins are retained. The newer packet/early-cell
fences and all mission, physics, fluid and AI changes remain present.

[Source postimages](EVIDENCE/gui-cloud-source-postimages-7c502b4.json) and
[actual class parity](EVIDENCE/gui-cloud-class-parity-7c502b4.json) record this
boundary: 11 menu classes and 17 cloud-related classes match the corresponding
tested component JARs byte for byte. Separate component GUI five-scene and cloud
GL3.2/Sodium evidence does not become an additional executed combined scenario.
The combined menu observer is being rebound separately with unchanged criteria.

## Actual combined clients

The native saved-surface arrival passes without fixture block edits, post-baseline
external mutations or dropped diagnostic events. Server FULL is first observed at
tick104, both server volumes at107, and the final result at123: 19 ticks after
server FULL. The saved black observation becomes GOLD at height2301 through the
real ingestion, disk and client-packet paths. This is saved-observation repair,
not first-ever ingestion or general migration acceptance.

The six-scene presentation client passes Native, PS1, Debug, real window resize,
restoration and exception paths. Every scene has GL error0 and world nearest-grid
ratio1.0. Five palette/dither references agree with their independent RGB oracle
at1.0. Eight mirror panes form one reflected surface. HUD/outline state is retained;
the actual gold weapon has166 pixels in Native and PS1, with identical camera,
modules and zero projected-vertex movement. A resized PS1 frame was also viewed.

## Evidence and open work

[Combined evidence](EVIDENCE/integrated-7c502b4.zip) contains133 payloads,
40 fresh PNGs and10 depth arrays, 4,779,840 bytes, SHA-256
`e2e83a110c6dd915ba51a82e41ce6ac8d174d02a021af00fa658a58edb9816da`.
Every payload hash/size, ZIP CRC and PNG structure is checked. The complete closed
GameTest save is preserved separately:1350 files /243,947,623 bytes, archive
`work/ufo-body/integrated-7c502b4.zip`, SHA-256
`4f845fd458674c5a496306d0432b317618f67d245eccea4b54f151ba990d1e9a`.
All425 nested gzip streams were decoded before reclaiming only that raw test save.

Both latest specifications, all99 task IDs, the original21 packets and seven
supplemental clauses remain in scope. Generalized PS1 vertex/UV rendering,
terrain/volumetric tiers, full bestiary, automatic siege, general3D AI, generic
ControlCore and complete Dynamic Boarding remain open. Friendly-AA fragments and
recon feedback retain their separate first failures and candidate repairs.
No whole task, release, hardware-performance result or complete specification is
promoted by this report. The previous canonical13c evidence remains unchanged.

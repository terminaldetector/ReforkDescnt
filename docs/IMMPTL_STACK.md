# Native mirrors and portals — 1.1.17-mirrors.1

Immersive Portals is no longer a build or runtime dependency. The bundled external JAR, Gradle
coordinate and Fabric suggestion were removed. Placed DRMD mirrors never spawn external entities.
DRMD owns the required access wideners, reflected view, aperture composite and same-dimension travel.

Adjacent mirrors of the same material and facing form an edge-connected surface. A surface uses
one offscreen world render; each tile contributes its own aperture and only exposed boundary edges
retain a frame. Holes, corners, different facings and linked portal faces do not get filled or joined.
The visible budget is two surfaces per frame within 24 blocks, not two tiles. Chunk scanning remains
limited to loaded nearby chunks. Native recursion is disabled; shader packs, Fabulous and Sodium
require separate verification. Cross-dimension/scaled travel is not added by this milestone.

Saved foreign entity UUIDs are retained for migration. If their owned entity is loaded, the native
server ticker discards it by UUID after checking its entity-type namespace (`imm_ptl` or
`immersive_portals`). It never scans, deletes unrelated portals or loads chunks to locate one.
Native travel/render no longer stop just because an old UUID or an installed external mod exists.
Old external entities not anchored by a surviving DRMD block are outside this bounded migration.

The pure math helpers under `vendor/immptl` remain locally compiled attributed Apache-2.0 source.
Their licence/NOTICE must be retained. They do not load any external mod classes or require its JAR.

Historically this project compiled against ImmPtl and accidentally inherited a transitive access
widener for `Entity.getBoundingBox`. DRMD declares that widening itself. Building and running with
no external JAR is the check against repeating this failure; `AccessWidenerTest` pins the directive.

# Gravity runtime foundation — 1.1.20 candidate

Branch `feature/gravity-runtime-v5` starts at WorldAccess/mirror source a39e3c9 and
includes the subsequent conservative-frustum/camera fix02189c9 via cherry-pick;
the earlier physics and integration/PR9 branches remain preserved.

Gravity fields now use dimension-scoped 3D cells at up to32 scales. A normal emitter
occupies at most eight cells at one scale. Point queries visit one cell per occupied
scale plus dimension-independent fields, rather than scanning all generators. Tilted
cylinders and planes use conservative bounds; exact influence still decides the result.
Exceptionally large fields use a bounded-storage global bucket. This deliberately retains
their influence, though queries still cost O(number of such oversized fields). Null-world
queries preserve the old cross-dimension diagnostic behaviour and are a full scan.

Updates remove old memberships, moving fields replace their prior origin, and clear/remove
release empty buckets. Field values normalize accepted directions, snapshot mutable BlockPos inputs and reject non-finite
or undefined direction/radius/power data. Candidate UUID order is stable, so floating-point
blend order does not depend on hash insertion order. No world storage, tickets or loaded
cube state are owned by this index. Registry mutation/query are synchronized across the
integrated client/server callers.

EntityGravitySystem visits loaded living actors once per dimension with fields. It no longer
repeats entity-section queries for every overlapping emitter. Individual actors then use
the local index. The existing physical response is unchanged in this first step.

This is not universal entity6DoF acceptance. Current legacy mob handling still needs explicit
gravity ownership/restoration, preservation of normal acceleration, oriented bodies and eyes,
collision/pathfinding policy, network orientation and ragdoll transition work. In particular,
the existing setNoGravity(false) release is unsafe for naturally weightless actors; it must
be replaced before claiming general actor compatibility. Ship assembly/content parity and
the v5 combat/city scenarios remain separate unfinished milestones.

Validation must include randomized exact-influence containment, negative/high-Y boundaries,
tilted cylinders, dimension replacement/removal, moving origins,10,000 remote emitters,
large-field fallback and deterministic blend parity, followed by GameTests and fresh clients
before any installable binary is certified.

First focused run:564 unit tests and99 GameTests passed;21 checkpoint-script tests
passed too. The installer generator now takes the verified version, filename and
digest instead of copying the historical1.1.11 instruction file. These focused results
do not certify a1.1.20 binary or a full native6DoF entity milestone.

Follow-up:564 unit tests and101 GameTests passed, including two checks of actual generator
NBT. Invalid legacy radius/power values recover defaults before registration; positive finite
authored radii and zero power survive unchanged. This avoids introducing a load-time exception
through the stricter Field constructor. Tiny accepted directions normalize without becoming
the zero vector. The weapon fixture's acknowledged-terrain change0f76a07 is also included.

## Actor acceleration ownership

The following stage removes EntityGravitySystem's writes to the persistent NoGravity flag.
A common Entity.getFinalGravity mixin suppresses the vanilla scalar acceleration only while
this exact server actor/world has a live field state. Weak actor/world references reject a
reloaded copy with the same UUID and do not retain unloaded worlds. Player movement stays
with FootGravitySystem. Mounting, leaving the field, death/removal and clear release only
this runtime override. Entity NBT and flags owned by flying actors or another system remain
untouched; server shutdown clears the runtime catalogue.

Airborne normal velocity is preserved and acceleration scales with the sampled strength.
The field pass updates velocity; LivingEntity.travel performs ordinary displacement on the
actor's own tick. The old second position step has been removed. Surface remount/adhesion
corrections are still the existing helper. This is not a replacement for arbitrary-orientation
collision, navigation or custom flying-actor travel adapters. Vanilla axis-dependent drag,
world-Y AI, oriented mob geometry/eyes, native ticking-volume policy and network orientation
still need dedicated work before universal6DoF acceptance.

Six new real-world tests cover NBT/UUID reload, natural/external weightlessness, field exit,
vehicle mount/clear, six-axis accumulation/strength, actual vanilla travel and absence of a
second integration. They run in separate basic/live batches: the initial shared-batch attempt
allowed an asynchronous field to overlap a synchronous case and global clear. A subsequent
NoAI fixture could not exercise vanilla travel; the live case now enables AI and explicitly
holds one test column, releasing its own ticket afterward. One legacy portal case also failed
under the changed mixed-batch layout; its XML is retained. Keeping all new cases outside the
old default batches restored the previous101-case placement. All107 GameTests then passed.

The first failed run also stalled in vanilla chunk-unload future completion during shutdown;
its stack and disposable world are retained. A fresh isolated run shut down normally. This
is not evidence that the historical193-second user stall or every shutdown case is fixed.

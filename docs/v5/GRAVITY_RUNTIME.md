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
release empty buckets. Field values snapshot mutable BlockPos inputs and reject non-finite
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

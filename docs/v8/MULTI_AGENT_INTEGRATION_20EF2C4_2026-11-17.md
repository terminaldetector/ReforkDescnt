# Manual city authoring and initial LOD synchronization: verified integration

Exact source `20ef2c4c8fd551e4326d2841601f9c56fa4a3a0a` passes **853 unit tests,
442 ordinary GameTests and 71 entrypoints**, with zero failures, errors or skips.
The offline Java21 build/remaps close0 in4m51s. Two subsequent actual BCC/Sodium
clients pass on the **same game and observer JARs**: original no-edit surface
arrival and colour/profile/mirror/HUD/viewmodel, with40 fresh PNG and10 depth arrays.

## Concrete changes

**Manual city authoring** is now part of ordinary mod startup. The public
`/ungravitycity` commands plan/inspect/apply/pause/resume/cancel use one durable
operator-owned slot, the existing deterministic blueprint and journal, and one
serial background I/O worker per explicitly opened session. Startup does not open
or generate a city. Planning is inert; apply requires a connected Creative
operator with permission2. Loaded sterile air/inert full blocks, real borders,
permissions, world/backend identity and a one-cube page halo are checked. A batch
has at most64 changes, at most448 snapshots and one atomic CAS per server tick.
Occupied solids use a bounded one-result entity query; an actor in the last solid
rejects the entire batch before any world write. Cancellation stops further work
and saves its cursor; it does not undo blocks already placed.

Five integrated runtime files and eight real-file I/O unit cases match the
reviewed isolated source. Independent review found the previously unbounded actor
query; its separate correction passes both actual20Hz command/actor cases. The
unmasked command gate executes41 real dispatcher commands with31 unchanged
criteria, no test bootstrap, normal end-tick packet flush, rights/owner checks,
64 actual changes, durable cancellation/reopen and closed workers. The occupied
case proves all64 cells remain AIR/CAS0, then explicit resume produces one exact
64-cell CAS after removing only the real blocker. See
[command startup](CITY_MAIN_UNMASKED_FIRST_RESULT_2026-11-17.md) and
[bounded actor proof](CITY_BOUNDED_ACTOR_FIRST_RESULT_2026-11-17.md).
These functional world-write cases use STOCK; native city CAS, full85520-cell
completion and mass-destruction/endurance acceptance remain open.

**Initial surface synchronization** preserves accepted section packets when the
first planet seed arrives later. Known-seed replacement and disconnect still
clear prior observations; delayed network application is fenced by connection
identity. The original362 native low-phase premise failure is preserved. Two
deterministic new units fail on its old code, then all three boundary cases pass
after the correction. The unchanged native no-edit scenario now passes as well.
See [ordering defect and first outcomes](CLIENT_FIRST_SNAPSHOT_ORDER_2026-11-17.md).

The earlier integrated linear mechanisms, PS1 palette/dither, native cube-arrival
resampling, scout investigation and stationary physical interception remain. See
[the preceding verified integration](MULTI_AGENT_INTEGRATION_6B50273_2026-11-17.md).
The old hostile-station rule is retained; this does not provide friendly modular
AA for a player's base or activate new siege encounters.

## Exact artifacts and native results

| Artifact | SHA-256 |
| --- | --- |
| Game JAR | `ae446ebfe83258eb336c96ec2452418fe9357e661feae1f4a87ce776242d1dff` |
| Cubiccheck observer | `c7a7602a526ad1fc870a4682b2af6db0625509e673f0a13984978ffa033395a4` |

Native surface arrival observes first server FULL at117, the high cube FULL on
both sides at118 and final receipt at143:26 ticks after first FULL. The initial
actual packet remains2301/black, the later packet2301/GOLD; independent Python
decoding verifies both raw5121-byte payloads and the closed disk cell. Fixture
edits, outside-native post-baseline mutations and dropped observations are0.
The two images are context, not smoothing or rendered-seam acceptance.

Five PS1 colour references have RGB agreement1.0, max channel error0, nearest
world grid1.0, max depth delta5.960464477539063e-8 and minimum dither-sensitive
domain8179 pixels. Six profile/resize scenes preserve native HUD and GL0. Eight
mirror panes merge into one surface, with27772/15837 reflected GOLD pixels.
Projected weapon visibility is166 native/166 PS1 against125 required, unchanged
camera/modules and zero projected vertex delta. Two final/resized frames were
directly inspected. This is actual software Mesa GL3.3, not physical hardware or
a new Fabulous acceptance.

## Evidence and remaining requirements

[Combined evidence](EVIDENCE/integrated-20ef2c4.zip) preserves136 payload files plus
SHA manifest,4,733,077bytes; SHA-256
`9b3d018d22ec2aca98246329fcef06daf44182e694c13f488eb9dffb6a2bb282`.
All member SHA and ZIP CRC checks pass. It includes fresh full/unit XML, executing
helper snapshots, actual native outcomes/packets/cache and40PNG/10depth arrays.
Exact tested JARs remain in `work/multi-agent/integrated-20ef2c4/artifacts/` and the
game binary is in [the local snapshot](../../dist/local-snapshots/20ef2c4/README.md).
The closed full world is independently archived:1281files/239331966bytes,
SHA `e83809371ddb4fe743d51daf528fc5ec47529f6b328219181e78f54fd708aafe`.
Only disposable closed worlds/private derived caches are reclaimed after exact
hash/CRC preservation; game JARs, source, protected donors and user saves remain.

[The21-packet untouched-work map](UNTOUCHED_IMPLEMENTATION_PACKETS_2026-11-17.md)
keeps both current specifications, equal PS1 coverage and direct Boarding/Create
requirements in scope. Live ledger:3 completed /28 in progress /68 pending.
S15-T05 starts for actual preview-source/PS1-HUD work; no task is marked completed
by this integration and no whole-specification or release acceptance is claimed.
Known open work includes actual deferred-fluid continuation after an unavailable
cube arrives, cloud/far-LOD occlusion, complete surface/3D AI and occupied moving
mechanisms, real drone-preview images, connected-body portal transitions,
automatic Survival siege/counterplay and performance/endurance/hardware gates.
The experimental fixes/prototypes for these are separate from the tested game.
No GitHub publication or Actions occurred.

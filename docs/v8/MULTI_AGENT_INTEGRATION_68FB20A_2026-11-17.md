# Deferred native fluids: exact full integration and two same-JAR clients

Exact source **68fb20a4be8b87ee6b280c0fce85bb5baacb76cd** passes **885 unit
tests,450 ordinary GameTests and73 entrypoints**, followed by **two actual
BCC/Sodium clients on the same remapped game/observer bytes**. Every test and
client exit passed;40 fresh PNG and10 depth arrays are preserved. This is a local
verified increment, not full-specification or release acceptance.

The production delta after522ee07 is the reviewed native deferred-fluid manager,
its normal scheduled-callback observer and ordinary initialization. Work is keyed
by full signed XYZ and fluid family, capped at4096, coalesced and fairly inspected
16 entries/tick. No age limit deletes valid unknown work. Actual loaded source
and target availability precede handoff to Minecraft's normal future scheduler;
only `isQueued` acknowledges it. Invalid bounds/replaced material are distinct
from temporarily unknown cubes. No direct fluid tick, forced generation or new
fluid simulation is introduced. Stock worlds keep their original scheduler.

Component source704 separately passes19 new units and two dedicated Vanilla-
profile STOCK controls. The unchanged real native HALO scenario reproduces the
old failure at7ca, then passes with704: unknown edge291, target FULL297, ordinary
callback300, target385, PASS387 within the original400-tick allowance. Those two
dedicated STOCK controls are not counted in the ordinary450 suite. Cold unknown
writer/reader work retains its separate artifacts and acceptance boundary; see below.

| Actual remapped artifact | SHA-256 |
| --- | --- |
| Game | `7e35d702e098b780d7499645bdf1a5f73524e8f195a0a63f4f5a198cb9b5f064` |
| Observer | `c7a7602a526ad1fc870a4682b2af6db0625509e673f0a13984978ffa033395a4` |

The first client repairs a saved black surface cell atY2301 after normal native
arrival: server FULL104, both source/client FULL108, final121, zero fixture edits,
zero post-baseline external mutations and zero dropped events. Independent
[Python decoding](EVIDENCE/surface-byte-readback-68fb20a.py) verifies both actual
5121-byte network payloads and the preserved compressed L0 disk cell: black2301
becomes GOLD2301. These frames are context, not terrain-smoothing proof.

The second client passes all six Native/PS1/Debug/window-resize/restore scenes:
world nearest-grid1.0, five palette/dither references with RGB agreement1.0 and
max channel error0, nearest-depth delta5.960464477539063e-8, every GL error0.
Eight mirror panes form one reflected surface. Native/PS1 gun pixels166/166
exceed125, with identical camera/modules and projected-vertex delta0. Root also
directly inspected the fresh resized PS1 frame. This software MesaGL3.3/Sodium
proof is not an Intel hardware benchmark or full Fabulous qualification.

[Compact evidence](EVIDENCE/integrated-68fb20a.zip),4,789,015 bytes/132payloads,
SHA-256 `357859ba1fdc657ac0944e93084dca5e2973c2f1aae8a139dfe292a161725ae0`,
passed every payload SHA and ZIP CRC. [Metrics](EVIDENCE/integrated-68fb20a-metrics.json)
and the raw first logs/XML/helpers/JAR bindings remain exact. Complete closed
GameTest and native worlds were preserved separately with every-file SHA/CRC
and exclusive `lockf` before reclaiming only their disposable raw copies.

Both latest specifications, PS1 and direct Dynamic Boarding/Create-style
requirements remain open in the99-task ledger and21-packet overlay. Unaccepted
GUI preview, friendly modular AA, new cloud rendering, climber improvements and
mission runtime stay isolated; none is inferred from this integration. The
existing hostile station is not player AA. Full cubic parity, general3D AI,
arbitrary moving-body Boarding, whole-city destruction, performance/endurance
and remaining content are not closed. No source or binary was published.

## Separate actual cold-unknown continuation

The [cold writer](NATIVE_DEFERRED_FLUID_COLD_WRITER_330E9A7_2026-11-17/REPORT.md)
stops with the source FULL, receiver UNKNOWN, ordinary water queue empty and one
actual saved32→31 job. The [fresh reader](NATIVE_DEFERRED_FLUID_COLD_READER_330E9A7_2026-11-17/REPORT.md)
loads the exact93 writer files minus session.lock. Its sole job restores at tick1,
waits while all three cubes remain UNKNOWN through89, hands off at FULL90, and
normal callbacks95→100 continue downward to target177:81/400ticks. No source or
shaft edits or manual rescheduling occur. Actual final saved metadata is empty.
This uses the distinct704/330 game/observer pair, not the canonical whole JAR.
Independent [actual class parity](EVIDENCE/deferred-fluid-class-parity-68fb20a.json)
confirms all14 fluid-manager/mixin entries are byte-identical between them.
Lava, multi-Y/general BlockEntity/light parity and the complete S02 task remain open.

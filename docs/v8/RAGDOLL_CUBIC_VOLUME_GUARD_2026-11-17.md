# Ragdoll native cube availability — reproduced negative

The corrected native observer on exact source `fcf17b76f756b67b9c9537357a711c814f5c8941` reproduces a ragdoll entering a Y cube that the real BCC backend still considers unavailable. This is separate from the retained first `20fb873` observer linkage failure.

## Actual native negative

Compilation and provenance verification pass. The game JAR remains the verified `5839062` baseline, SHA-256 `cf3701b529b21633a8a3e910e6755d006d97d6349e8938d7701c82671027138d`; the remapped observer JAR is `f553e766833b0a752fdd06dce61cf3ebee08766fb3e704e3031b58251aab27bf`.

In the actual BCC0.1.1/GL3.2 world, the naturally loaded parent column is X=-4,Z=5. CubeY8 is FULL; cubeY9, beginning at blockY144, is DECORATED, already generated/materialized but not FULL. `WorldAccess.isVolumeAvailable` returns false and `readIfLoaded` is empty before and after the production step. This case concerns a non-FULL volume, not a completely absent cube.

The loaded-air control moves upward0.084671875blocks. A separate real five-part scout starts wholly inside available air at COM Y143.5 with upward physical velocity40blocks/second. One explicit call through vanilla `Entity.tick()` dispatches to the production ragdoll implementation. Its COM ends at Y145.240803125 and all five physical parts occupy unavailable cubeY9. Entity UUID, registration and simulation ownership remain unchanged.

The native status hash, loaded/materialized counts17/17, pending818, in-flight16 and temporary-viewer/region counts0 remain identical across the read queries and production tick. The observer adds no preload, ticket, block write or native metadata mutation. The only recorded violation is entry into an unavailable Y volume. The raw native exit is255, marker validation1, with an explicit `failureKind=GAMEPLAY` marker. Both temporary rigs are discarded before another normal world tick.

The cause is local to `RagdollEntity.insideLoadedWorld`: it checks global height and loaded X/Z columns but does not require the occupied Y cubes to be FULL. The approved next correction queries the existing `WorldAccess.isVolumeAvailable(D6ChunkPos)` contract for occupied XYZ cubes, preserving height/border checks and the existing rollback policy. No production change or positive result is included in this negative checkpoint.

## Evidence and limits

[ragdoll-cubic-volumes-fcf17b7-negative.zip](EVIDENCE/ragdoll-cubic-volumes-fcf17b7-negative.zip) retains original logs, crash report, raw failure marker, input/build hashes, exact source and JARs, launcher helpers and the complete closed disposable save, with verified CRC and per-file SHA-256. The first observer-failure archive remains immutable. Only a completely archived, hash-verified closed save may be removed to reclaim space.

This is an atomic production-tick reproduction on the real server owner thread. It is not natural multi-frame entity streaming, continuous collision detection, native visual acceptance or full S08 completion. No shared D6 solver, force/unit conversion, ticket or loading code was changed. Work remains local.

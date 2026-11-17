# Native ragdoll Y-volume negative — first observer attempt

Test-only source `20fb8736d65a42b7ee71b8867329ea274df94506` adds an isolated native observer and bootstrap flag. Production source is unchanged from integrated `5839062`. The intended case observes a naturally loaded column with an unavailable adjacent Y cube, runs a loaded-air control and then attempts one atomic production ragdoll tick across the boundary. It adds no preload request, ticket or block mutation.

## First result: observer ABI failure

Compilation and artifact verification both pass, exit0. The freshly built game JAR matches the integrated baseline exactly:

`cf3701b529b21633a8a3e910e6755d006d97d6349e8938d7701c82671027138d`

The native observer JAR is:

`84b6870cf0214b4a12b59ff71bbc232bd2278cb0f82ebecc5ea336c8d6f54b7e`

The first actual BCC/GL3.2 launch exits255; independent marker validation exits1. It stops with `NoSuchMethodError: void RagdollEntity.tick()` in `NativeRagdollVolumeCheck.exercise:168`, at the loaded-air control's direct subtype tick call. That inherited/overridden call did not receive the vanilla intermediary method name in the remapped observer. The boundary ragdoll's production tick is not reached and no result marker is produced. This is an **observer linkage failure, not a reproduced gameplay defect or a pass**. No production fix is justified by this run.

The next fixture-only correction is to invoke both ticks through the mapped vanilla `Entity` receiver. A linkage failure may be recorded as an observer precondition, but must still fail the gate. No automatic retry was performed.

## Preservation and scope

[ragdoll-cubic-volumes-20fb873-observer-failure.zip](EVIDENCE/ragdoll-cubic-volumes-20fb873-observer-failure.zip) retains exact source/patch, build and launch helpers, source/input hashes, both original JARs, raw compile/native logs and exits, launch inputs, the crash report, classification and every file of the closed disposable save. ZIP CRC and every manifest SHA-256 are independently checked. Only the archived closed save may then be removed to reclaim space; the archive records its original paths. The initial disposable seed world remains untouched.

Only `-Ddrmd.gate.ragdollVolumes=true` was enabled. Presentation and other native gate flags were absent. This attempt concerns an explicit atomic call of the production tick in a real native world. It does not prove natural multi-frame streaming, client visuals, continuous collision coverage or broader S08 acceptance. The earlier field-strength fix and full772-unit/391-GameTest verification are separate evidence. Work remains local.

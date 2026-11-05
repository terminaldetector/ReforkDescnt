# Physical wreck orientation follows the destroyed drone

Production source0217840a91ec3dc21c421e77d93ca50f743b5c14 replaces the identity quaternion used by spawnFromDrone with the same normalized yaw/pitch/bank basis used by the visible flight hull. FlightHullPose is common pure math; the client adapter delegates to it. Existing spawnScout callers retain their identity default. The existing RagdollSimulation, constraints, momentum/impact handling and component save format remain in use.

Three actual lethal-damage tests first failed on3b37ea8: heading-only ORB, vertically flying ROCKET and banked/pitched JET lost their physical body axes. Each now checks every part's initial world position and nose/top basis immediately after the real damage/onDeath/replacement path.706 unit and272 GameTests pass locally. This checks physical construction, not a manually rotated screenshot.

Native observer source0529e4c2150c15537e7b732da017c6fb304a55d0 passes on Mesa GL3.2 and Sodium GL3.3 at Y2400. Three authored types render first, then real genericKill damage produces exactly three registered wreck entities. All15 initial part transforms are checked before the first solver step. Every wreck genuinely ticks/moves, all three articulated models render with finite replicated poses, and no visible dead parent remains. Both clients exit0 and save all dimensions. Four fresh before/after frames are preserved.

Game JAR SHA256b92d246531531ac5891116991c17f0ff3387b1768aec1ae97ce8a187cc1790c1; observer SHA256a5fb28c6bc75ab45ed63e5f9d06d50ca98716b1315fbceffe3507027b93d18aa. EVIDENCE/drone-wreck-poses-0529e4c.zip SHA256860c905819c0b3de4b7745b074688b910cdc7558e83aa8c3b230ee2074c71ff5 contains exact launches/hashes, logs, first failures and passing XML/frames.

This intentionally does NOT close type-specific wreck geometry: all three replacements still use the legacy five-part scout rig. The native marker explicitly records matchingTypedRigGeometry=false. Their shape/scale matching, complete bestiary, universal ragdoll/3D AI and full v8 remain open. No accepted release is declared.

## Headless fixture investigation

The owned-ticket correction1001f1b still failed remote admission, now with diagnostic spawned=false/ticking=false/ages0. Local reproduction also failed for a different role, confirming a fixture-readiness problem rather than Artillery motion. A reset of Vanilla's300-tick empty-world idle timer alone was insufficient; the failed awake run is retained. Vanilla makeChunkEntitiesTickable additionally requires a radius2 FULL neighbour region.243ec0a preloads that halo before the existing readiness gate. The80-tick admission cap, five actual initial ticks,40 measured ticks and every motion threshold remain. Local272 and the remote Server GameTests step of Actions42089463622 pass; broader CI stability is not inferred from one success.

Source artifact13444312819/run42089463622 is saved/notexpired, digestsha256:5323dc58dc02c255d3ec9bbdee0799cf9e7fbf8348ea36d55a6219441cd37c00. The workflow was still running when checked; full Actions/release acceptance is not claimed.

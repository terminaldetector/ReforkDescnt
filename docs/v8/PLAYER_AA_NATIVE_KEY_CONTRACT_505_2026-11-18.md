# Native ordinary-player modular AA arming — frozen proposal

Pinned gameplay source: `5057931efce99156ffdc8a195e90b1a926907ed8`. Status: SOURCE CONTRACT ONLY, no native run or component proof inheritance. The main AA component already passed real server tests and the root full951/480 gate. This proposal tests the previously unobserved actual client input path.

## Admission and explicit limits

Use an actual ordinary Survival local player, real client/world, no current screen, no vehicle, no active generic ControlCore lease. BodyEditClient.eligible explicitly refuses DescentClientState.enabled and DroneCombatInput mouse ownership. Therefore first acceptance is ordinary movement only; 6DoF arming is OPEN. Capture this actual input eligibility in receipts; do not silently disable gameplay state mid-transaction.

Construct one actual BlockBody using the existing separate bearing/drive/ship_laser/lever/ballast blocks. Initial fixture construction, inventory stock and initial player positioning are declared preparation, not crafting/ore-obtainability or native player-construction proof. At most4 actual bodies. The new B2 generic-core scheduler bridge remains a separate candidate; absence of a core uses original505 routing.

## Physical input transaction

Use normal hotbar selection and actual press/release of USE, with the rendered wrench/mainhand identity and target cell visible. First ordinary wrench-link the real components if linkage is not supplied as declared fixture preparation; then wrench-USE the SHIP_LASER through BodyEditClient.place -> revisioned BodyBlockInteraction.Edit USE -> normal server validation. Select empty hand and USE the actual lever. Respect the existing4tick input cooldown and released buttons; bounded waiting does not write readyAt/capacitor/pose/solver state.

Read-only probes may correlate physical key, normal target ray, emitted packet, receiver transaction, current cell revision, principal UUID and decoded claim, lever power and real emitted energy bolt. They must not call toggle, claim read/write, BodyBlockInteraction receiver, Item.use, emitter, factory or scheduler as a fallback to satisfy player arming. No claims injected through NBT or direct API. No active transaction teleport, forced follow, manual tick or synthetic held state.

## Combat result and controls

After arming, emit an actual registered ROCKET through its real normal weapon action against a real Survival target/body; preserve all ordinary speed, splash, fuse, allegiance and payload rules. This declared combat fixture is distinct from autonomous siege/novice balance. The defended transaction must observe a real moving energy bolt and relativeCCD-driven hostile missile termination before payload harms protected material/player. A lever-OFF/unarmed transaction retains actual material impact/payload as the negative control. Friendly/unknown owner rejection retains server component acceptance but is not inferred as native coverage unless explicitly executed.

Initial body creation/retirement is allowed only between completed transactions with held0 and no live transaction projectiles. Do not delete or disarm an incoming projectile to pass. No resistance/invulnerability/health enlargement, shot nerf or new firing clock. Keep normal20HP Survival semantics; supplied fixture items do not establish minimum crafting progression.

## Evidence and result classification

Freeze exact source/JAR/observer/runner/input hashes and bounded transaction/wall deadlines before a separately granted native lease. Capture real client frames with target+wrench/lever, physical input receipts, packet/receiver sequence, actual claim/cooldown/source identity, real projectile flight/payload and terminal result. Source/JAR must be one launch; fresh markers only. Missing key/packet/claim admission is PRECONDITION_UNOBSERVED, instrumentation/startup error is technical failure, observed admitted failure is gameplay failure. No direct-use fallback, sustained replacement for a short pulse, adaptive deadline or self-retry.

This does not close 6DoF mouse ownership, vehicle/piloting, offline owners, PvP/arbitrary alliances, automatic motor aiming, beginner resource/raid balance, native fracture/reload, generic-core union, renderer/hardware matrix or full U04/S12. Later B2 union must separately retain claim/refusal priority and the same emitter/cooldown budgets.

# Video audit v3: source implementation ledger

Input ZIP SHA-256: `3fa35c5670e409f879ff6db7f7211270574f182e15a5a3abf7eed34b80025270`.
Received 25 October 2026; same 19:29 video, expanded requirements UG-022..031.
The recorded JAR version remains unknown (user confirmed); this source starts from
`a39e91d2874d6512a4507d364212cb0abf227b37`, version1.1.14-audit.1.
Attached requirements are context; the user's latest request controls the work.

The user now says GitHub limits are exhausted and asks to work here with source code.
Use local commits and downloadable source/bundle/patch/report backups. Do not push or
trigger Actions during this pause. These backups are not GHA checkpoints or tested JARs.

| ID | Scope | Source status / acceptance still required |
|---|---|---|
|022|Mirrors|Native reflection path exists; OFF in video proves neither failure nor success. Audit resource lifecycle/clipping; actual ON/OFF not run.|
|023|Portals|Pairing, separate camera and traversal exist; review independently of mirror transforms. Actual destination/traversal checks not run.|
|024|Weapon visibility|Confirmed unconditional vanilla hand suppression while flight is enabled, even for non-cluster items or weaponView OFF. Fix required.|
|025|Item models|Previous16 asset changes retained. Add registry/resource/texture/display inventory and warnings, actual client still pending.|
|026|Legacy|Reference/registration audit only; retain saved IDs and live mechanics. No blind deletion.|
|027|Clusters|Counts7/14 already exist; inspect exactly-once/reload/owner/lifetime/collision and work bounds.|
|028|Drill/melt|Whole-block excavation exists; smooth circular mesh/collision not implemented. Review material processing, permissions, loaded chunks and budget first.|
|029|Construction|Confirmed source paths place without consumption; central server transaction and lifecycle cleanup required.|
|030|Hybrid default|No separate profile toggle; implement persisted ON default, preserve explicit OFF and independent subflags.|
|031|Expanded default|No explicit authoritative profile; define persisted server/world policy and actual hooked behaviors before claiming support.|

PriorP0 fixes and365-frame harness remain as in docs/audit/2026-10-24/STATUS.md.
Fabric compilation is blocked locally by absent Loom1.10-SNAPSHOT; runtime evidence is
NOT RUN. Do not assert all31items fixed or use old1.1.13images as proof of this revision.

## M construction source changes (local; runtime NOT RUN)

Tool, surface, ray and batch construction share one server transaction: loaded/bordered/in-height
target, vanilla modification permission, valid replaceable/collision-free state, successful write,
then exactly one decrement from the selected offhand/hotbar stack. Survival has no invented iron
fallback; creative retains its fallback and does not consume. Batch actions examine at most256
cells (the existing purple tier cap). Full ship-forward and actual local-gravity up are shared.
Construction/draft state is cleared at death,respawn,dimension change,disconnect and server stop.
Block-entity/container item data cannot yet be transferred by this direct path; such stacks are
rejected intact to avoid silently losing their contents. No claim-mod integration exists.
Four real GameTests cover consumption,hotbar,rejected targets,batch duplicates,exhaustion and
creative collision. Added but NOT RUN: Fabric/Loom remains unavailable locally.

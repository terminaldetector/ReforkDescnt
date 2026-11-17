# Verified integration: ordinary startup and physical missile interception

Exact source `6b50273c6896433a2d5caf68991150126e897245` passes **843 unit tests,
442 ordinary GameTests and 71 entrypoints**, with zero failures, errors or skips.
The full offline Java 21 build/remaps closed successfully in 5m32s. Two subsequent
actual BCC/Sodium clients pass on the **same game/observer JARs**, preserving
40 fresh PNG images and 10 actual depth arrays.

This adds physical missile interception to the five-feature integration: reusable
linear mechanisms, PS1 palette/dither, native surface-cache arrival repair,
scout/wingman investigation and stationary point defense. The preceding four
increments and their limits are in [the ae1bd82 report](MULTI_AGENT_INTEGRATION_AE1BD82_2026-11-17.md).

## What changed

The existing point-defense block now emits a real finite KINETIC projectile at
an approaching, already loaded hostile missile. The shared weapon factory retains
its original owner-based API; an explicitly validated stationary-muzzle overload
handles this ownerless block. Relative swept-box contact resolves both physical
segments before nearer world, actor or rotated-hull cover. Ordinary missile
impacts remain immediate; only tagged defensive rounds defer their own terminal
impact until this world tick ends. The original missile fuse, blast and payload
still execute once at the real pair contact, rather than being safely erased.

Interception works in both actual entity-tick orders. Newly born rounds cannot
intercept an earlier segment in their birth tick. Acquisition retains range 12,
six-tick opportunities, the existing station threat predicate and finite work:
96 candidates, 32 detailed candidates, 256 active rounds and 256 source entries.
Per-opportunity geometry budgets and unload cleanup are explicit. Unknown source,
owner or target identity fails closed without materializing a column.

The unchanged natural baseline previously failed with no emitted defensive round.
The first component candidate passes six new math tests and 41 GameTests,
including 29 retained weapon/portal cases. See [the component report](POINT_DEFENSE_INTERCEPTION_2026-11-17.md).
The production registration is a separate commit. Both test bootstrap calls were
removed; **only `DescentMod.onInitialize` registers the service**. The natural
two-case fixture is again byte-identical to its original negative. All 12 new
cases pass inside the subsequent ordinary 442-case integration suite.

This retains the old ownerless hostile-station policy. It does not turn the block
into player-owned friendly AA or replace the user's Create-style modular turrets.
Player-owned mechanical AA, ground rocket infantry and full siege balancing
remain distinct work.

## Exact artifacts and client results

| Artifact | SHA-256 |
| --- | --- |
| Game JAR | `5ae9d0cae2223e110f993a23554010ad2159fd45ca3e9dbe7e1ddb2e7ea2bb68` |
| Cubiccheck observer | `c7a7602a526ad1fc870a4682b2af6db0625509e673f0a13984978ffa033395a4` |

No-edit native surface arrival passes: first server FULL at tick 123, the high cube FULL on server and client at 130, final fresh receipt at 141, **18 ticks after first FULL**.
Actual raw packets and compressed disk independently decode from height2301/black
to height2301/GOLD. Fixture edits, post-baseline outside-native mutations and
dropped observations are zero. These two frames are context, not terrain-smoothing
or rendered-seam acceptance.

All five PS1 colour references have RGB agreement 1.0 and maximum channel error 0.
Maximum nearest-depth delta is 5.960464477539063e-8; the smallest dither-sensitive
domain has 8175 pixels. All six profile/resize scenes retain native HUD and GL0.
Eight mirror panes merge into one surface; reflected GOLD is 27772 pixels at
640×360→1280×720 and 15837 after 320×180→960×540. Actual projected weapon visibility
is 166 native /166 PS1 pixels against the unchanged 125 minimum, with identical
camera/modules and zero projected vertex delta. The two final resized/unresized
images were directly inspected. This is software Mesa GL3.3, not a hardware or
full Fabulous gate.

## Preservation and remaining work

[Evidence ZIP](EVIDENCE/integrated-6b50273.zip): 138 payload files plus SHA manifest,
4,702,250 bytes, SHA-256 `7b1124ef07eae6dae167fe6e1a14851eaa9ff0ec04ed78c88f33035b3fb7341a`.
All payload hashes and ZIP CRC pass. It includes full fresh XML/unit archive,
executing helpers, both native outcomes, raw packets/cache, 40 PNG/10 depth files
and the initial packaging-helper dimension rejection. That helper initially
omitted valid 640×360/320×180 source-buffer images; this was corrected before ZIP
creation without changing or rerunning game/client checks.

The closed full GameTest world is separately archived: 1261 files/239,502,999 bytes,
archive SHA `69f82cbba5a5d77471ce299447e19b79ae34e9a6d3388b7f0e06aab6f1517691`.
Only closed disposable saves were reclaimed after every file hash, ZIP CRC and
exclusive session lock were verified. Exact tested JARs remain in
`work/multi-agent/integrated-6b50273/artifacts/` and the game binary is packaged in
[the local snapshot](../../dist/local-snapshots/6b50273/README.md).

The [untouched-work map](UNTOUCHED_IMPLEMENTATION_PACKETS_2026-11-17.md) adds 21
concrete implementation packets and distinguishes absent work from existing,
partly accepted or actively failing code. Master v8, the PS1 renderer requirements
and direct Dynamic Boarding remain in scope. The live ledger remains
**3 completed /27 in progress /69 pending**; this integration closes no whole task.
Cloud occlusion/far visibility, arbitrary-surface AI and legitimate local jumps,
occupied moving mechanisms, connected-body portal transitions, loadout preview,
Survival siege/content and hardware/performance acceptance remain open.

No experimental climber, city command or cloud implementation enters these game
bytes. No GitHub publication, Actions, whole-spec or release acceptance occurred.

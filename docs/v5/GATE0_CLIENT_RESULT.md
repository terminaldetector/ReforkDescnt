# GATE-0 client: native cubic authority, 27 October 2026

**Full GATE-0 remains incomplete. Native skylight reproducer: fixed in the 1.1.18 candidate.**
The strict production-client subset passes on source `6494541`, without the experimental relight
probe. It includes recovery through two lower cubes and vertical unload/reload. Save reopen and
Sodium A/B are tracked separately below as they complete. This is not a declaration that
Master v5 world migration or GATE-0 is complete. Original saves and the baseline branches are intact.

## Actual runtime

An official production Minecraft 1.21.1 client, Fabric Loader 0.19.5, Fabric API 0.116.15+1.21.1,
unmodified Better Cubic Chunks 0.1.1. The original reproducer used DRMD 1.1.17-mirrors.1 from the separately
verified mirror branch; its SHA-256 is
`b8e23d5cee6d6bc2c40620c9787e57576158028b2082d65ef2033c5c65d0df85`.
The candidate now incorporates the tested collider, native mirror and licence commits into this
world worktree, followed by the optional native skylight repair. Its version is 1.1.18-cubic.1.
The small `drmd_cubiccheck` JAR is a separate, remapped test mod and contains no DescentMod class.

The closed BCC artifact is installed unchanged, outside Git. Its Loom 1.17.21 metadata is retained.
Our Loom 1.10.5 builds only the vanilla/Fabric-facing harness; a production KnotClient classpath
avoids remapping that upstream artifact. Every official Minecraft library is checksum-verified;
assets use the verified official index. No Immersive Portals is installed in the runtime.

A new dedicated cubic-preset world was originally created with seed 261026. Each client attempt
copies that disposable world; no old vanilla world is silently converted. Runtime inputs and mod
hashes live in each `build/cubic-client-*/inputs.json`. Config uses render distance 4, simulation 5,
heap 2 GiB and upstream vertical distance 8. The client confirms native TALL packing and dimension
bounds minY=-16384, height=32768. Graphics is Mesa llvmpipe via Xvfb, not the user's Intel hardware.

## Verified subsets

- Old server-created chest at (8,2150,8) and barrel at (8,-512,8) arrive as the correct client BEs;
  their saved names are verified on the authoritative server. The initial native region save/reopen
  is documented in GATE0_SERVER_RESULT.md.
- A real sign created beside each container transmits text NBT to the client at those coordinates.
- Normal forward input and DRMD's orientation API move upward across Y=2160. A server sample records
  Y=2165.2385180867363 and positive flight velocity Y=14.072072008242332 before fixture stabilization.
  No position stepping is used for that flight; initial high/negative setup uses explicit teleports.
- Both native marker states on Y=2159 and Y=2160 are present, and native collider raycasts hit the
  scene. Native `ClientCubes.isCubeLoaded` and BE removal confirm the high cube is unavailable after
  travel to the low cube while the low cube remains loaded.
- Completed game frames show real redstone geometry, the chest and the synchronized sign at Y=2150,
  and the negative cave scene. High marker pixels are 206765 in `client-production-marker.log`.
- In the expanded test, water from (16,2162,0) reaches (15,2158,-1) through Y and X/Z cube boundaries,
  driven by normal fluid ticks. Server and client both observe water.
- Glowstone at (16,2160,-3) produces block light 13 at (15,2159,-3), across X/Y cube boundaries;
  removing it reduces that light. Roof placement reduces skylight below 15 on both sides.

The original 1.1.17 runs only passed subsets. The strict 1.1.18 run now produces a successful
`CLIENT_SUBSET_PASS_FULL_GATE_PENDING` marker with eleven checks and 698 completed frames.
Its server flight sample is Y=2165.45406582087, velocity Y=22.121829796214364. Evidence is retained in
`work/gate0/verified-native-priority/` and `client-production-native-priority.log`.
Logs for the marker subset are `work/gate0/client-production-marker.log`; expanded boundary cases
are `client-production-light.log` and `client-production-isolated-sky.log`.

## Original reproducer and correction

1. In the loaded native cube containing (-8,2160,-8), confirm air and sky light 15.
2. Place a 3x3 opaque roof centred at (-8,2161,-8), x/z offsets -1..1. Confirm diminished skylight.
3. Remove all nine roof blocks with normal server block updates. Do not change light values by hand.
4. After 400 client ticks, both client and server still report 13 at (-8,2160,-8), with air at the
   probe and directly above it. The unchanged assertion requires recovery to 15 and fails.

The first roof overlapped the fluid enclosure. That fixture interaction was removed; the isolated
roof still fails identically. The failure/crash and world snapshot remain under
`build/cubic-client-isolated-skylight-failure` and `work/gate0/client-production-isolated-sky.log`.

A controlled experimental probe invokes public native source refresh, vanilla checkBlock,
propagateLight and section status APIs. Source height remains 87 and light remains 13. This is
recorded in `client-production-relight-probe.log`. The subsequent occlusion consistency probe is
explicitly flagged `drmd.gate.tryNativeRelight`; even a successful probe will not certify an
unmodified runtime or a shipped fix. No upstream binary or foreign implementation was copied.
The upstream GitHub source URLs currently return 404; public runtime API signatures are available.

The successful experimental probe established that the cached occlusion height was 2162 after the
roof became air, whereas the native loaded source height was 87. It did not constitute a shipped fix.
The production correction and all failed intermediate attempts are documented in
`NATIVE_SKYLIGHT_REPAIR.md`. In the accepted strict run no `drmd.gate.tryNativeRelight` property is set,
and the harness never repairs light or writes light arrays itself.

## Corrected test assumptions / evidence limits

Vanilla containers inherit empty initial-chunk NBT and no update packet; their private inventory/name
is not a valid chunk-packet expectation. The first client test timed out on that incorrect assumption.
It was replaced by authoritative server-name checks plus a sign, whose NBT really is synchronized.
The first early screenshots did not establish visible geometry. A later red-pixel check could count
skin pixels; manual inspection rejected it. The accepted marker run hides HUD/hand in the fixture,
uses a specified camera direction, completes real game frames and requires a stricter red ratio.
These fixture settings are not a permanent fix for the separate held-item clipping requirement.

Native telemetry has nonzero `droppedWrites`; its broader meaning and worldgen integrity remain
unverified. It is neither declared data loss nor dismissed as harmless. All successful marker
round trips are narrower evidence. Some worldgen reads report unloaded columns treated as empty.
Default gameplay-content parity, additional seeds, corruption recovery and old-world migration
remain pending. This candidate does not certify the original user's 193-second stalls.

Exact Sodium 0.8.13+mc1.21.1 was fetched from official Modrinth release SMxNOGZ6 with its published
SHA-512/SHA-1 verified, SHA-256
`3d43c14985a4deb19c654aad3393b454cf57e1ebcbba86c4ae6a98dfc60eca2d`.
Sodium A/B and GL 3.2 runs are pending the baseline lighting repair. No claim about real HD 4400
hardware or full NoCubes/3D LOD/mirror integration is made by this client subset.

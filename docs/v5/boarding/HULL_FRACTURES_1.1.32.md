# Connected hull fracture —1.1.32-fragments.1

This stage extends BlockBody/D6. It does not create another physics engine or dimension.
The full master specification, Boarding12 and UFO9 acceptance remain OPEN.
Source/JAR certification belongs to the later exact-source CI and installed-client reports.

## Implemented

- Atomic local-grid damage detects newly broken six-face connections. One connected remnant
  retains the original body identity; each detached connected component has its own existing
  BlockBody, compound collider and D6 mass/inertia. No stationary rigid body per block.
- Pre-existing disconnected legacy blueprint cells do not shatter after an unrelated edit.
  Removed bridge regions share a connectivity relation so simultaneous cuts cannot duplicate
  an intermediate component. Ties choose a deterministic largest surviving part.
- Material points keep their world positions and velocity fields. A fragment's COM receives
  v_body + omega cross radius; its world inertia receives the original instantaneous spin.
  Sum of surviving spin plus orbital angular momentum obeys the parallel-axis relation.
- The parent applies one combined local-grid batch and preserves its fixed grid pivot. Its
  existing journal/baseline and dirty16-cube geometry rebuilds remain in use. Children receive
  independent sparse grids, UUIDs and pose state; no temporary world-block copy.
- Fragments are prepared before spawning and parent mutation. A spawn refusal discards staged
  children without altering the source grid. A later mutation exception restores parent state
  and removes all spawned children. Repeated damage is a no-op rather than another fleet.
- Translation limit expands from50 to512m/s and the real collision subdivision budget from256
  to512. Slow/stopped hulls still use their actual speed and need only the steps they require.
  Loading prediction and owner replay use the same velocity bound. A128-cell rotating bar's
  64/65m/s component velocities remain intact through the next loaded physical tick.
- A corner-chip connectivity probe may finish after visiting fewer than64 cells of a32768-cell
  solid. Input snapshots and mass/grid bookkeeping still scale with hull size; this is not an
  assertion that the entire edit is constant-time or allocation-free.
- Each part stores its own local grid, quaternion, COM pose, linear/angular momentum and UUID.
  Unspawned NBT inspection does not enroll or duplicate a live spatial-index entry.

Relative entity packets quantize coordinates to1/4096 of a block. A frozen contact sample
may wait inside that wire-precision bound for the normal absolute update; errors larger than
that bound fail immediately. Final pose samples still require the original error below1e-5.
The wait and its maximum observed error are recorded separately, not counted as precise passes.

## Admission and remaining limits

A single atomic edit is refused before material changes if it would create more than256 new
physical components, or if a new/retained COM would require a speed above512m/s. Material,
revision and momentum remain unchanged on refusal. This is a real remaining limit: no queued
large-destruction scheduler or fragment simulation LOD is implemented by this stage.

The existing2rad/s angular limiter remains dissipative above its bound. Artificial passenger
gravity stays with the original body until physical ControlCore/module ownership exists;
detached debris inherits ordinary body-gravity mode, not a duplicated artificial field.
Functional block entities, joints and module separation are not claimed.

## Verification scope

Server regressions cover low-speed three-axis fracture, surviving material/momentum, atomic
multiple cuts, duplicate prevention, excess-fragment refusal, high inherited speed through a
whole physical tick, NBT state and velocity admission. Pure tests cover bridge topology,
legacy isolated cells, multiple cuts, reconnection within a batch, local corner probes and
expanded corner-travel bounds.

`runFracturecheck` reuses the actual contact renderer and adds three scenes: fracture halves,
fast fragments, and saved fragment state. In the native BCC variant the128-cell test hull first
holds its complete footprint with production volume leases. Both sides then execute the
real material edit and a loaded physical tick. The solved samples are held afterwards for
network/render comparison. Clouds are disabled in this disposable scene; the two fast COM
regions must contain visible material pixels, not merely successful render callbacks.

The NBT scene is an independent entity-state round-trip, not a full world close/reopen test.
Markers explicitly report fullWorldReload=false. No player-on-fragment handoff, multiplayer
battle, mechanism/controller persistence or full-world/module reload is certified here.
A first client startup timeout and the first visually cloud-occluded fast frame remain in
first-failure evidence; neither is counted as a passing client proof.

Local-grid mutation/mining is wired in this stage. WeaponCore projectile/explosion hits
still provide their existing physical impulse; per-cell combat damage and explosive batch
selection are separate unfinished integration work.

Next acceptance work remains active: player/contact transfer onto separated parts, movable
joints and module ownership, functional blocks and ControlCore, physical large UFO conversion,
full3D AI and the bestiary, dense destruction scheduling/LOD, and dedicated multiplayer and
whole-world/module save/reload. Publication of this stage does not close that work.

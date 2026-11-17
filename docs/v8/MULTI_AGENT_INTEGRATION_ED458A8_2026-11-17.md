# Linear stage and PS1 colour integration — exact ed458a8

Exact combined source `ed458a8b0ceaaa06b6ec153d2c051e7dcb1b6c5c` passes
**828 unit tests and423 ordinary GameTests across67 entrypoints**, with zero
failures, errors or skips. One subsequent real BCC/Sodium software-Mesa client
passes the colour/depth reference, six profile scenes, merged mirror, native HUD
and actual projected viewmodel checks on the same two remapped JARs.

This integrates two concrete increments from the primary specifications:

- Separate player-built linear guide and drive blocks configure the existing
  bounded SLIDER joint. Wrench assembly, rolled extension/stops/retraction,
  finite force, wrong/competing sources, actuator/material destruction and
  momentum-preserving release have five new regressions. Twelve existing rotary
  cases remain green. See [the component report](PLAYER_BUILT_LINEAR_STAGE_2026-11-17.md).
- Configurable PS1 colour quantization and scene-texel Bayer dither operate on
  the existing completed world texture before native overlays. Native default,
  raw bypass and independent physics/hybrid settings are retained. Original
  negative and both separately verified renderer backends remain in
  [the colour checkpoint](PS1_COLOR_CHECKPOINT_2026-11-17.md).

Both primary specifications and their compatible retained clauses are mapped in
[the99-task crosswalk](TWO_SPEC_COVERAGE_2026-11-17.md). Review data is not runtime
acceptance. Only S05-T02 changes from pending_reverification to in_progress;
vertex snap and affine UV remain open. Current ledger:3completed/25in_progress/
71pending. No whole-task, whole-specification or release acceptance is claimed.

## Exact executed artifacts and gates

| Artifact | SHA-256 |
| --- | --- |
| Game JAR | `3e98be00df312efccb1bc73ed6d6edc7475750c3960aced058a66ad170ef3e70` |
| Cubiccheck JAR | `ffd6c6da18201a51fa17ea948eab1847e1650fe578d6118478112a97d47b4093` |

Java21/offline/no-daemon compilation, all unit/server cases and both remaps close
successfully. The ordinary resource stage starts clean and matches the committed
manifest exactly; prior XML is retained separately and the new report must have
a post-invocation timestamp. The ordinary TestServer enables discoverable packs,
including the Advanced registry pack; it is not the separate Vanilla-64/320 test
profile and does not reactivate retired generation code.

The client uses actual BCC, Sodium0.8.13, Mesa GL3.3/GLSL330 and the same game/
observer bytes. No source or executing helper changes occur during either gate.
Five colour states—raw, palette32, dither32/.5, raw-restored and resized dither—
all reach100% RGB reference agreement with maximum channel error0, finite depth,
maximum nearest-depth difference `5.960464477539063e-8`, nearest world groups1.0,
restored readback state and GL error0. Initial640×360→1280×720 and actual resized
320×180→960×540 are both observed. The smallest sensitive dither sample count is7957.

All six profile/window transitions and target/Debug exception restoration pass.
The real eight-pane merged mirror retains27741 gold pixels in PS1 and15837 at
the resized target. Debug's projected world-edge contrast is327/346=.9450867052.
Full-resolution HUD and projected gun166Native/166PS1 (minimum125) remain visible,
with unchanged camera/modules and maximum projected vertex displacement0. World
nearest groups are measured before native overlays; the final composite includes
those deliberately full-resolution layers and is not falsely required to obey
the world pixel grid. Two actual final/profile images were directly inspected.

## Preservation and remaining work

[`EVIDENCE/integrated-ed458a8.zip`](EVIDENCE/integrated-ed458a8.zip) contains98
payload files plus the verified SHA manifest:38 fresh PNG,10 actual depth arrays,
unit XML archive, complete game XML, build/native logs/exits, inputs/stamp and
actual executing helper snapshots. Archive4516519bytes, SHA-256
`ed78390f1b1c10b89eb012e675a6ca5f23907a7aa85c748391fa59fba6cb8639`.
Every entry SHA and ZIP CRC was checked; the adjacent JSON provides exact counts
and artifact identity. Exact JARs are preserved under
`work/multi-agent/integrated-ed458a8/artifacts/`.

The closed disposable GameTest world is separately preserved:1151files,
167380556bytes→20393783bytes, archive SHA
`d3caeb31998552511017d5a808ca197ab761193d2e3d79568994625509346fe5`.
Its per-file hashes/CRC were checked before reclaiming that closed fixture copy;
this is preservation, not save/reload gameplay acceptance.

Actual rider/child-platform boarding, complete mechanism/cold-restart acceptance,
vertex snap/affine/fog/clouds, volumetric farLOD and the broader specifications
remain open. The separately successful native cube-arrival repair, squad AI
increment, wall movement diagnostics and city runtime are not included in ed458a8.
Physical hardware, endurance, remote multiplayer and final performance gates are
also separate. Development remains local, with no publication.

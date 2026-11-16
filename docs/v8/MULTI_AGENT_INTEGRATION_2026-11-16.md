# Combined local development checkpoint — 16 November 2026

The combined gameplay, server suite and two real-client scenarios all use source
`5839062c3af8a57687b7e39f2f6d79ce5badf8a3`. All **772 unit tests and 391 registered
GameTests** pass, with zero failures/errors/skips and the complete **62-entrypoint**
manifest. Later evidence and Python-tool commits do not change these build inputs.
This checkpoint does not complete either master specification or Dynamic Boarding.

## Integrated behavior

- Vanilla Bee/Phantom/Ghast/Allay/Vex retain the flying gravity adapter instead of
  entering the ground mounting/teleport path. The original reproduced failures,
  three focused cases and sixteen related cases remain preserved.
- Low-health drones consider upper/lower and vertical retreat cover, including real
  rotated BlockBody geometry. Unknown terrain is not loaded by cover rays; work is
  bounded. All six new regressions are registered in the complete server suite.
- Native/PS1/Debug presentation choices and configurable internal resolution are
  integrated. PS1 uses nearest-upscaled world targets with native-resolution custom
  weapons, cockpit and HUD. This does not add the remaining PS1 artistic effects.
- A confirmed Survival machine kill can provoke a persisted, warned recon pair.
  Existing drones acquire the player naturally, lose sight behind solid cover and
  search the last observation; defeating the pair starts cooldown. Bounds, novice
  grace, deduplication, disabled-spawn rules and disk state restoration are tested.
- Ragdoll acceleration uses sampled field strength. The retained negative measured
  identical quarter/full acceleration; the correction passes all six axes at
  .25/.5/1/2/capped strength, no-field/removal momentum and the original rig cases.

## Exact artifacts and fresh clients

Game JAR SHA-256: `cf3701b529b21633a8a3e910e6755d006d97d6349e8938d7701c82671027138d`.
Separate observer SHA-256: `65c53432ffebb0b6b8a0143b3dc338e1720806a39e8723951774fd326f132693`.
Both actual staged mod files were CRC/hash checked against the successful build
receipt. They are identical across the full-suite build and both new client runs.

`ps1-integrated-5839062-sodium` passes Native→PS1→Native→Debug→resized PS1→Native
without a world reload. Eight fresh inspected images include matching pure-world
and final-overlay pairs. Real targets are 640×360→1280×720 and 320×180→960×540;
both pure-world nearest-grid ratios are 1.0, final HUD witness stays one physical
pixel, and all scenes restore the viewport/FBO without GL errors or fallback.
One merged mirror contains eight actual panes and reflects the gold wall behind
the camera. This current run is Sodium/GL3.3 software Mesa. The earlier GL3.2
proof on `703ea85` remains separately identified, not relabelled as this source.

`mounted-integrated-5839062-keys-sodium` passes actual use/sneak-key linking,
laser/rocket firing, both OFF, drive reversal and physical bearing release.
Five fresh images were inspected. The ordinary free-body fixture supplies no
joint/shots or follow teleport. It observes 19 lasers/10 rockets, zero muzzle
error, maximum launch-velocity error 1.05e-15, maximum anchor error .000316252m,
95 OFF ticks and preserved spin after separation. Timing-dependent shot counts
are observations, not a new weapon balance. The original five-frame proof is
also retained; none of its values are replaced by this run.

The [compact archive](EVIDENCE/integrated-5839062.zip) contains full logs/XML,
unit XML bundle, complete processed manifest, source/input/artifact receipts,
both native inputs/logs/markers, the thirteen fresh PNGs and exact local helpers.
It has 48 payloads plus a SHA-256 manifest, with all hashes/ZIP CRC verified.
Its checksum and parsed results are in [the receipt](EVIDENCE/integrated-5839062.json).
Closed disposable worlds were separately archived and byte/CRC checked before
reclaiming space; their preservation is not a two-process restore acceptance.

## Open failures and next work

Fabulous Debug on `09b50e0` is a real retained negative: only 3/346 projected
world-edge samples appear, with a black frame/fullscreen diagonal. Its narrow
Debug-only compositor correction is a separate candidate, not part of this
passing checkpoint. Sky/LOD black wedges remain plainly visible in the mechanism
frames. Natural-terrain smoothing, seams, clouds, PS1 colour/dither/UV/fog,
generic world anchoring/automatic aiming/economy/lifecycle, complete 3D bestiary
and all v8/boarding acceptance remain open.

Separate first negatives retain the actual gravity-item unknown-column load.
RAIL diagnostics expose duplicate same-tick test callbacks and a scheduler-map
crash: its first actual launch succeeds, but complete ballistic acceptance still
needs the corrected test driver. Retired legacy portal/dragon routing
fails six of eight focused cases. Neither unaccepted candidate inherits the
passing counts above. New actual crafting/repair tests are being prepared.

S16 has a separate source/artifact/observation collector with twenty Python tests;
it never manufactures missing performance data. Hardware benchmarks, larger
swarms and native metric production remain open. The independent 99-task audit
stays pinned to its historical `4b2e243` source rather than silently changing.
Development is local; no release acceptance, GitHub push or publication occurred.

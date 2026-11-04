# Existing UFO → physical BlockBody (work in progress)

This follows1.1.34 source5d5c2b8. It is not included in that certified JAR.
Registration `drmd:sky_ufo` and the existing `/d6 mega ufo` spawn route remain.
The current migration replaces its grid relocation, carried-entity shifts and solid envelope
with the existing BlockBody subclass/runtime. No parallel UFO entity or interior dimension.

Physical migration, actual enterable UFO client evidence, functional weapon/bay/reactor
modules, core capture, expanded interior, full world reload and UFO9 acceptance remain OPEN.
Source compilation or generic boarding evidence cannot certify these requirements.

The development version is1.1.35-ufo-dev.1. Its distribution gate rejects development
metadata; it cannot replace the accepted1.1.34 package. Initial local checks:671 unit,
229 server GameTests, including seven tests of the actual registered UFO. These cover one
physical identity/no terrain writes, a rotated real bay/deck, local mining, damaged-grid
entity NBT, retained material-point velocity and immediate reactor power loss/fragments.
The first reactor fixture incorrectly compared COM before/after removing material; its
failure is retained and the corrected check compares an unchanged material point and its
world velocity at1e-8 instead. A COM shift is physically required by this mass edit.

Native UFO mode reuses the real ship-gravity stand with the actual registered entity.
It does not place the small generic fixture's floor/ceiling in the world. A bounded physical
attitude controller drives the larger inertia, and the shared renderer must actually draw
the UFO. Completion of those scenes still does not establish functional turret/bay/reactor
sphere modules, expanded interior, core capture or full3D combat AI. The compatibility bay
currently retains unknown/unloaded escort IDs rather than refilling them without evidence
of death; durable escort death reconciliation remains open.

The first native fixture retained contact through full roll and most of pitch, then lost
support during the final pitch correction. Its larger mass-based torque allowance could
accelerate the surface away faster than the32m/s² field. The stand now bounds its commanded
angular acceleration to4rad/s² before applying torque through the actual inertia tensor.
Contact assertions remain unchanged; the runtime is not forced to keep impossible contact.

The bounded native pass retained contact through all full rotations and both jump modes.
Its final model assertion revealed a test-probe bug: every ModelOrientation call, including
launched escorts, was compared with the local player frame. The probe now samples the
actual appended matrix of the local player renderer, using that draw call’s tick delta.
The model/camera tolerances are unchanged. Both exact-source native reruns passed:
Mesa GL3.2 and Sodium GL3.3,13 scenes each and26 fresh frames in total. Each run
compared569 acknowledged predictions; maximum position error was0.01410m. The actual
registered1046-cell UFO was rendered513/527 times. Camera error stayed below0.000469
and model up error below0.00000372. See `evidence/ufo-d4aa411-native/VERIFIED.json` and
its checksum-verified archive for raw frames, inputs, logs and retained first failures.
Exact tested source is `d4aa41101dfe29d7b8a7ef406775586c3f7fffd2`, main JAR SHA-256
`bbaf09f7faa782261d489ee1e0d622fc3059065b5a8746009b122d6523846db5`.

The stand places its pilot inside the UFO initially. It does not prove entering from
outside while free flight is active. Inspection found that free-flight travel still
uses ordinary Minecraft collision rather than the BlockBody contact kernel. Fixing
that movement/authority boundary and proving an external approach is the next step.
This is still a development checkpoint, not an accepted1.1.35 distribution.

## Free-flight contact integration (development)

The suit now shares `DynamicBodyAttachment.freeFlight` with the existing D6/BlockBody
contact kernel. This mode consumes the suit integrator's world velocity without adding
walking gravity, field alignment or tangential foot friction. It retains moving/rotating
compound CCD and opposite hull reactions. The server defers nearby suit travel until
after joints/hull contacts and before boarding acquisition. While it owns this contact
region, ordinary movement packets cannot replace the resolved player position.

The client predicts through the same kernel, with a bounded16-command history and
32-command server queue. Prediction clocks carry no client pose or velocity authority.
Outgoing shape dependencies pause prediction until the actual matching grids arrive.
Loaded-world collision snapshots include the flight path, never generate unknown cubes,
and fence extreme displacement beyond32m per axis. Suit collision geometry retains the
existing upright character shape; this does not certify an arbitrarily oriented suit OBB.

Local checks:679 unit tests and234 server GameTests passed. Eight new unit cases cover
thin-hull CCD at512m/s, moving hull approach, open/rotated doors, momentum reaction,
separation and absence of a second gravity/frame motor. Two tests use real UFO geometry.
Three registered server-player tests exercise actual travel deferral, blocked position
overwrite, epoch/replay/queue admission and transfer to the existing walking motor.
The first server-player handoff fixture omitted the ordinary movement step and erased
the moving deck's normal velocity; its retained failure was corrected by executing the
real phase order and retaining contact-point velocity. No contact tolerance was widened.

The new native external-entry stand must still run on this exact candidate. It exercises
approach through the real moving UFO bay, H-off boarding, walking and roof collision during
three-axis spin. Until its real-client evidence and broader regression are verified, the
integration remains WIP. Dedicated/latency/multiclient/reload acceptance remains open.

The first native external approach timed out before acquiring a lease: real network
ServerPlayers do not normally call `travel`, unlike the explicit test invocation.
The authoritative world phase now enrolls eligible nearby pilots itself. Two additional
registered regressions prove that no travel call is needed and that a portal discards
only the obsolete prediction clock, preserving momentum and rejecting its old commands.
The client can resume above a server acknowledgment after a local predictor reset.
All679 unit and236 server tests passed after this correction; a native rerun is required.

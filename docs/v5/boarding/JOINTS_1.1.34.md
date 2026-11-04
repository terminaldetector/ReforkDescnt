# Physical component joints —1.1.34-joints.1

This extends the existing BlockBody/D6 runtime. It is not another physics engine or dimension.
The full master specification, Boarding12 and UFO9 acceptance remain OPEN. Exact-source CI
and installed-client certification are recorded separately from implementation notes.

## Runtime and persistence

Fixed joints constrain relative position and orientation. Hinges retain one angular degree of
freedom; motors drive that hinge axis with a force/torque allowance shared across iterations.
Sliders lock transverse and angular motion, provide predictive axial stops and a bounded motor.
Material anchors are local grid points plus occupied cells, adapted to the current COM before
each solve. Mass edits therefore do not move a mount, and a fractured component receives its
actual anchor. Destroying the anchor removes the joint. Explicit or overload separation keeps
the current bodies and their world velocity/angular momentum; it does not recreate them.

Connected components share force integration, sequential impulse constraints and checked pose
integration before hull/player contact phases. Slow groups use at least16 substeps; adaptive
rollback bounds corner travel to0.15m, with a512-substep maximum. Force/torque accumulators and
quaternion bookkeeping are restored on retry. The old D6.step path remains equivalent to its
split force/pose calls. Geometry remains one compound collider per component, not per block.
Deferred material split within this phase is integrated once. Controller torque stays with
the original body instead of being duplicated onto every fragment.

One persisted world registry owns each joint identity and both endpoint indexes. Removing an
endpoint for ordinary chunk unloading keeps the record; true destruction removes its links.
A same-UUID, unspawned NBT clone cannot delete a live connection. Activation starts on the next
world tick, so creating a joint cannot couple only half of an entity update phase. Missing or
unticked partners pause the connected island with momentum retained. No synchronous terrain
generation or imaginary immovable replacement is used to stand in for an absent endpoint.

## Operator controls

The existing operator permission on /d6body applies to:

- /d6body joint add fixed|hinge|slider|motor <first body UUID> <second body UUID> <world x y z>
- /d6body joint list
- /d6body joint motor <joint UUID> <relative speed> <force or torque>
- /d6body joint break_load <joint UUID> <force> <torque>
- /d6body joint remove <joint UUID>

Body UUIDs resolve through the loaded physical index, including a hull whose vanilla pivot
section is not being tracked. No chunks are generated to resolve an identifier.
The creation point must belong to occupied material in both bodies. Coordinates are exact,
including integer coordinates; commands never teleport a hull into position. The joint axis
is the first body's localX. Current relative orientations are retained. A motor starts at1rad/s;
a slider initially holds zero offset with stops at0..3m. Motor commands use rad/s and Nm for a
hinge motor, m/s and N for a slider. Fixed/passive-hinge links reject motor commands. Relative
angular command speed is limited to4rad/s across the two2rad/s bodies. Full in-world editing,
mechanism block controls, local axis selection UI and ControlCore remain separate work.

## Acceptance evidence

Pure checks cover shared momentum, permitted degrees of freedom, motor allowance per physical
step, predictive stops, force break, long forced translation/spin, free hinge orbit energy,
thousands of slider steps, old-step equivalence and exact adaptive rollback.
Server checks cover registry save/restore, COM change, actual material/anchor migration,
real fixed/motor/slider/passive-hinge motion through the world phase, overload release,
unloaded-partner pause/resume and the actual permission-checked command dispatcher.

The real-client stand requires four new90-frame scenes: moving fixed components, hinge motor,
a moving slider platform with actual player contacts/inputs, and overload separation. It checks
the production hull renderer, material anchor error, motor speed, physical platform travel,
acknowledged player prediction and unchanged break velocity. Fixture propulsion and attitude
control apply bounded forces/torques; no hull pose or passenger follow position is assigned.
Samples of the broken pair are held after the observed physical break for a clear final frame.
Old motion-only or boarding-only evidence cannot satisfy the joint distribution gate. Existing
weapon, inventory, mirror/portal, LOD, swarm, boarding, edit, index and full hull-scale gates remain.

The full hull stand also delays actual tracked geometry by30 client ticks. During this wait,
an authoritative boarding state owns client movement even before its collider is available.
An empty revision0 client hull is not usable geometry. Vanilla gravity and prediction remain
paused until the shape arrives; ordinary contact prediction then resumes without teleporting
the passenger to a ship-local anchor. Certification requires at least20 measured waiting ticks,
authoritative movement ownership and less than0.05m of fall, in addition to the unchanged
full hull prediction/error and300-edit rebase checks. The retained before-fix experiment fell
1.3733333253330784m; the same delayed delivery after the fix measured0m.
Outgoing boarding snapshots resolve their body ID through the same physical index, rather
than vanilla's pivot-section entity lookup. A server GameTest requires an actually loaded
hull absent from that vanilla lookup, verifies its COM, shape revision, gravity and velocity,
then removes it and checks that it cannot supply a phantom snapshot.

## Remaining limits

Current admission is64 joints per body and4096 per world. Force/torque and body speed limits
remain real simulation limits. Dense joint/contact islands, extreme CCD, self-intersecting
mechanisms, endpoint loading beyond currently active body volumes and simulation LOD need
additional stress/acceptance work. A missing endpoint currently pauses its island until loaded;
the registry does not discover and generate arbitrary remote entity locations by UUID.

Registry NBT checks are not a complete world close/reopen test. Dedicated/multiple-client,
portal/group transfer, whole-world module persistence and arbitrary mechanism controls are
not certified by this stage. Functional block entities, ControlCore, physical large UFO and
mothership conversion, full3D AI/bestiary and city-scale destruction remain active master work.

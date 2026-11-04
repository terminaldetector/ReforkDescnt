# ControlCore development increment

Existing UFO and existing BlockBody/D6 only. Gold deck cell at local(3,-1,0) is the prototype console, not final art or a new machinery block ecosystem. Right click uses the revision/reach/occlusion/permission-checked local-grid path. One live connection owns the body; repeated use is idempotent. WASD/Space/Ctrl apply bounded local thrust; mouse and Q/E apply bounded torque through real inertia. Alt permits freelook; H releases without enabling suit flight. The walking motor retains physical contact with neutral foot input; no seat proxy or teleport follow.

Release restores the previous autopilot setting. Core loss, removal, dead/outside/dimension-changed pilots and heartbeat expiry release control. Active connection ownership is not serialized; saved autopilot reflects the pre-capture setting. Existing saved hulls preserve their grid and need a local console retrofit; newly spawned UFOs have the prototype cell.

Local checks passed:679 unit tests and243 GameTests, including actual local-ray console use, exclusive/idempotent capture, bounded input and one drive per tick, unchanged capture/release momentum, NBT clone isolation, and immediate console-damage release. Native UI/control acceptance remains pending. Weapons, reactor sphere, full functional modules and UFO9 remain OPEN.

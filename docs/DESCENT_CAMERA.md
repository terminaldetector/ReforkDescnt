# Descent Camera — PC Fabric

Port of GMod `d6_client.lua` camera stack (`CreateMove` + `CalcView`) onto Minecraft’s `Camera` / `GameRenderer`.

## GMod → Minecraft mapping

| GMod | Minecraft |
|------|-----------|
| `Ang:RotateAroundAxis` (pitch/yaw/roll) | `ShipAttitude` + `ShipAttitudeClient` |
| `cmd:SetViewAngles(Ang)` | Entity yaw/pitch (pitch clamped ±90 for net); camera takes the unclamped values |
| `CalcView` CamLag / micro / vib | `DescentCamera` → `CameraMixin` `setPos` |
| `Ang.r` in CalcView | `GameRendererMixin.tiltViewWhenHurt` Z-bank (MC Camera has no roll) |
| Third person `Forward*-100+Up*22` + trace | F5 / third-person perspective → ship-relative chase + ray clip |
| Always-Run FOV feel | `getFov` boost while afterburning / fast |
| Vanilla bob / hurt tilt | Cancelled while 6DoF on |

## 360° freedom — how the poles are handled

Descent has no "up". Two separate things have to hold for that to be true in Minecraft:

**1. Input.** `ShipAttitude` is an orthonormal basis (forward + up), and the mouse rotates it
around its own local axes (`yawLocal` → `pitchLocal`). Nothing in the input path references
world up, so the nose passes through the zenith and nadir with no gimbal lock, and
`rollLocal` is unbounded — barrel rolls never hit a stop.

**2. Camera.** `Camera` only accepts yaw/pitch, so the bank is applied separately as a
screen-space Z rotation. The decomposition must be *exact*:

```
bank = atan2(shipUp · levelRight, shipUp · levelUp)
```

where `levelRight` / `levelUp` are the zero-roll camera axes at this basis' yaw and pitch —
derived from **yaw alone** (`levelRight = (-cos yaw, 0, -sin yaw)`), never from
`forward × worldUp`, which collapses when the nose is vertical.

That makes `(yaw, pitch, bank)` reproduce the ship basis to ~1e-15, which has two
consequences worth remembering before touching this code:

- **Never smooth the view roll.** Near a pole yaw swings fast and the bank cancels the swing
  exactly; a lerp breaks the cancellation and the horizon visibly detaches from the hull
  (measured up to 108° of error). `DescentCamera.viewRoll()` is exact;
  `DescentCamera.hudRoll()` is the smoothed value and feeds HUD gauges only.
- **Euler components jump at the exact pole, and that is fine.** Yaw and bank both flip 180°
  in the same step; the composed rotation stays continuous.

Prior behaviour, for reference: the bank reference swapped to `(0,0,1)` once `|forward.y|`
passed 0.95, which snapped the horizon ~174° a couple of degrees off vertical.

## Feel knobs (author-tuned in GMod, scaled for blocks)

- **TurnVel** `ft*8`, **AngVel** FA on `ft*5` / off `ft*2`
- **RollVel** `ft*5` toward `±175°/s`, unbounded bank
- Soft **X level** lerps bank (not instant snap)
- CamLag pullback under Always-Run; dash opposite kick
- F5 front view mirrors yaw/pitch, bank and the chase offset

## Files

- `client/flight/ShipAttitudeClient.java`
- `client/flight/DescentCamera.java`
- `mixin/client/CameraMixin.java`, `CameraAccessor.java`
- `mixin/client/GameRendererMixin.java`, `MouseMixin.java`

# Descent Camera — PC Fabric

Port of GMod `d6_client.lua` camera stack (`CreateMove` + `CalcView`) onto Minecraft’s `Camera` / `GameRenderer`.

## GMod → Minecraft mapping

| GMod | Minecraft |
|------|-----------|
| `Ang:RotateAroundAxis` (pitch/yaw/roll) | `ShipAttitude` + `ShipAttitudeClient` |
| `cmd:SetViewAngles(Ang)` | Player yaw/pitch (pitch clamped ±90 for net) + bank on view matrix |
| `CalcView` CamLag / micro / vib | `DescentCamera` → `CameraMixin` `setPos` |
| `Ang.r` in CalcView | `GameRendererMixin.tiltViewWhenHurt` Z-bank (MC Camera has no roll) |
| Third person `Forward*-100+Up*22` + trace | F5 / third-person perspective → ship-relative chase + ray clip |
| Always-Run FOV feel | `getFov` boost while afterburning / fast |
| Vanilla bob / hurt tilt | Cancelled while 6DoF on |

## Feel knobs (author-tuned in GMod, scaled for blocks)

- **TurnVel** `ft*8`, **AngVel** FA on `ft*5` / off `ft*2`
- **RollVel** `ft*5` toward `±175°/s`, bank clamp ±180
- Soft **X level** lerps bank (not instant snap)
- CamLag pullback under Always-Run; dash opposite kick

## Files

- `client/flight/ShipAttitudeClient.java`
- `client/flight/DescentCamera.java`
- `mixin/client/CameraMixin.java`, `CameraAccessor.java`
- `mixin/client/GameRendererMixin.java`, `MouseMixin.java`

# Actual slot-backed drone preview — source-only contract

One behavior: the existing drone inventory must preview the equipment actually
in the selected synchronized slot, rather than a blank entity with permanently
modelled guns. This is U02 from the untouched implementation map, not a redesign
of the inventory, gun input, world hull rendering or physics.

Source pin `45dacc786468c0a8949bde30900ba7647cf85dc1` and15 selected path hashes
are in the [source receipt](DRONE_LOADOUT_PREVIEW_CONTRACT_2026-11-17.json).
The receipt separately compares their bytes to the later observed canonical HEAD;
that is a source comparison, not a runtime result. Root approved the source-only
scope; **no Java/client launch or production change occurred at this contract**.
The prior837/430 ae1 integration is retained, not relabelled as preview acceptance.

## Current behavior and sources of truth

- `DroneInventoryScreen:7–17` creates an unspawned blank PyroShipEntity and invokes
  the entity dispatcher. No handler slot is read for the preview.
- `DroneInventoryHandler:14–29` already supplies5 real inventory slots with
  vanilla ScreenHandler synchronization. `DroneInventory.ORDER:8` maps the first
  four backed hull slots; `DroneLoadout.of:10` supports mounted/free loadouts.
- `DroneArsenal` fires only channels0–2; index3 is reserve and index4 is propulsion.
  The UI labels are LMB/RMB/B/reserve/engine. Do not infer the preview equipment
  from the player's held stack or call the arsenal to render it.
- The **existing procedural Java** PyroShipModel is the current supported hull
  model. This does not authorize restoring an excluded external/imported Pyro
  model. Its laser/missile/top/bottom appendages are baked into the mesh even on
  a blank preview. `PyroShipRenderer:86–108` only draws additional construction
  meshes when a real pilot holds a supported weapon. Reusing that entity path
  would either stay blank or invite fake-pilot/hand-state mutation.
- The actual physical layout path is
  `WeaponCore.muzzleFor/allMuzzles:285–303 → ConstructionRegistry.getMuzzle(s) →
  WeaponClusters.MuzzleOffset`; no WeaponMounts class exists in this source pin.
  `ConstructionRegistry.getModules` returns copies of saved overrides or actual
  default layouts. Reading it must never call setOverride/acceptClientOverride.

## Chosen presentation

1. Read `handler.getSlot(index).getStack()` for the selected slot, every visible
   state revision. Slot synchronization, not DroneLoadout/player-hand inference,
   is authoritative for this open screen. A preview cache may hold read-only
   copies with invalidation on item/components/layout changes; it is not another
   inventory, cannot consume items and cannot publish/save anything.
2. Use a private baked ModelPart from **the same** PyroShipModel model data.
   Hide laser_l/r, missile_l/r, top weapon pod/tubes and lower rig/emitter only on
   that private UI copy. Never modify renderer-owned/global parts, live entities
   or the world model. Empty selected slot shows the bare supported hull.
3. Preview **one selected bank's complete actual layout** on the hull. The user
   can select a bank by hovering its existing slot/label; a persistent highlight
   and native caption make the selected bank clear. The other slots retain their
   actual vanilla item icons and interaction. Do not consume inventory clicks or
   add a second command/network protocol. Default selection is primary.
4. Do not show three complete overlapping layouts at once or invent new unsynced
   per-bank offsets. Preserve positions/scales/angles/tints of the selected saved
   construction layout. Shared mesh and actual item representation are reused;
   no new substitute mesh is invented for unsupported items.
5. Existing HybridWeaponModels parts are rendered with the same module transform
   as the world. For logical legacy models, tools, reserve and engine entries,
   use that actual stack's baked item model, including components. Such an item
   inspection is presentation only; reserve/engine icons do not establish a new
   physical weapon mount. Do not silently treat a reserve brick as a firing gun.

## Coordinates and bounds

Construction layout offsets are Source inches and become blocks through `/16`,
as WeaponCore/WeaponClusters already do. The preview physical frame uses
`+X=right, +Y=up, -Z=forward`. A module anchor is therefore
`(posRgt, posUp, -posFwd)/16`; shared HybridWeaponModels.transform already gives
its rotated/scaled local vertex. Add that anchor, then apply a single UI orbit
and fit transform to the complete hull+selected layout. Preserve the actual
layout internally; only the common viewport transform may translate/scale it.

The procedural hull's ModelPart vertices use model units and negativeY-up;
convert their `/16` geometry into that same inspection frame. Document the
matrix conversion in code instead of scattering guessed bank offsets. The
screen is a stable equipment inspection, not a live cockpit camera: moving or
rolling the player must not jerk the UI. Do not read transient camera recoil to
animate an inventory image. Actual world Pyro baked-appendage behavior and exact
pilot-eye/hull/muzzle world visual parity are separate issues, **not claimed**.

Keep existing slot coordinates, transfer/permission/firing semantics and window
size. Fit/clamp the preview inside its card with restored scissor/matrix state.
Respect existing construction validation (64modules), use finite geometry and
bounded caches; a malformed/unknown layout uses its real item representation
rather than allocating without limit or poisoning later renders.

## Exact prospective file ownership

Production edits, after this contract checkpoint, are limited to:

- Existing `client/screen/DroneInventoryScreen.java`: replace blank-entity path,
  select/highlight bank and delegate read-only drawing.
- New `client/render/DroneLoadoutPreview.java`: private hull model, selected
  module/item drawing, bounds and state restoration.
- A small new preview-plan/layout helper only if needed for meaningful pure
  geometry/selection tests; keep it in this UI scope and use actual shared layout
  data. No global WeaponMounts abstraction or weapon authority rewrite.
- Focused source tests and a coordinated test-only native observer/entrypoint
  later. No shared FrameProbe/PS1 renderer rewrite or production debug hook.

PyroShipRenderer/Model source, DroneArsenal/DroneLoadout/Handler, item IDs/recipes,
ConstructionRegistry, physical muzzles, ScenePresentation/FBO/mirror/portal hooks
and central ledgers stay outside this packet. If existing APIs cannot safely
restore a needed state, report that concrete need before expanding ownership.

## Required acceptance, still OPEN

First source/unit gate must cover finite shared coordinate conversion, fit bounds
at varied window sizes, empty/current/invalid selection and replacement/layout
changes without aliasing mutable server inventory. Tests must exercise a useful
geometry/state contract, not merely assert that the new helper calls itself.

Then use an actual client and the real menu/input/synchronized slots:

1. Empty selected bank: bare hull, no static gun appendage and no phantom entity.
2. Put a known custom mesh weapon into the actual primary slot while menu stays
   open. Its model/layout appears on the hull. Equip a distinct secondary, hover
   its selector and observe that bank's actual layout, then swap/remove it and
   verify the visible change/clearing. Record actual registry IDs and server slot
   state, not just a client fixture field.
3. Keep an unrelated held building stack (and test empty hand): preview follows
   selected socket, never held item. Tool/engine/reserve use their actual baked
   model/icon; reserve does not masquerade as an active weapon.
4. Actual free-flight and mounted menus agree with their synchronized inventories;
   real shift-click/equip/remove persist without duplication. Opening menu still
   suppresses firing and cannot release a previously charged shot.
5. Native/PS1 and ordinary/6DoF views, resize/reopen, captions/tooltips and real
   mouse interaction remain usable. Native GUI does not enter low-resolution
   world target. World orientation cannot jerk the equipment image.
6. Actual visual oracle binds selected server registry ID/layout to a changed
   rendered model in the preview ROI, plus empty-state control. Rendering may not
   pass merely from an observer's selected-ID counter or native item icons. Freeze
   scene/oracle thresholds before executing and preserve first failures.
7. Matrix/scissor, GL error, blend/depth/shader/FBO/viewport and shadow state are
   restored after normal, empty and exceptional paths. Prefer avoiding the entity
   dispatcher entirely; no unconditional setRenderShadows(true) reset. Verify the
   following GUI/world/mirror/portal frame is unchanged, not just that preview drew.

No native/hardware/performance/fullS10/S15 acceptance exists yet. Java/Gradle/native
remain serialized under root lease; this contract is source-only readiness.

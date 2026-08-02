/**
 * DRMD 6DOF — MCPE Master / Descent-style attitude
 * Local pitch (нос вверх/вниз), barrel roll (бочки), thrust on ship axes.
 * On-screen control overlay + telemetry HUD.
 */
import { system, world } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

const TAG_6DOF = "drmd_6dof";
const TAG_CONSTRUCT = "drmd_construct";
const TAG_WELCOME = "drmd_welcome";
const TAG_AFTERBURN = "drmd_afterburn";
const TAG_HUD_CTRL = "drmd_hud_ctrl";
/** Flight Assist / dampeners — the PC build's F key. */
const TAG_FLIGHT_ASSIST = "drmd_flight_assist";

/** Pitch / roll rates (deg per tick) — Descent-like responsiveness */
const PITCH_RATE = 3.2;
const ROLL_RATE = 4.8;
const YAW_RATE = 2.8;

/** Selectable barrel-roll rates, deg/tick. Index is stored per pilot. */
const ROLL_RATES = [
  { label: "медленно", rate: 2.6 },
  { label: "норма", rate: ROLL_RATE },
  { label: "быстро", rate: 8.0 },
];

/** Scripted roll maneuvers run at a multiple of the manual rate. */
const MANEUVER_GAIN = 1.6;

/**
 * @typedef {{
 *   vx:number, vy:number, vz:number,
 *   fx:number, fy:number, fz:number,
 *   ux:number, uy:number, uz:number,
 *   dashCd:number, hudTick:number
 * }} FlightState
 */

/** @type {Map<string, FlightState>} */
const flights = new Map();
const pulseFlags = new Map();
/** Server tick counter driving the flight loop, torch sweeps and cache expiry. */
let flightTick = 0;

const HOLD = {
  "drmd:ctrl_roll_left": "rollL",
  "drmd:ctrl_roll_right": "rollR",
  "drmd:ctrl_ascend": "pitchUp", // нос вверх (pitch)
  "drmd:ctrl_descend": "pitchDown", // нос вниз (pitch)
  "drmd:ctrl_strafe_left": "strafeL",
  "drmd:ctrl_strafe_right": "strafeR",
  "drmd:ctrl_yaw_left": "yawL",
  "drmd:ctrl_yaw_right": "yawR",
  "drmd:ctrl_slide_up": "slideUp",
  "drmd:ctrl_slide_down": "slideDown",
  "drmd:ctrl_brake": "brake",
  "drmd:ctrl_thrust": "thrust",
};

function v(x, y, z) {
  return { x, y, z };
}

function add(a, b) {
  return v(a.x + b.x, a.y + b.y, a.z + b.z);
}

function scale(a, s) {
  return v(a.x * s, a.y * s, a.z * s);
}

function dot(a, b) {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}

function cross(a, b) {
  return v(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
}

function normalize(a) {
  const l = Math.sqrt(dot(a, a)) || 1;
  return scale(a, 1 / l);
}

/** Rodrigues rotation of vec around unit axis by deg */
function rotateAround(vec, axis, deg) {
  const rad = (deg * Math.PI) / 180;
  const c = Math.cos(rad);
  const s = Math.sin(rad);
  const k = normalize(axis);
  // v cos + (k×v) sin + k(k·v)(1-cos)
  const kxv = cross(k, vec);
  const kdv = dot(k, vec);
  return add(add(scale(vec, c), scale(kxv, s)), scale(k, kdv * (1 - c)));
}

function forwardOf(st) {
  return v(st.fx, st.fy, st.fz);
}

function upOf(st) {
  return v(st.ux, st.uy, st.uz);
}

function rightOf(st) {
  return normalize(cross(forwardOf(st), upOf(st)));
}

function setForwardUp(st, f, u) {
  f = normalize(f);
  // re-orthogonalize up against forward
  u = normalize(add(u, scale(f, -dot(u, f))));
  if (dot(u, u) < 1e-6) u = v(0, 1, 0);
  st.fx = f.x;
  st.fy = f.y;
  st.fz = f.z;
  st.ux = u.x;
  st.uy = u.y;
  st.uz = u.z;
}

/**
 * Right vector of a zero-roll camera for this heading — derived from yaw alone.
 *
 * Port of ShipAttitude.levelRightOf from the PC build. Deliberately NOT
 * cross(forward, worldUp): that collapses when the nose points straight up or
 * down, and the old fallback made the roll read-out snap ~174 degrees a couple
 * of degrees off vertical.
 */
function levelRightOf(f) {
  const yaw = Math.atan2(-f.x, f.z);
  return v(-Math.cos(yaw), 0, -Math.sin(yaw));
}

/** Up vector of a zero-roll camera for this heading. */
function levelUpOf(f) {
  const u = cross(levelRightOf(f), f);
  if (dot(u, u) < 1e-12) return v(0, 1, 0);
  return normalize(u);
}

function attitudeFromView(player) {
  const f = normalize(player.getViewDirection());
  return { f, u: levelUpOf(f) };
}

/* ------------------------------------------------------------------ *
 * Propulsion
 *
 * applyKnockback is the smooth path — it goes through the engine's own
 * collision and interpolation. But Bedrock makes creative and spectator
 * players knockback-immune, and the call is a silent no-op there rather
 * than a throw, so the catch-block fallback never fires either. A pilot
 * in creative got zero thrust and simply hung at the spawn point.
 *
 * Those two modes fly by teleport instead.
 * ------------------------------------------------------------------ */

/** gameMode cache: id -> { mode, tick }. Re-probed once a second. */
const gameModes = new Map();
const GAMEMODE_TTL = 20;
const KNOCKBACK_MODES = ["survival", "adventure"];

function gameModeOf(player, tick) {
  const cached = gameModes.get(player.id);
  if (cached && tick - cached.tick < GAMEMODE_TTL) return cached.mode;
  let mode = "survival";
  try {
    const direct = player.getGameMode?.();
    if (typeof direct === "string" && direct) mode = direct;
    else mode = probeGameMode(player);
  } catch (_) {
    mode = probeGameMode(player);
  }
  gameModes.set(player.id, { mode, tick });
  return mode;
}

/** Fallback for engines whose Player has no getGameMode(): ask the selector. */
function probeGameMode(player) {
  for (const mode of ["creative", "spectator", "adventure", "survival"]) {
    try {
      for (const p of world.getPlayers({ gameMode: mode })) {
        if (p.id === player.id) return mode;
      }
    } catch (_) {}
  }
  return "survival";
}

/**
 * Walk the hull along its velocity by teleporting.
 *
 * checkForBlocks gives us collision: a blocked destination fails instead of
 * putting the pilot inside a wall. Fast passes are split so a 2-block step
 * cannot tunnel a 1-block wall.
 */
function stepByTeleport(player, st, speed) {
  if (speed < 1e-4) return;
  const steps = speed > 0.85 ? 3 : 1;
  const sx = st.vx / steps;
  const sy = st.vy / steps;
  const sz = st.vz / steps;
  for (let i = 0; i < steps; i++) {
    const loc = player.location;
    const to = { x: loc.x + sx, y: loc.y + sy, z: loc.z + sz };
    let ok = false;
    try {
      ok = player.tryTeleport(to, { checkForBlocks: true }) !== false;
    } catch (_) {
      ok = false;
    }
    if (!ok) {
      // Wall bounce, same feel as the PC build: most of the energy goes into the hull.
      st.vx *= -0.3;
      st.vy *= -0.3;
      st.vz *= -0.3;
      return;
    }
  }
}

function propel(player, st, speed, mode) {
  if (KNOCKBACK_MODES.includes(mode)) {
    try {
      player.applyKnockback(st.vx, st.vz, Math.min(1.3, speed * 0.92), st.vy * 0.38);
      return;
    } catch (_) {}
  }
  stepByTeleport(player, st, speed);
}

function stateOf(player) {
  const id = player.id;
  if (!flights.has(id)) {
    const { f, u } = attitudeFromView(player);
    flights.set(id, {
      vx: 0,
      vy: 0,
      vz: 0,
      fx: f.x,
      fy: f.y,
      fz: f.z,
      ux: u.x,
      uy: u.y,
      uz: u.z,
      dashCd: 0,
      hudTick: 0,
      rollRateIdx: 1,
      /** Active barrel-roll maneuver, or null. */
      man: null,
    });
  }
  return flights.get(id);
}

function syncCamera(player, st) {
  const f = forwardOf(st);
  // Minecraft pitch: negative = look up
  let pitch = (-Math.asin(Math.max(-1, Math.min(1, f.y))) * 180) / Math.PI;
  let yaw = (Math.atan2(-f.x, f.z) * 180) / Math.PI;
  try {
    player.setRotation({ x: pitch, y: yaw });
  } catch (_) {
    try {
      player.runCommand(
        `teleport @s ~~~ ${yaw.toFixed(2)} ${pitch.toFixed(2)}`
      );
    } catch (__) {}
  }
}

function pitchDegrees(st) {
  return (-Math.asin(Math.max(-1, Math.min(1, st.fy))) * 180) / Math.PI;
}

function yawDegrees(st) {
  return (Math.atan2(-st.fx, st.fz) * 180) / Math.PI;
}

/**
 * Bank against the pole-safe zero-roll frame, in (-180, 180].
 *
 * Matches ShipAttitude.bankDegrees on PC, so the two versions report the same
 * number for the same attitude — including straight up and straight down.
 */
function rollDegrees(st) {
  const f = forwardOf(st);
  const u = upOf(st);
  return (
    (Math.atan2(dot(u, levelRightOf(f)), dot(u, levelUpOf(f))) * 180) / Math.PI
  );
}

/**
 * Level band for an altitude, using the same thresholds as WorldLevels on PC.
 *
 * Bedrock cannot widen the overworld the way the PC datapack does, so only the
 * bands inside -64..320 are reachable here; the labels still match so both
 * versions name the same altitude the same way.
 */
function bandOf(y) {
  if (y >= 880) return "END";
  if (y >= 640) return "ORBITAL";
  if (y >= 320) return "SKY";
  if (y >= 40) return "SURFACE";
  if (y >= -64) return "INDUSTRIAL";
  if (y >= -240) return "ABYSS";
  return "NETHER";
}

function bar(val, max, width) {
  const n = Math.max(0, Math.min(width, Math.round(((val + max) / (2 * max)) * width)));
  let s = "";
  for (let i = 0; i < width; i++) s += i === n ? "§e|§a" : "§8·";
  return "§a" + s;
}

function selectedId(player) {
  try {
    const inv = player.getComponent("minecraft:inventory") || player.getComponent("inventory");
    const container = inv?.container;
    if (!container) return "";
    let slot = 0;
    try {
      slot = player.selectedSlotIndex;
    } catch (_) {
      try {
        slot = player.selectedSlot;
      } catch (__) {}
    }
    return container.getItem(slot | 0)?.typeId ?? "";
  } catch (_) {
    return "";
  }
}

function safeCommand(player, cmd) {
  try {
    player.runCommand(cmd);
  } catch (_) {
    try {
      player.runCommandAsync?.(cmd);
    } catch (__) {}
  }
}

/* ================================================================== *
 * Gravity torch — walking on walls and ceilings
 *
 * The Prey trick, and the same model as the PC build's FootGravitySystem:
 * a torch redefines which way is down for anyone near the face it is
 * mounted on, and you then walk that surface as if it were the floor.
 *
 * One honest platform difference: the Bedrock script API exposes only yaw
 * and pitch, so the view cannot be rolled. On PC the world rotates under
 * you and a wall looks like a floor; here you genuinely walk the wall,
 * but the horizon stays world-level. Everything else — sticking, local
 * gravity, movement in the surface frame, jumping off — behaves the same.
 * ================================================================== */

const TORCH_ID = "drmd:gravity_torch";
/** Reach of one torch, matching GravityTorchBlock.RADIUS on PC. */
const TORCH_RADIUS = 8;
/** Half-width of the block sweep that finds torches. */
const SCAN_RADIUS = 6;
/** A torch drops out of the registry if a sweep has not seen it for this long. */
const TORCH_TTL = 200;
/** Per-tick arc onto / off a surface — PC uses the same pair. */
const CAPTURE_RATE = 0.18;
const RELEASE_RATE = 0.26;
/** Vanilla walking is 0.216 blocks/tick; ground friction is 0.84, so accel is that times 0.16. */
const WALK_ACCEL = 0.035;
const SPRINT_GAIN = 1.3;
const FOOT_GRAVITY = 0.08;
const JUMP_IMPULSE = 0.42;
/** How far below the feet still counts as standing on the surface. */
const STICK_DIST = 1.4;

const FACE_UP = {
  up: { x: 0, y: 1, z: 0 },
  down: { x: 0, y: -1, z: 0 },
  north: { x: 0, y: 0, z: -1 },
  south: { x: 0, y: 0, z: 1 },
  west: { x: -1, y: 0, z: 0 },
  east: { x: 1, y: 0, z: 0 },
};

/** Known torches: "dim:x,y,z" -> { dimId, x, y, z, up, seen }. */
const torches = new Map();
/** Per-pilot walking state. */
const walkers = new Map();

/** Shortest-arc interpolation — a component lerp collapses to zero on a ceiling flip. */
function slerpV(from, to, t) {
  const a = normalize(from);
  const b = normalize(to);
  const d = Math.max(-1, Math.min(1, dot(a, b)));
  if (d > 0.99995) return b;
  let axis = cross(a, b);
  if (dot(axis, axis) < 1e-12) {
    axis = cross(a, v(0, 1, 0));
    if (dot(axis, axis) < 1e-12) axis = cross(a, v(1, 0, 0));
  }
  return normalize(rotateAround(a, axis, (Math.acos(d) * 180) / Math.PI * t));
}

function nearWorldUp(u) {
  const dx = u.x, dy = u.y - 1, dz = u.z;
  return dx * dx + dy * dy + dz * dz < 0.0025;
}

function torchKey(dimId, x, y, z) {
  return `${dimId}:${x},${y},${z}`;
}

/** Surface normal a torch was mounted against, from the placement trait. */
function torchUp(block) {
  try {
    const face = block.permutation?.getState?.("minecraft:block_face");
    if (typeof face === "string" && FACE_UP[face]) return FACE_UP[face];
  } catch (_) {}
  return FACE_UP.up;
}

function rememberTorch(dimId, pos, up, tick) {
  torches.set(torchKey(dimId, pos.x, pos.y, pos.z), {
    dimId,
    x: pos.x,
    y: pos.y,
    z: pos.z,
    up,
    seen: tick,
  });
}

/**
 * Sweep one horizontal slice of blocks around a pilot per tick.
 *
 * A full 13x13x13 cube is far too many getBlock calls to do at once, so the
 * slice index walks and the registry carries what earlier slices found. A
 * complete sweep lands inside 0.65 s, which is well under the time it takes
 * to walk into a torch's reach.
 */
function sweepForTorches(player, st, tick) {
  const dim = player.dimension;
  const o = player.location;
  const bx = Math.floor(o.x);
  const by = Math.floor(o.y);
  const bz = Math.floor(o.z);
  const y = by + st.sweepY;
  st.sweepY++;
  if (st.sweepY > SCAN_RADIUS) st.sweepY = -SCAN_RADIUS;

  for (let dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
    for (let dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
      const pos = { x: bx + dx, y, z: bz + dz };
      let block;
      try {
        block = dim.getBlock(pos);
      } catch (_) {
        continue;
      }
      const key = torchKey(dim.id, pos.x, pos.y, pos.z);
      if (block && block.typeId === TORCH_ID) {
        rememberTorch(dim.id, pos, torchUp(block), tick);
      } else if (torches.has(key)) {
        // Swept this exact spot and it is not a torch any more.
        torches.delete(key);
      }
    }
  }
}

/** Nearest torch whose field covers this position, or null. */
function fieldAt(player) {
  const o = player.location;
  const dimId = player.dimension.id;
  let best = null;
  let bestWeight = 0;
  for (const t of torches.values()) {
    if (t.dimId !== dimId) continue;
    const rel = v(o.x - (t.x + 0.5), o.y - (t.y + 0.5), o.z - (t.z + 0.5));
    const d = Math.sqrt(dot(rel, rel));
    if (d > TORCH_RADIUS) continue;
    // Stop at the surface it is bolted to, so a torch on a wall leaves the room
    // on the other side of that wall alone.
    if (dot(rel, t.up) < -0.5) continue;
    const w = 1 - d / TORCH_RADIUS;
    if (w > bestWeight) {
      bestWeight = w;
      best = t;
    }
  }
  return best;
}

function walkerOf(player) {
  const id = player.id;
  if (!walkers.has(id)) {
    walkers.set(id, {
      ux: 0, uy: 1, uz: 0,
      vx: 0, vy: 0, vz: 0,
      // Authoritative position while a surface owns the pilot — see stepSurface.
      px: 0, py: 0, pz: 0,
      sweepY: -SCAN_RADIUS,
      active: false,
      label: "",
    });
  }
  return walkers.get(id);
}

/** How far the engine may drag us before we accept its position instead of ours. */
const RESYNC_DIST_SQ = 4.0;

/**
 * Step along the surface from our own tracked position, not the engine's.
 *
 * Bedrock gives no way to switch a player's gravity off, so the engine keeps
 * pulling world-down every tick underneath us. Stepping from player.location
 * would fold that pull into our position and the pilot would slide down the
 * wall they are supposed to be standing on. Integrating from px/py/pz and
 * overwriting the position each tick makes the engine's contribution moot.
 *
 * We still take the engine's word for it when the gap gets large — something
 * else moved the player (a real teleport, a piston, a respawn) and our tracked
 * position is stale.
 */
function stepSurface(player, st, vel) {
  const loc = player.location;
  const dx = loc.x - st.px;
  const dy = loc.y - st.py;
  const dz = loc.z - st.pz;
  if (dx * dx + dy * dy + dz * dz > RESYNC_DIST_SQ) {
    st.px = loc.x;
    st.py = loc.y;
    st.pz = loc.z;
  }

  const speed = Math.sqrt(dot(vel, vel));
  const steps = speed > 0.5 ? 2 : 1;
  for (let i = 0; i < steps; i++) {
    const to = {
      x: st.px + vel.x / steps,
      y: st.py + vel.y / steps,
      z: st.pz + vel.z / steps,
    };
    let ok = false;
    try {
      ok = player.tryTeleport(to, { checkForBlocks: true }) !== false;
    } catch (_) {
      ok = false;
    }
    if (!ok) {
      // Ran into geometry. Walking stops dead rather than bouncing — a bounce is
      // right for a hull at speed, wrong for a pair of boots.
      st.vx = st.vy = st.vz = 0;
      const here = player.location;
      st.px = here.x;
      st.py = here.y;
      st.pz = here.z;
      return;
    }
    st.px = to.x;
    st.py = to.y;
    st.pz = to.z;
  }
}

/** Is there surface within reach along local down? */
function groundedOn(player, up) {
  const o = player.location;
  const from = { x: o.x + up.x * 0.15, y: o.y + up.y * 0.15 + 0.1, z: o.z + up.z * 0.15 };
  try {
    const hit = player.dimension.getBlockFromRay(from, scale(up, -1), {
      maxDistance: STICK_DIST,
      includeLiquidBlocks: false,
      includePassableBlocks: false,
    });
    return !!hit;
  } catch (_) {
    return false;
  }
}

/**
 * One tick of surface walking. Returns true while the torch owns this pilot,
 * false once they are back on ordinary world gravity.
 */
function wallWalkTick(player, tick) {
  const st = walkerOf(player);
  // One slice is 169 block lookups, so it only runs every tick while a surface
  // already has the pilot — where latency shows. Otherwise it idles at a fifth of
  // that, and placing a torch registers it immediately through the block event.
  if (st.active || tick % 5 === 0) sweepForTorches(player, st, tick);
  if (tick % 40 === 0) {
    for (const [key, t] of torches) {
      if (tick - t.seen > TORCH_TTL) torches.delete(key);
    }
  }

  const field = fieldAt(player);
  const up0 = v(st.ux, st.uy, st.uz);
  const target = field ? field.up : v(0, 1, 0);
  const upN = slerpV(up0, target, field ? CAPTURE_RATE : RELEASE_RATE);
  st.ux = upN.x;
  st.uy = upN.y;
  st.uz = upN.z;

  if (!field && nearWorldUp(upN)) {
    // Released: hand the pilot back to ordinary gravity.
    if (st.active) {
      st.active = false;
      st.vx = st.vy = st.vz = 0;
      st.label = "";
      player.onScreenDisplay?.setActionBar?.("§7Гравиполе отпустило");
    }
    return false;
  }

  if (!st.active) {
    st.active = true;
    st.label = field ? "Гравифакел" : "Локальная";
    const at = player.location;
    st.px = at.x;
    st.py = at.y;
    st.pz = at.z;
    st.vx = st.vy = st.vz = 0;
    try {
      player.dimension.playSound?.("beacon.activate", at, { volume: 0.35, pitch: 1.7 });
    } catch (_) {}
  }

  // Walk frame: the look vector flattened onto the surface. Using world yaw here
  // instead would collapse every heading onto one axis the moment the surface
  // goes vertical, leaving two usable directions out of the whole circle.
  const up = upN;
  let look = v(0, 0, 1);
  try {
    look = normalize(player.getViewDirection());
  } catch (_) {}
  let forward = add(look, scale(up, -dot(look, up)));
  if (dot(forward, forward) < 1e-6) {
    const alt = Math.abs(up.y) > 0.9 ? v(0, 0, 1) : v(0, 1, 0);
    forward = add(alt, scale(up, -dot(alt, up)));
  }
  forward = normalize(forward);
  const right = normalize(cross(up, forward));

  let mx = 0;
  let my = 0;
  try {
    const mv = player.inputInfo?.getMovementVector?.();
    if (mv) {
      mx = mv.x ?? 0;
      my = mv.y ?? 0;
    }
  } catch (_) {}
  let sprint = false;
  let jumping = false;
  try {
    sprint = !!player.isSprinting;
    jumping = !!player.isJumping;
  } catch (_) {}

  const gain = WALK_ACCEL * (sprint ? SPRINT_GAIN : 1);
  const wish = add(scale(right, mx * gain), scale(forward, my * gain));

  let grounded = groundedOn(player, up);

  // Strip any component along local up, then rebuild it from gravity / jump —
  // otherwise the old world-down velocity keeps dragging the pilot off the wall.
  let vel = v(st.vx, st.vy, st.vz);
  vel = add(vel, scale(up, -dot(vel, up)));

  if (jumping && grounded) {
    vel = add(vel, scale(up, JUMP_IMPULSE));
    grounded = false;
  }
  vel = scale(vel, grounded ? 0.84 : 0.98);
  vel = add(vel, wish);
  if (!grounded) vel = add(vel, scale(up, -FOOT_GRAVITY));

  st.vx = vel.x;
  st.vy = vel.y;
  st.vz = vel.z;

  stepSurface(player, st, vel);
  return true;
}

function walkHud(player, st) {
  const up = v(st.ux, st.uy, st.uz);
  let face = "ПОЛ";
  if (up.y < -0.7) face = "ПОТОЛОК";
  else if (Math.abs(up.y) < 0.7) face = "СТЕНА";
  const line =
    `§b${st.label || "Гравиполе"}§7 · §f${face}§7 · UP ` +
    `§a${up.x.toFixed(2)} ${up.y.toFixed(2)} ${up.z.toFixed(2)}§7 · ` +
    `${bandOf(player.location.y)}`;
  try {
    player.onScreenDisplay?.setActionBar?.(line);
  } catch (_) {}
}

// Placing or breaking a torch updates the registry straight away, so you do not
// have to wait for a sweep to reach that slice.
try {
  world.afterEvents.playerPlaceBlock.subscribe((ev) => {
    const block = ev.block;
    if (!block || block.typeId !== TORCH_ID) return;
    rememberTorch(block.dimension.id, block.location, torchUp(block), flightTick);
  });
} catch (_) {}
try {
  world.afterEvents.playerBreakBlock.subscribe((ev) => {
    const id = ev.brokenBlockPermutation?.type?.id;
    if (id !== TORCH_ID) return;
    const b = ev.block;
    if (b) torches.delete(torchKey(b.dimension.id, b.location.x, b.location.y, b.location.z));
  });
} catch (_) {}

function giveControls(player) {
  const items = [
    "drmd:ctrl_panel",
    "drmd:ctrl_barrel",
    "drmd:ctrl_ascend", // pitch up
    "drmd:ctrl_descend", // pitch down
    "drmd:ctrl_roll_left",
    "drmd:ctrl_roll_right",
    "drmd:ctrl_yaw_left",
    "drmd:ctrl_yaw_right",
    "drmd:ctrl_strafe_left",
    "drmd:ctrl_strafe_right",
    "drmd:ctrl_slide_up",
    "drmd:ctrl_slide_down",
    "drmd:ctrl_thrust",
    "drmd:ctrl_dash",
    "drmd:ctrl_brake",
    "drmd:ctrl_reset_roll",
    "drmd:ctrl_afterburner",
  ];
  for (const id of items) safeCommand(player, `give @s ${id} 1`);
}

function giveStarter(player) {
  safeCommand(player, "give @s drmd:pyro_beacon 1");
  safeCommand(player, "give @s drmd:gravity_torch_item 8");
  safeCommand(player, "give @s drmd:construction_wand 1");
  safeCommand(player, "give @s iron_block 32");
  safeCommand(player, "give @s elytra 1");
  giveControls(player);
}

function applyDash(player, st) {
  if (st.dashCd > 0) {
    player.sendMessage("§7Рывок перезаряжается…");
    return;
  }
  const f = forwardOf(st);
  const boost = player.hasTag(TAG_AFTERBURN) ? 1.45 : 1.0;
  st.vx += f.x * boost;
  st.vy += f.y * boost;
  st.vz += f.z * boost;
  st.dashCd = 18;
  player.sendMessage("§eРЫВОК");
  try {
    player.playSound("random.explode", { volume: 0.3, pitch: 1.7 });
  } catch (_) {}
}

function toggleAfterburner(player) {
  if (player.hasTag(TAG_AFTERBURN)) {
    player.removeTag(TAG_AFTERBURN);
    player.sendMessage("§7Форсаж ВЫКЛ");
  } else {
    player.addTag(TAG_AFTERBURN);
    player.sendMessage("§cФорсаж ВКЛ");
  }
}

function toggleFlightAssist(player) {
  if (player.hasTag(TAG_FLIGHT_ASSIST)) {
    player.removeTag(TAG_FLIGHT_ASSIST);
    player.sendMessage("§7Демпферы ВЫКЛ — инерция как в Descent");
  } else {
    player.addTag(TAG_FLIGHT_ASSIST);
    player.sendMessage("§aДемпферы ВКЛ");
  }
}

function toggle6dof(player) {
  if (player.hasTag(TAG_6DOF)) {
    player.removeTag(TAG_6DOF);
    flights.delete(player.id);
    player.sendMessage("§76DoF ВЫКЛ");
  } else {
    player.addTag(TAG_6DOF);
    const st = stateOf(player);
    const { f, u } = attitudeFromView(player);
    setForwardUp(st, f, u);
    player.sendMessage("§a6DoF ВКЛ — Descent: нос/бочки/тяга");
    showControlsTitle(player, true);
  }
}

function resetAttitude(player, st) {
  // Keep the nose where it is and zero the bank against the pole-safe frame.
  const f = forwardOf(st);
  st.man = null;
  setForwardUp(st, f, levelUpOf(f));
  syncCamera(player, st);
  player.sendMessage("§6Крен выровнен (уровень)");
}

function pulseHold(player, key, ticks) {
  const id = player.id;
  if (!pulseFlags.has(id)) pulseFlags.set(id, {});
  pulseFlags.get(id)[key] = Math.max(pulseFlags.get(id)[key] ?? 0, ticks);
}

function activeHolds(player) {
  const holds = {
    rollL: false,
    rollR: false,
    pitchUp: false,
    pitchDown: false,
    yawL: false,
    yawR: false,
    strafeL: false,
    strafeR: false,
    slideUp: false,
    slideDown: false,
    brake: false,
    thrust: false,
  };
  const mapped = HOLD[selectedId(player)];
  if (mapped) holds[mapped] = true;
  const pulse = pulseFlags.get(player.id);
  if (pulse) {
    for (const k of Object.keys(holds)) {
      if ((pulse[k] ?? 0) > 0) {
        holds[k] = true;
        pulse[k]--;
      }
    }
  }
  return holds;
}

function showControlsTitle(player, force) {
  try {
    const disp = player.onScreenDisplay;
    disp.setTitle("§b§lDRMD 6DOF");
    disp.updateSubtitle(
      "§aНос↑↓ §f| §aБочки←→ §f| §aРысканье §f| §eТяга/Рывок §f| §cТормоз"
    );
    if (force) {
      // clear quickly so it doesn't block view long
      system.runTimeout(() => {
        try {
          disp.setTitle(" ");
          disp.updateSubtitle(" ");
        } catch (_) {}
      }, 45);
    }
  } catch (_) {}
}

function drawHud(player, st, holds) {
  const spd = Math.sqrt(st.vx * st.vx + st.vy * st.vy + st.vz * st.vz) * 20;
  const pitch = pitchDegrees(st);
  const roll = rollDegrees(st);
  const yaw = yawDegrees(st);
  const after = player.hasTag(TAG_AFTERBURN);
  const mode = player.hasTag(TAG_CONSTRUCT) ? "СТРОЙ" : "ПОЛЁТ";

  // Active control highlight
  const act = [];
  if (holds.pitchUp) act.push("НОС↑");
  if (holds.pitchDown) act.push("НОС↓");
  if (holds.rollL) act.push("БОЧКА←");
  if (holds.rollR) act.push("БОЧКА→");
  if (holds.yawL) act.push("РЫСК←");
  if (holds.yawR) act.push("РЫСК→");
  if (holds.thrust || player.isJumping) act.push("ТЯГА");
  if (holds.brake || player.isSneaking) act.push("ТОРМОЗ");
  if (holds.slideUp) act.push("СКОЛЬЗ↑");
  if (holds.slideDown) act.push("СКОЛЬЗ↓");
  if (holds.strafeL) act.push("СТРЕЙФ←");
  if (holds.strafeR) act.push("СТРЕЙФ→");

  const pitchBar = bar(pitch, 90, 9);
  const rollBar = bar(roll, 180, 9);

  // Same readouts the PC cockpit shows, condensed into the action bar.
  const thrustPct = Math.round(Math.min(1, spd / 33) * 100);
  const damp = player.hasTag(TAG_FLIGHT_ASSIST) ? "§aВКЛ" : "§7ВЫКЛ";
  let alt = 0;
  let band = "SURFACE";
  try {
    alt = Math.round(player.location.y);
    band = bandOf(alt);
  } catch (_) {}

  // Barrel-roll maneuver takes over the tail of the line while it runs.
  const prog = maneuverProgress(st);
  let manText = "";
  if (st.man && st.man.kind === "cont") {
    manText = ` §d[БОЧКА ${st.man.dir < 0 ? "←" : "→"} ∞]`;
  } else if (prog >= 0) {
    const filled = Math.round(prog * 8);
    let pb = "";
    for (let i = 0; i < 8; i++) pb += i < filled ? "§d█" : "§8░";
    manText = ` §d[БОЧКА ${Math.round(st.man.total)}° ${pb}§d]`;
  }

  try {
    player.onScreenDisplay.setActionBar(
      `§a6DOF·${mode}${after ? " §cФС" : ""} §fТЯГА§e${thrustPct}% §fДЕМПФ${damp}` +
        ` §fSPD§e${spd.toFixed(0)} §fP§b${pitch.toFixed(0)}§7°${pitchBar} §fR§d${roll.toFixed(0)}§7°${rollBar}` +
        ` §fALT§b${alt} §7${band}` +
        (manText || (act.length ? ` §6[${act.join(" ")}]` : " §8[хотбар=удерж]"))
    );
  } catch (_) {}

  // Periodic on-screen control legend (subtitle)
  st.hudTick = (st.hudTick || 0) + 1;
  if (player.hasTag(TAG_HUD_CTRL) && st.hudTick % 60 === 0) {
    try {
      player.onScreenDisplay.updateSubtitle(
        "§7Упр: §fНос↑↓ §8бочки §f←→ §8рыск §f←→ §8| §eПрыжок=тяга §cShift=тормоз §aПанель"
      );
    } catch (_) {}
  }
}

function panelChatFallback(player) {
  player.sendMessage("§b=== DRMD Descent-управление ===");
  player.sendMessage("§aНос вверх/вниз §7— поворот вокруг поперечной оси");
  player.sendMessage("§aБочки ←/→ §7— крен вокруг продольной оси");
  player.sendMessage("§aРысканье ←/→ §7— вокруг вертикали корабля");
  player.sendMessage("§eПрыжок/Тяга §7вперёд · §cТормоз · §bСкольжение ↑↓");
  player.sendMessage("§dПолёт бочкой §7— §f!d6 barrel §7(360°/180°/непрерывно)");
  player.sendMessage("§f!d6 panel|barrel|kit|hud|level|damp|dash|toggle");
}

async function openControlPanel(player) {
  const form = new ActionFormData()
    .title("DRMD Descent")
    .body(
      "Поворот как в Descent:\n§aНос §7— pitch вокруг своей оси\n§aБочки §7— roll\n§aРысканье §7— yaw\nУдерживай кнопку в хотбаре"
    )
    .button("§aНос вверх")
    .button("§aНос вниз")
    .button("§dБочка влево")
    .button("§dБочка вправо")
    .button("§3Рысканье влево")
    .button("§3Рысканье вправо")
    .button("§bСкольжение вверх")
    .button("§bСкольжение вниз")
    .button("§eРывок")
    .button("§cТормоз")
    .button("§6Выровнять крен")
    .button("§dПолёт бочкой…")
    .button("§cФорсаж")
    .button(player.hasTag(TAG_HUD_CTRL) ? "§7Скрыть подсказки HUD" : "§aПоказать подсказки HUD")
    .button(player.hasTag(TAG_6DOF) ? "§76DoF ВЫКЛ" : "§a6DoF ВКЛ")
    .button("§8Закрыть");

  try {
    const res = await form.show(player);
    if (res.canceled) return;
    const st = stateOf(player);
    switch (res.selection) {
      case 0:
        pulseHold(player, "pitchUp", 10);
        break;
      case 1:
        pulseHold(player, "pitchDown", 10);
        break;
      case 2:
        pulseHold(player, "rollL", 10);
        break;
      case 3:
        pulseHold(player, "rollR", 10);
        break;
      case 4:
        pulseHold(player, "yawL", 10);
        break;
      case 5:
        pulseHold(player, "yawR", 10);
        break;
      case 6:
        pulseHold(player, "slideUp", 12);
        break;
      case 7:
        pulseHold(player, "slideDown", 12);
        break;
      case 8:
        applyDash(player, st);
        break;
      case 9:
        pulseHold(player, "brake", 20);
        break;
      case 10:
        resetAttitude(player, st);
        break;
      case 11:
        system.run(() => openBarrelPanel(player));
        break;
      case 12:
        toggleAfterburner(player);
        break;
      case 13:
        if (player.hasTag(TAG_HUD_CTRL)) {
          player.removeTag(TAG_HUD_CTRL);
          player.sendMessage("§7Подсказки HUD выкл");
        } else {
          player.addTag(TAG_HUD_CTRL);
          player.sendMessage("§aПодсказки HUD вкл");
          showControlsTitle(player, true);
        }
        break;
      case 14:
        toggle6dof(player);
        break;
      default:
        break;
    }
  } catch (_) {
    panelChatFallback(player);
  }
}

/**
 * Barrel-roll interface — the flight panel dedicated to rolls.
 *
 * The hotbar buttons already give a raw held axis; this is the other half:
 * scripted arcs you fire and forget, a continuous roll you can leave running,
 * and the rate they all use.
 */
async function openBarrelPanel(player) {
  const st = stateOf(player);
  const rateLabel = ROLL_RATES[st.rollRateIdx ?? 1]?.label ?? "норма";
  const m = st.man;
  const status = m
    ? m.kind === "cont"
      ? `§dНЕПРЕРЫВНАЯ ${m.dir < 0 ? "влево" : "вправо"}`
      : `§dБочка ${Math.round(m.total)}° — осталось ${Math.round(m.left)}°`
    : "§7манёвр не выполняется";

  const form = new ActionFormData()
    .title("DRMD — Полёт бочкой")
    .body(
      `Крен вокруг продольной оси.
§fСейчас: ${status}
§fСкорость крена: §a${rateLabel}
§fТекущий крен: §d${rollDegrees(st).toFixed(0)}°

§7Манёвр можно перебить ручным креном.`
    )
    .button("§dБочка 360° влево")
    .button("§dБочка 360° вправо")
    .button("§5Полубочка 180° (перевернуться)")
    .button("§5Двойная бочка 720° вправо")
    .button("§bНепрерывная бочка ←")
    .button("§bНепрерывная бочка →")
    .button("§cСтоп манёвр")
    .button(`§eСкорость крена: §a${rateLabel}`)
    .button("§6Выровнять крен")
    .button("§8Назад")
    .button("§8Закрыть");

  try {
    const res = await form.show(player);
    if (res.canceled) return;
    switch (res.selection) {
      case 0:
        startBarrel(st, 360, -1);
        player.sendMessage("§dБочка 360° влево");
        break;
      case 1:
        startBarrel(st, 360, 1);
        player.sendMessage("§dБочка 360° вправо");
        break;
      case 2:
        startBarrel(st, 180, 1);
        player.sendMessage("§5Полубочка — переворот");
        break;
      case 3:
        startBarrel(st, 720, 1);
        player.sendMessage("§5Двойная бочка");
        break;
      case 4:
        startBarrel(st, 0, -1);
        player.sendMessage("§bНепрерывная бочка влево — «Стоп» чтобы прервать");
        break;
      case 5:
        startBarrel(st, 0, 1);
        player.sendMessage("§bНепрерывная бочка вправо — «Стоп» чтобы прервать");
        break;
      case 6:
        stopBarrel(st);
        player.sendMessage("§7Манёвр прерван");
        break;
      case 7:
        st.rollRateIdx = ((st.rollRateIdx ?? 1) + 1) % ROLL_RATES.length;
        player.sendMessage(
          `§eСкорость крена: §a${ROLL_RATES[st.rollRateIdx].label}`
        );
        system.run(() => openBarrelPanel(player));
        break;
      case 8:
        resetAttitude(player, st);
        break;
      case 9:
        system.run(() => openControlPanel(player));
        break;
      default:
        break;
    }
  } catch (_) {
    player.sendMessage("§b=== Полёт бочкой ===");
    player.sendMessage("§f!d6 barrel §7— панель · §f!d6 roll360 §7· §f!d6 roll180");
    player.sendMessage("§f!d6 rollcont §7— непрерывно · §f!d6 rollstop §7— стоп");
  }
}

function handleControlUse(player, id) {
  const st = stateOf(player);
  if (id === "drmd:ctrl_dash") {
    applyDash(player, st);
    return true;
  }
  if (id === "drmd:ctrl_reset_roll") {
    resetAttitude(player, st);
    return true;
  }
  if (id === "drmd:ctrl_afterburner") {
    toggleAfterburner(player);
    return true;
  }
  if (id === "drmd:ctrl_panel") {
    system.run(() => openControlPanel(player));
    return true;
  }
  if (id === "drmd:ctrl_barrel") {
    system.run(() => openBarrelPanel(player));
    return true;
  }
  // Tap = short pulse of Descent axis
  if (id === "drmd:ctrl_ascend") {
    pulseHold(player, "pitchUp", 6);
    return true;
  }
  if (id === "drmd:ctrl_descend") {
    pulseHold(player, "pitchDown", 6);
    return true;
  }
  if (id === "drmd:ctrl_roll_left") {
    pulseHold(player, "rollL", 6);
    return true;
  }
  if (id === "drmd:ctrl_roll_right") {
    pulseHold(player, "rollR", 6);
    return true;
  }
  if (HOLD[id]) {
    pulseHold(player, HOLD[id], 8);
    return true;
  }
  return false;
}

function handleChatCommand(player, msg) {
  if (!msg.startsWith("!d6")) return false;
  const arg = msg.slice(3).trim().toLowerCase();
  const st = stateOf(player);
  if (arg === "panel" || arg === "" || arg === "help") {
    system.run(() => openControlPanel(player));
  } else if (arg === "hud") {
    if (player.hasTag(TAG_HUD_CTRL)) player.removeTag(TAG_HUD_CTRL);
    else {
      player.addTag(TAG_HUD_CTRL);
      showControlsTitle(player, true);
    }
  } else if (arg === "level" || arg === "reset") {
    resetAttitude(player, st);
  } else if (arg === "pitchu" || arg === "up") {
    pulseHold(player, "pitchUp", 10);
  } else if (arg === "pitchd" || arg === "down") {
    pulseHold(player, "pitchDown", 10);
  } else if (arg === "rolll") {
    pulseHold(player, "rollL", 10);
  } else if (arg === "rollr") {
    pulseHold(player, "rollR", 10);
  } else if (arg === "barrel" || arg === "roll") {
    system.run(() => openBarrelPanel(player));
  } else if (arg === "roll360") {
    startBarrel(st, 360, 1);
    player.sendMessage("§dБочка 360°");
  } else if (arg === "roll180") {
    startBarrel(st, 180, 1);
    player.sendMessage("§5Полубочка 180°");
  } else if (arg === "rollcont") {
    startBarrel(st, 0, 1);
    player.sendMessage("§bНепрерывная бочка");
  } else if (arg === "rollstop") {
    stopBarrel(st);
    player.sendMessage("§7Манёвр прерван");
  } else if (arg === "damp" || arg === "assist") {
    toggleFlightAssist(player);
  } else if (arg === "dash") {
    applyDash(player, st);
  } else if (arg === "kit") {
    giveControls(player);
    player.sendMessage("§aКит Descent-кнопок выдан");
  } else if (arg === "toggle") {
    toggle6dof(player);
  } else {
    panelChatFallback(player);
  }
  return true;
}

function rollRateOf(st) {
  return ROLL_RATES[st.rollRateIdx ?? 1]?.rate ?? ROLL_RATE;
}

/**
 * Start a scripted barrel roll.
 *
 * @param degrees total arc, or 0 for a continuous roll that runs until stopped
 * @param dir     -1 left, +1 right
 */
function startBarrel(st, degrees, dir) {
  st.man = {
    kind: degrees > 0 ? "arc" : "cont",
    dir: dir < 0 ? -1 : 1,
    left: degrees,
    total: degrees,
  };
}

function stopBarrel(st) {
  st.man = null;
}

/** Degrees of roll the active maneuver contributes this tick. */
function maneuverRoll(st) {
  const m = st.man;
  if (!m) return 0;
  const step = rollRateOf(st) * MANEUVER_GAIN;
  if (m.kind === "cont") return step * m.dir;
  const applied = Math.min(step, m.left);
  m.left -= applied;
  if (m.left <= 1e-3) st.man = null;
  return applied * m.dir;
}

/** 0..1 progress of an arc maneuver, or -1 when none / continuous. */
function maneuverProgress(st) {
  const m = st.man;
  if (!m || m.kind !== "arc" || !(m.total > 0)) return -1;
  return 1 - m.left / m.total;
}

/** Apply Descent local-axis rotations this tick */
function applyAttitude(st, holds) {
  let f = forwardOf(st);
  let u = upOf(st);
  let r = normalize(cross(f, u));

  // Pitch around local RIGHT (нос вверх/вниз) — как в Descent
  if (holds.pitchUp) {
    f = rotateAround(f, r, -PITCH_RATE);
    u = rotateAround(u, r, -PITCH_RATE);
  }
  if (holds.pitchDown) {
    f = rotateAround(f, r, PITCH_RATE);
    u = rotateAround(u, r, PITCH_RATE);
  }

  // Recompute right after pitch
  r = normalize(cross(f, u));
  u = normalize(cross(r, f));

  // Barrel roll around local FORWARD (бочки). Manual holds and any scripted
  // maneuver sum into one delta, so a pilot can steer out of a 360 mid-roll.
  const rate = rollRateOf(st);
  let rollDelta = 0;
  if (holds.rollL) rollDelta -= rate;
  if (holds.rollR) rollDelta += rate;
  rollDelta += maneuverRoll(st);
  if (rollDelta !== 0) {
    u = rotateAround(u, f, rollDelta);
    r = rotateAround(r, f, rollDelta);
  }

  // Yaw around local UP (рысканье)
  if (holds.yawL) {
    f = rotateAround(f, u, YAW_RATE);
    r = rotateAround(r, u, YAW_RATE);
  }
  if (holds.yawR) {
    f = rotateAround(f, u, -YAW_RATE);
    r = rotateAround(r, u, -YAW_RATE);
  }

  setForwardUp(st, f, u);
}

world.afterEvents.playerSpawn.subscribe((ev) => {
  const player = ev.player;
  if (!ev.initialSpawn) return;
  system.runTimeout(() => {
    try {
      if (typeof player.isValid === "function" && !player.isValid()) return;
    } catch (_) {}
    if (!player.hasTag(TAG_WELCOME)) {
      player.addTag(TAG_WELCOME);
      player.addTag(TAG_6DOF);
      player.addTag(TAG_HUD_CTRL);
      player.addTag(TAG_FLIGHT_ASSIST);
      const st = stateOf(player);
      const { f, u } = attitudeFromView(player);
      setForwardUp(st, f, u);
      player.sendMessage("§bDRMD 6DOF §f— §aDescent / MCPE Master");
      player.sendMessage("§aНос↑↓ §7поворот · §aБочки←→ §7крен · §eПрыжок §7тяга вперёд");
      player.sendMessage("§7На экране: SPD · Pitch · Roll · активные оси");
      player.sendMessage("§7Панель управления / §f!d6 hud §7· Beta APIs = ВКЛ");
      giveStarter(player);
      showControlsTitle(player, true);
    }
  }, 40);
});

world.afterEvents.itemUse.subscribe((ev) => {
  const player = ev.source;
  const id = ev.itemStack?.typeId ?? "";
  if (handleControlUse(player, id)) return;
  if (id === "drmd:pyro_beacon" || id === "minecraft:elytra") toggle6dof(player);
  if (id === "drmd:construction_wand" || id === "minecraft:stick") {
    if (player.hasTag(TAG_CONSTRUCT)) {
      player.removeTag(TAG_CONSTRUCT);
      player.sendMessage("§7Строительный режим ВЫКЛ");
    } else {
      player.addTag(TAG_CONSTRUCT);
      player.sendMessage("§aСтроительный режим ВКЛ");
    }
  }
});

try {
  world.afterEvents.playerPlaceBlock.subscribe((ev) => {
    if (!ev.player.hasTag(TAG_CONSTRUCT)) return;
    const b = ev.block;
    ev.player.onScreenDisplay.setActionBar(`§aБлок ${b.x} ${b.y} ${b.z}`);
  });
} catch (_) {}

function bindChat() {
  const handler = (ev) => {
    try {
      const player = ev.sender || ev.player;
      const msg = (ev.message || "").trim().toLowerCase();
      if (!player || !msg.startsWith("!d6")) return;
      if (handleChatCommand(player, msg)) {
        try {
          ev.cancel = true;
        } catch (_) {}
      }
    } catch (_) {}
  };
  try {
    world.beforeEvents.chatSend.subscribe(handler);
  } catch (_) {
    try {
      world.afterEvents.chatSend.subscribe(handler);
    } catch (__) {}
  }
}
bindChat();

system.runInterval(() => {
  flightTick++;
  for (const player of world.getPlayers()) {
    const mode = gameModeOf(player, flightTick);
    // A spectator already has free flight and no collision; hijacking it strands them.
    if (mode === "spectator") continue;
    if (!player.hasTag(TAG_6DOF)) {
      // On foot: a gravity torch may own which way is down. Thruster mode never
      // reorients — the ship keeps its own up, same rule as the PC build.
      if (wallWalkTick(player, flightTick)) walkHud(player, walkerOf(player));
      continue;
    }
    const st = stateOf(player);
    if (st.dashCd > 0) st.dashCd--;

    const holds = activeHolds(player);
    applyAttitude(st, holds);
    syncCamera(player, st);

    const f = forwardOf(st);
    const u = upOf(st);
    const r = rightOf(st);

    let moveX = 0;
    let moveY = 0;
    try {
      const mv = player.inputInfo?.getMovementVector?.();
      if (mv) {
        moveX = mv.x ?? 0;
        moveY = mv.y ?? 0;
      }
    } catch (_) {}

    const after = player.hasTag(TAG_AFTERBURN);
    const thrustMul = after ? 0.13 : 0.085;
    let jumping = false;
    let sneaking = false;
    try {
      jumping = !!player.isJumping;
      sneaking = !!player.isSneaking;
    } catch (_) {}

    // Forward thrust — Jump or Thrust button or stick forward
    if (jumping || holds.thrust || Math.abs(moveY) > 0.05) {
      const s = (jumping || holds.thrust ? 1 : moveY) * thrustMul;
      st.vx += f.x * s;
      st.vy += f.y * s;
      st.vz += f.z * s;
    }

    // Strafe / stick X along local right
    if (Math.abs(moveX) > 0.05) {
      const s = -moveX * thrustMul * 0.85;
      st.vx += r.x * s;
      st.vy += r.y * s;
      st.vz += r.z * s;
    }
    if (holds.strafeL) {
      st.vx -= r.x * thrustMul * 0.9;
      st.vy -= r.y * thrustMul * 0.9;
      st.vz -= r.z * thrustMul * 0.9;
    }
    if (holds.strafeR) {
      st.vx += r.x * thrustMul * 0.9;
      st.vy += r.y * thrustMul * 0.9;
      st.vz += r.z * thrustMul * 0.9;
    }

    // Translational slide along local UP (не путать с pitch!)
    if (holds.slideUp) {
      st.vx += u.x * thrustMul;
      st.vy += u.y * thrustMul;
      st.vz += u.z * thrustMul;
    }
    if (holds.slideDown) {
      st.vx -= u.x * thrustMul;
      st.vy -= u.y * thrustMul;
      st.vz -= u.z * thrustMul;
    }

    if (holds.brake || sneaking) {
      st.vx *= 0.8;
      st.vy *= 0.8;
      st.vz *= 0.8;
    } else {
      // Dampeners off = Descent drift; on = the assisted feel, as on PC.
      const keep = player.hasTag(TAG_FLIGHT_ASSIST) ? 0.955 : 0.992;
      st.vx *= keep;
      st.vy *= keep;
      st.vz *= keep;
    }

    const speed = Math.sqrt(st.vx * st.vx + st.vy * st.vy + st.vz * st.vz);
    const maxSpd = after ? 2.2 : 1.65;
    if (speed > maxSpd) {
      const s = maxSpd / speed;
      st.vx *= s;
      st.vy *= s;
      st.vz *= s;
    }

    propel(player, st, speed, mode);

    drawHud(player, st, holds);
  }
}, 1);

world.afterEvents.playerLeave.subscribe((ev) => {
  const id = ev.playerId || ev.player?.id;
  if (id) {
    flights.delete(id);
    pulseFlags.delete(id);
    gameModes.delete(id);
    walkers.delete(id);
  }
});

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

let flightTick = 0;

system.runInterval(() => {
  flightTick++;
  for (const player of world.getPlayers()) {
    if (!player.hasTag(TAG_6DOF)) continue;
    const mode = gameModeOf(player, flightTick);
    // A spectator already has free flight and no collision; hijacking it strands them.
    if (mode === "spectator") continue;
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
  }
});

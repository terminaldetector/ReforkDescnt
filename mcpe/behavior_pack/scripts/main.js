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

/** Pitch / roll rates (deg per tick) — Descent-like responsiveness */
const PITCH_RATE = 3.2;
const ROLL_RATE = 4.8;
const YAW_RATE = 2.8;

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

function attitudeFromView(player) {
  const f = normalize(player.getViewDirection());
  let r = cross(f, v(0, 1, 0));
  if (dot(r, r) < 1e-6) r = v(1, 0, 0);
  r = normalize(r);
  const u = normalize(cross(r, f));
  return { f, u };
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

function rollDegrees(st) {
  // Bank: angle of up projected onto world-up vs local right
  const f = forwardOf(st);
  const u = upOf(st);
  let worldUp = v(0, 1, 0);
  // if looking nearly straight up/down, use world forward as reference
  if (Math.abs(f.y) > 0.95) worldUp = v(0, 0, 1);
  const levelRight = normalize(cross(f, worldUp));
  const levelUp = normalize(cross(levelRight, f));
  const r = rightOf(st);
  return (Math.atan2(dot(u, levelRight), dot(u, levelUp)) * 180) / Math.PI;
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
  const { f, u } = attitudeFromView(player);
  setForwardUp(st, f, u);
  // level roll: rebuild up from world
  let r = cross(f, v(0, 1, 0));
  if (dot(r, r) < 1e-6) r = v(1, 0, 0);
  r = normalize(r);
  setForwardUp(st, f, normalize(cross(r, f)));
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

  try {
    player.onScreenDisplay.setActionBar(
      `§a${mode}${after ? " §cФС" : ""} §fSPD§e${spd.toFixed(0)} §fP§b${pitch.toFixed(0)}§7° ${pitchBar} §fR§d${roll.toFixed(0)}§7° ${rollBar}` +
        (act.length ? ` §6[${act.join(" ")}]` : " §8[хотбар=удерж]")
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
  player.sendMessage("§f!d6 panel|kit|hud|level|dash|toggle");
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
        toggleAfterburner(player);
        break;
      case 12:
        if (player.hasTag(TAG_HUD_CTRL)) {
          player.removeTag(TAG_HUD_CTRL);
          player.sendMessage("§7Подсказки HUD выкл");
        } else {
          player.addTag(TAG_HUD_CTRL);
          player.sendMessage("§aПодсказки HUD вкл");
          showControlsTitle(player, true);
        }
        break;
      case 13:
        toggle6dof(player);
        break;
      default:
        break;
    }
  } catch (_) {
    panelChatFallback(player);
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

  // Barrel roll around local FORWARD (бочки)
  if (holds.rollL) {
    u = rotateAround(u, f, -ROLL_RATE);
    r = rotateAround(r, f, -ROLL_RATE);
  }
  if (holds.rollR) {
    u = rotateAround(u, f, ROLL_RATE);
    r = rotateAround(r, f, ROLL_RATE);
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
  for (const player of world.getPlayers()) {
    if (!player.hasTag(TAG_6DOF)) continue;
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
      st.vx *= 0.97;
      st.vy *= 0.97;
      st.vz *= 0.97;
    }

    const speed = Math.sqrt(st.vx * st.vx + st.vy * st.vy + st.vz * st.vz);
    const maxSpd = after ? 2.2 : 1.65;
    if (speed > maxSpd) {
      const s = maxSpd / speed;
      st.vx *= s;
      st.vy *= s;
      st.vz *= s;
    }

    try {
      player.applyKnockback(st.vx, st.vz, Math.min(1.3, speed * 0.92), st.vy * 0.38);
    } catch (_) {
      try {
        player.applyImpulse({ x: st.vx * 0.22, y: st.vy * 0.22, z: st.vz * 0.22 });
      } catch (__) {}
    }

    drawHud(player, st, holds);
  }
}, 1);

world.afterEvents.playerLeave.subscribe((ev) => {
  const id = ev.playerId || ev.player?.id;
  if (id) {
    flights.delete(id);
    pulseFlags.delete(id);
  }
});

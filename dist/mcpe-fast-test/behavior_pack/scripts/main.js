/**
 * DRMD 6DOF — MCPE Fast Test Edition
 * Touch-friendly 6DoF: hotbar control buttons + Control Panel form.
 * Roll L/R, ascend/descend, strafe, dash, brake, afterburner, reset roll.
 */
import { system, world } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

const TAG_6DOF = "drmd_6dof";
const TAG_CONSTRUCT = "drmd_construct";
const TAG_WELCOME = "drmd_welcome";
const TAG_AFTERBURN = "drmd_afterburn";

/** @typedef {{ x:number,y:number,z:number, roll:number, dashCd:number }} FlightState */

/** @type {Map<string, FlightState>} */
const flights = new Map();

const HOLD = {
  "drmd:ctrl_roll_left": "rollL",
  "drmd:ctrl_roll_right": "rollR",
  "drmd:ctrl_ascend": "ascend",
  "drmd:ctrl_descend": "descend",
  "drmd:ctrl_strafe_left": "strafeL",
  "drmd:ctrl_strafe_right": "strafeR",
  "drmd:ctrl_brake": "brake",
};

const PULSE = new Set([
  "drmd:ctrl_dash",
  "drmd:ctrl_reset_roll",
  "drmd:ctrl_afterburner",
  "drmd:ctrl_panel",
]);

function stateOf(player) {
  const id = player.id;
  if (!flights.has(id)) {
    flights.set(id, { x: 0, y: 0, z: 0, roll: 0, dashCd: 0 });
  }
  return flights.get(id);
}

function selectedId(player) {
  try {
    const slot = player.selectedSlotIndex ?? player.selectedSlot ?? 0;
    const container = player.getComponent("inventory")?.container;
    const stack = container?.getItem(slot);
    return stack?.typeId ?? "";
  } catch (_) {
    return "";
  }
}

function cross(a, b) {
  return {
    x: a.y * b.z - a.z * b.y,
    y: a.z * b.x - a.x * b.z,
    z: a.x * b.y - a.y * b.x,
  };
}

function normalize(v) {
  const l = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) || 1;
  return { x: v.x / l, y: v.y / l, z: v.z / l };
}

function scale(v, s) {
  return { x: v.x * s, y: v.y * s, z: v.z * s };
}

function add(a, b) {
  return { x: a.x + b.x, y: a.y + b.y, z: a.z + b.z };
}

/** Local axes with barrel roll around look vector. */
function localAxes(view, rollDeg) {
  const forward = normalize(view);
  let right = cross(forward, { x: 0, y: 1, z: 0 });
  if (right.x * right.x + right.y * right.y + right.z * right.z < 1e-6) {
    right = { x: 1, y: 0, z: 0 };
  }
  right = normalize(right);
  let up = normalize(cross(right, forward));
  const rad = (rollDeg * Math.PI) / 180;
  const c = Math.cos(rad);
  const s = Math.sin(rad);
  const rolledRight = {
    x: right.x * c + up.x * s,
    y: right.y * c + up.y * s,
    z: right.z * c + up.z * s,
  };
  const rolledUp = {
    x: up.x * c - right.x * s,
    y: up.y * c - right.y * s,
    z: up.z * c - right.z * s,
  };
  return { forward, right: rolledRight, up: rolledUp };
}

function giveControls(player) {
  const items = [
    "drmd:ctrl_panel",
    "drmd:ctrl_roll_left",
    "drmd:ctrl_roll_right",
    "drmd:ctrl_ascend",
    "drmd:ctrl_descend",
    "drmd:ctrl_strafe_left",
    "drmd:ctrl_strafe_right",
    "drmd:ctrl_dash",
    "drmd:ctrl_brake",
    "drmd:ctrl_reset_roll",
    "drmd:ctrl_afterburner",
  ];
  for (const id of items) {
    try {
      player.runCommand(`give @s ${id} 1`);
    } catch (_) {}
  }
}

function giveStarter(player) {
  try {
    player.runCommand("give @s drmd:pyro_beacon 1");
    player.runCommand("give @s drmd:gravity_torch_item 8");
    player.runCommand("give @s drmd:construction_wand 1");
    player.runCommand("give @s iron_block 32");
  } catch (e) {
    try {
      player.runCommand("give @s elytra 1");
      player.runCommand("give @s firework_rocket 8");
      player.runCommand("give @s torch 8");
    } catch (_) {}
  }
  giveControls(player);
}

function applyDash(player, st) {
  if (st.dashCd > 0) {
    player.sendMessage("§7Dash cooling…");
    return;
  }
  const view = player.getViewDirection();
  const { forward } = localAxes(view, st.roll);
  const boost = player.hasTag(TAG_AFTERBURN) ? 1.4 : 0.95;
  st.x += forward.x * boost;
  st.y += forward.y * boost;
  st.z += forward.z * boost;
  st.dashCd = 18; // ticks
  player.sendMessage("§eDASH");
  try {
    player.playSound("random.explode", { volume: 0.35, pitch: 1.6 });
  } catch (_) {}
}

function toggleAfterburner(player) {
  if (player.hasTag(TAG_AFTERBURN)) {
    player.removeTag(TAG_AFTERBURN);
    player.sendMessage("§7Afterburner OFF");
  } else {
    player.addTag(TAG_AFTERBURN);
    player.sendMessage("§cAfterburner ON");
  }
}

async function openControlPanel(player) {
  const form = new ActionFormData()
    .title("DRMD Controls")
    .body("Touch buttons · hotbar hold = continuous\nRoll / vertical / strafe / dash")
    .button("§aRoll Left  Q")
    .button("§aRoll Right  E")
    .button("§bAscend  Space")
    .button("§bDescend  Ctrl")
    .button("§3Strafe Left")
    .button("§3Strafe Right")
    .button("§eDash  Shift")
    .button("§cBrake")
    .button("§6Reset Roll  X")
    .button("§cAfterburner  R")
    .button(player.hasTag(TAG_6DOF) ? "§7Toggle 6DoF OFF" : "§aToggle 6DoF ON")
    .button("§8Close");

  try {
    const res = await form.show(player);
    if (res.canceled) return;
    const st = stateOf(player);
    switch (res.selection) {
      case 0:
        st.roll -= 25;
        player.sendMessage(`§aRoll ${st.roll.toFixed(0)}°`);
        break;
      case 1:
        st.roll += 25;
        player.sendMessage(`§aRoll ${st.roll.toFixed(0)}°`);
        break;
      case 2:
        pulseHold(player, "ascend", 12);
        break;
      case 3:
        pulseHold(player, "descend", 12);
        break;
      case 4:
        pulseHold(player, "strafeL", 12);
        break;
      case 5:
        pulseHold(player, "strafeR", 12);
        break;
      case 6:
        applyDash(player, st);
        break;
      case 7:
        pulseHold(player, "brake", 20);
        break;
      case 8:
        st.roll = 0;
        player.sendMessage("§6Roll reset");
        break;
      case 9:
        toggleAfterburner(player);
        break;
      case 10:
        toggle6dof(player);
        break;
      default:
        break;
    }
  } catch (e) {
    player.sendMessage("§cControl panel needs Beta APIs / server-ui");
  }
}

/** Temporary hold flags from panel taps (no selected item). */
const pulseFlags = new Map();

function pulseHold(player, key, ticks) {
  const id = player.id;
  if (!pulseFlags.has(id)) pulseFlags.set(id, {});
  const f = pulseFlags.get(id);
  f[key] = Math.max(f[key] ?? 0, ticks);
  player.sendMessage(`§7${key} ×${ticks}t`);
}

function activeHolds(player) {
  const holds = {
    rollL: false,
    rollR: false,
    ascend: false,
    descend: false,
    strafeL: false,
    strafeR: false,
    brake: false,
  };
  const sel = selectedId(player);
  const mapped = HOLD[sel];
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

function toggle6dof(player) {
  if (player.hasTag(TAG_6DOF)) {
    player.removeTag(TAG_6DOF);
    flights.delete(player.id);
    player.sendMessage("§76DoF OFF");
  } else {
    player.addTag(TAG_6DOF);
    player.sendMessage("§a6DoF ON — use Control Panel / hotbar buttons");
  }
}

function handleControlUse(player, id) {
  const st = stateOf(player);
  if (id === "drmd:ctrl_dash") {
    applyDash(player, st);
    return true;
  }
  if (id === "drmd:ctrl_reset_roll") {
    st.roll = 0;
    player.sendMessage("§6Roll reset");
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
  // Hold controls: tap also nudges once for discoverability
  if (id === "drmd:ctrl_roll_left") {
    st.roll -= 15;
    player.onScreenDisplay.setActionBar(`§aROLL ${st.roll.toFixed(0)}°`);
    return true;
  }
  if (id === "drmd:ctrl_roll_right") {
    st.roll += 15;
    player.onScreenDisplay.setActionBar(`§aROLL ${st.roll.toFixed(0)}°`);
    return true;
  }
  if (HOLD[id]) {
    pulseHold(player, HOLD[id], 8);
    return true;
  }
  return false;
}

world.afterEvents.playerSpawn.subscribe((ev) => {
  const player = ev.player;
  if (!ev.initialSpawn) return;
  system.runTimeout(() => {
    if (!player.isValid()) return;
    if (!player.hasTag(TAG_WELCOME)) {
      player.addTag(TAG_WELCOME);
      player.addTag(TAG_6DOF);
      player.sendMessage("§bDRMD 6DOF Fast Test §f(MCPE)");
      player.sendMessage("§7Hotbar buttons: Roll L/R · Ascend/Descend · Strafe · Dash");
      player.sendMessage("§7Open §aControl Panel §7item for all actions");
      player.sendMessage("§7Jump=thrust · Sneak=brake · hold selected = continuous");
      giveStarter(player);
    }
  }, 20);
});

world.afterEvents.itemUse.subscribe((ev) => {
  const player = ev.source;
  const id = ev.itemStack?.typeId ?? "";

  if (handleControlUse(player, id)) return;

  if (id === "drmd:pyro_beacon" || id === "minecraft:elytra") {
    toggle6dof(player);
  }
  if (id === "drmd:construction_wand" || id === "minecraft:stick") {
    if (player.hasTag(TAG_CONSTRUCT)) {
      player.removeTag(TAG_CONSTRUCT);
      player.sendMessage("§7Construction Mode OFF");
    } else {
      player.addTag(TAG_CONSTRUCT);
      player.sendMessage("§aConstruction Mode ON — place on any face");
    }
  }
  if (id === "drmd:gravity_torch_item" || id === "minecraft:torch") {
    player.sendMessage("§aGravity Torch — place to mark local DOWN");
  }
});

world.afterEvents.playerPlaceBlock.subscribe((ev) => {
  const player = ev.player;
  const block = ev.block;
  if (!player.hasTag(TAG_CONSTRUCT)) return;
  player.onScreenDisplay.setActionBar(`§aPlace @ ${block.x} ${block.y} ${block.z} · construct`);
});

/** Chat shortcuts for keyboard/controller players on Bedrock */
world.beforeEvents.chatSend?.subscribe?.((ev) => {
  const msg = (ev.message || "").trim().toLowerCase();
  if (!msg.startsWith("!d6") && !msg.startsWith("/d6")) return;
  // can't cancel easily on all versions — also support afterEvents below
});

world.afterEvents.chatSend?.subscribe?.((ev) => {
  const player = ev.sender;
  const msg = (ev.message || "").trim().toLowerCase();
  if (!msg.startsWith("!d6")) return;
  const arg = msg.slice(3).trim();
  const st = stateOf(player);
  if (arg === "panel" || arg === "") {
    system.run(() => openControlPanel(player));
  } else if (arg === "rolll" || arg === "ql") {
    st.roll -= 25;
  } else if (arg === "rollr" || arg === "er") {
    st.roll += 25;
  } else if (arg === "dash") {
    applyDash(player, st);
  } else if (arg === "reset") {
    st.roll = 0;
  } else if (arg === "kit") {
    giveControls(player);
    player.sendMessage("§aControl kit re-issued");
  } else if (arg === "help") {
    player.sendMessage("§b!d6 panel|rolll|rollr|dash|reset|kit");
  }
});

system.runInterval(() => {
  for (const player of world.getPlayers()) {
    if (!player.hasTag(TAG_6DOF)) continue;
    const st = stateOf(player);
    if (st.dashCd > 0) st.dashCd--;

    const view = player.getViewDirection();
    const axes = localAxes(view, st.roll);
    const holds = activeHolds(player);

    // Movement stick (touch joystick / WASD) when API available
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
    const thrustMul = after ? 0.12 : 0.08;
    let jumping = false;
    let sneaking = false;
    try {
      jumping = !!player.isJumping;
      sneaking = !!player.isSneaking;
    } catch (_) {}

    // Jump = forward thrust (classic sandbox)
    if (jumping) {
      st.x += axes.forward.x * thrustMul;
      st.y += axes.forward.y * thrustMul;
      st.z += axes.forward.z * thrustMul;
    }

    // Stick forward/back + strafe in look frame
    if (Math.abs(moveY) > 0.05) {
      const s = moveY * thrustMul;
      st.x += axes.forward.x * s;
      st.y += axes.forward.y * s;
      st.z += axes.forward.z * s;
    }
    if (Math.abs(moveX) > 0.05) {
      const s = -moveX * thrustMul * 0.85;
      st.x += axes.right.x * s;
      st.y += axes.right.y * s;
      st.z += axes.right.z * s;
    }

    // Hotbar / panel holds
    if (holds.ascend) {
      st.x += axes.up.x * thrustMul;
      st.y += axes.up.y * thrustMul;
      st.z += axes.up.z * thrustMul;
    }
    if (holds.descend) {
      st.x -= axes.up.x * thrustMul;
      st.y -= axes.up.y * thrustMul;
      st.z -= axes.up.z * thrustMul;
    }
    if (holds.strafeL) {
      st.x -= axes.right.x * thrustMul * 0.9;
      st.y -= axes.right.y * thrustMul * 0.9;
      st.z -= axes.right.z * thrustMul * 0.9;
    }
    if (holds.strafeR) {
      st.x += axes.right.x * thrustMul * 0.9;
      st.y += axes.right.y * thrustMul * 0.9;
      st.z += axes.right.z * thrustMul * 0.9;
    }

    // Barrel roll (continuous while selected)
    if (holds.rollL) st.roll -= 4.5;
    if (holds.rollR) st.roll += 4.5;
    while (st.roll > 180) st.roll -= 360;
    while (st.roll < -180) st.roll += 360;

    // Brake
    if (holds.brake || sneaking) {
      st.x *= 0.82;
      st.y *= 0.82;
      st.z *= 0.82;
    } else {
      st.x *= 0.97;
      st.y *= 0.97;
      st.z *= 0.97;
    }

    const speed = Math.sqrt(st.x * st.x + st.y * st.y + st.z * st.z);
    const maxSpd = after ? 2.1 : 1.6;
    if (speed > maxSpd) {
      const s = maxSpd / speed;
      st.x *= s;
      st.y *= s;
      st.z *= s;
    }

    try {
      player.applyKnockback(st.x, st.z, Math.min(1.25, speed * 0.9), st.y * 0.35);
    } catch (_) {
      try {
        player.applyImpulse({ x: st.x * 0.2, y: st.y * 0.2, z: st.z * 0.2 });
      } catch (__) {}
    }

    const spd = (Math.sqrt(st.x * st.x + st.y * st.y + st.z * st.z) * 20).toFixed(1);
    const mode = player.hasTag(TAG_CONSTRUCT) ? "CONSTRUCT" : "FLIGHT";
    const ab = after ? " §cAB" : "";
    const holdHint = selectedId(player).startsWith("drmd:ctrl_") ? " §e●" : "";
    player.onScreenDisplay.setActionBar(
      `§a6DOF §f${mode}${ab}${holdHint} §7SPD ${spd} §aROLL ${st.roll.toFixed(0)}° §7Y${player.location.y.toFixed(0)}`
    );
  }
}, 1);

world.afterEvents.playerLeave.subscribe((ev) => {
  flights.delete(ev.playerId);
  pulseFlags.delete(ev.playerId);
});

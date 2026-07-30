/**
 * DRMD 6DOF — MCPE Master / Bedrock Fast Test
 * Touch hotbar buttons + Control Panel. Defensive APIs for Master clients.
 */
import { system, world } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

const TAG_6DOF = "drmd_6dof";
const TAG_CONSTRUCT = "drmd_construct";
const TAG_WELCOME = "drmd_welcome";
const TAG_AFTERBURN = "drmd_afterburn";

/** @type {Map<string, { x:number,y:number,z:number, roll:number, dashCd:number }>} */
const flights = new Map();
const pulseFlags = new Map();

const HOLD = {
  "drmd:ctrl_roll_left": "rollL",
  "drmd:ctrl_roll_right": "rollR",
  "drmd:ctrl_ascend": "ascend",
  "drmd:ctrl_descend": "descend",
  "drmd:ctrl_strafe_left": "strafeL",
  "drmd:ctrl_strafe_right": "strafeR",
  "drmd:ctrl_brake": "brake",
};

function stateOf(player) {
  const id = player.id;
  if (!flights.has(id)) flights.set(id, { x: 0, y: 0, z: 0, roll: 0, dashCd: 0 });
  return flights.get(id);
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
    const stack = container.getItem(slot | 0);
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

function localAxes(view, rollDeg) {
  const forward = normalize(view);
  let right = cross(forward, { x: 0, y: 1, z: 0 });
  if (right.x * right.x + right.y * right.y + right.z * right.z < 1e-6) {
    right = { x: 1, y: 0, z: 0 };
  }
  right = normalize(right);
  const up = normalize(cross(right, forward));
  const rad = (rollDeg * Math.PI) / 180;
  const c = Math.cos(rad);
  const s = Math.sin(rad);
  return {
    forward,
    right: {
      x: right.x * c + up.x * s,
      y: right.y * c + up.y * s,
      z: right.z * c + up.z * s,
    },
    up: {
      x: up.x * c - right.x * s,
      y: up.y * c - right.y * s,
      z: up.z * c - right.z * s,
    },
  };
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
  for (const id of items) safeCommand(player, `give @s ${id} 1`);
}

function giveStarter(player) {
  safeCommand(player, "give @s drmd:pyro_beacon 1");
  safeCommand(player, "give @s drmd:gravity_torch_item 8");
  safeCommand(player, "give @s drmd:construction_wand 1");
  safeCommand(player, "give @s iron_block 32");
  // Vanilla fallbacks if custom items missing on older Master
  safeCommand(player, "give @s elytra 1");
  giveControls(player);
}

function applyDash(player, st) {
  if (st.dashCd > 0) {
    player.sendMessage("§7Рывок перезаряжается…");
    return;
  }
  const view = player.getViewDirection();
  const { forward } = localAxes(view, st.roll);
  const boost = player.hasTag(TAG_AFTERBURN) ? 1.4 : 0.95;
  st.x += forward.x * boost;
  st.y += forward.y * boost;
  st.z += forward.z * boost;
  st.dashCd = 18;
  player.sendMessage("§eРЫВОК / DASH");
  try {
    player.playSound("random.explode", { volume: 0.35, pitch: 1.6 });
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
    player.sendMessage("§a6DoF ВКЛ — кнопки хотбара / Панель");
  }
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
    ascend: false,
    descend: false,
    strafeL: false,
    strafeR: false,
    brake: false,
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

function panelChatFallback(player) {
  player.sendMessage("§b=== DRMD Панель (чат) ===");
  player.sendMessage("§f!d6 rolll §7крен←  §f!d6 rollr §7крен→");
  player.sendMessage("§f!d6 up|down|left|right §7импульс");
  player.sendMessage("§f!d6 dash §7рывок  §f!d6 brake §7тормоз");
  player.sendMessage("§f!d6 reset §7сброс крена  §f!d6 ab §7форсаж");
  player.sendMessage("§f!d6 kit §7выдать кнопки  §f!d6 toggle §76DoF");
}

async function openControlPanel(player) {
  const form = new ActionFormData()
    .title("DRMD Управление")
    .body("MCPE Master · удерживайте кнопку в хотбаре\nдля непрерывного ввода")
    .button("§aКрен влево")
    .button("§aКрен вправо")
    .button("§bВверх")
    .button("§bВниз")
    .button("§3Стрейф ←")
    .button("§3Стрейф →")
    .button("§eРывок")
    .button("§cТормоз")
    .button("§6Сброс крена")
    .button("§cФорсаж")
    .button(player.hasTag(TAG_6DOF) ? "§76DoF ВЫКЛ" : "§a6DoF ВКЛ")
    .button("§8Закрыть");

  try {
    const res = await form.show(player);
    if (res.canceled) return;
    const st = stateOf(player);
    switch (res.selection) {
      case 0:
        st.roll -= 25;
        break;
      case 1:
        st.roll += 25;
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
        player.sendMessage("§6Крен сброшен");
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
    st.roll = 0;
    player.sendMessage("§6Крен сброшен");
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
  if (id === "drmd:ctrl_roll_left") {
    st.roll -= 15;
    return true;
  }
  if (id === "drmd:ctrl_roll_right") {
    st.roll += 15;
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
  } else if (arg === "rolll" || arg === "ql") {
    st.roll -= 25;
  } else if (arg === "rollr" || arg === "er") {
    st.roll += 25;
  } else if (arg === "up") {
    pulseHold(player, "ascend", 12);
  } else if (arg === "down") {
    pulseHold(player, "descend", 12);
  } else if (arg === "left") {
    pulseHold(player, "strafeL", 12);
  } else if (arg === "right") {
    pulseHold(player, "strafeR", 12);
  } else if (arg === "dash") {
    applyDash(player, st);
  } else if (arg === "brake") {
    pulseHold(player, "brake", 20);
  } else if (arg === "reset") {
    st.roll = 0;
  } else if (arg === "ab" || arg === "afterburner") {
    toggleAfterburner(player);
  } else if (arg === "kit") {
    giveControls(player);
    player.sendMessage("§aКнопки выданы");
  } else if (arg === "toggle") {
    toggle6dof(player);
  } else {
    panelChatFallback(player);
  }
  return true;
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
      player.sendMessage("§bDRMD 6DOF §f— §aMCPE Master");
      player.sendMessage("§7Хотбар: Крен ←/→ · Вверх/Вниз · Стрейф · Рывок");
      player.sendMessage("§7Открой §aПанель управления §7или напиши §f!d6 panel");
      player.sendMessage("§7Прыжок=тяга · Красться=тормоз · удерживай кнопку=непрерывно");
      player.sendMessage("§eМир → Эксперименты → Beta APIs = ВКЛ");
      giveStarter(player);
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
  if (id === "drmd:gravity_torch_item" || id === "minecraft:torch") {
    player.sendMessage("§aГрави-факел — поставь, чтобы отметить локальный НИЗ");
  }
});

try {
  world.afterEvents.playerPlaceBlock.subscribe((ev) => {
    if (!ev.player.hasTag(TAG_CONSTRUCT)) return;
    const b = ev.block;
    ev.player.onScreenDisplay.setActionBar(`§aБлок ${b.x} ${b.y} ${b.z}`);
  });
} catch (_) {}

// Chat: support both legacy and current event names
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

    let view;
    try {
      view = player.getViewDirection();
    } catch (_) {
      continue;
    }
    const axes = localAxes(view, st.roll);
    const holds = activeHolds(player);

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

    if (jumping) {
      st.x += axes.forward.x * thrustMul;
      st.y += axes.forward.y * thrustMul;
      st.z += axes.forward.z * thrustMul;
    }
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
    if (holds.rollL) st.roll -= 4.5;
    if (holds.rollR) st.roll += 4.5;
    while (st.roll > 180) st.roll -= 360;
    while (st.roll < -180) st.roll += 360;

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
    const mode = player.hasTag(TAG_CONSTRUCT) ? "СТРОЙ" : "ПОЛЁТ";
    const ab = after ? " §cФС" : "";
    try {
      player.onScreenDisplay.setActionBar(
        `§a6DOF §f${mode}${ab} §7SPD ${spd} §aКРЕН ${st.roll.toFixed(0)}° §7Y${player.location.y.toFixed(0)}`
      );
    } catch (_) {}
  }
}, 1);

world.afterEvents.playerLeave.subscribe((ev) => {
  const id = ev.playerId || ev.player?.id;
  if (id) {
    flights.delete(id);
    pulseFlags.delete(id);
  }
});

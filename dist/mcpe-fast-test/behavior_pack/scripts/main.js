/**
 * DRMD 6DOF — MCPE Fast Test Edition
 * UX sandbox: 6DoF-ish flight, construction mode tip, gravity torch, mini HUD.
 * Heavy systems (LLOD / megastructures / full AI) live in the Fabric jar only.
 */
import {
  system,
  world,
  Player,
  EntityDamageCause,
  GameMode,
  ItemStack,
  MolangVariableMap
} from "@minecraft/server";

const TAG_6DOF = "drmd_6dof";
const TAG_CONSTRUCT = "drmd_construct";
const TAG_WELCOME = "drmd_welcome";

/** Per-player flight velocity (simplified inertia). */
const velocities = new Map();

function velOf(player) {
  const id = player.id;
  if (!velocities.has(id)) velocities.set(id, { x: 0, y: 0, z: 0 });
  return velocities.get(id);
}

function giveStarter(player) {
  try {
    player.runCommand("give @s drmd:pyro_beacon 1");
    player.runCommand("give @s drmd:gravity_torch_item 8");
    player.runCommand("give @s drmd:construction_wand 1");
    player.runCommand("give @s iron_block 64");
  } catch (e) {
    // Items may not resolve on older engines — fall back to vanilla
    try {
      player.runCommand("give @s elytra 1");
      player.runCommand("give @s firework_rocket 16");
      player.runCommand("give @s torch 8");
      player.runCommand("give @s iron_block 32");
    } catch (_) {}
  }
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
      player.sendMessage("§7Sneak+jump = thrust · hold use Pyro Beacon for dash");
      player.sendMessage("§7Construction Wand: tip for no-up building · Gravity Torch: local down");
      giveStarter(player);
    }
  }, 20);
});

world.afterEvents.itemUse.subscribe((ev) => {
  const player = ev.source;
  const id = ev.itemStack?.typeId ?? "";
  if (id === "drmd:pyro_beacon" || id === "minecraft:elytra") {
    // Toggle 6DoF
    if (player.hasTag(TAG_6DOF)) {
      player.removeTag(TAG_6DOF);
      velocities.delete(player.id);
      player.sendMessage("§76DoF OFF");
    } else {
      player.addTag(TAG_6DOF);
      player.sendMessage("§a6DoF ON — inertial flight");
    }
  }
  if (id === "drmd:construction_wand" || id === "minecraft:stick") {
    if (player.hasTag(TAG_CONSTRUCT)) {
      player.removeTag(TAG_CONSTRUCT);
      player.sendMessage("§7Construction Mode OFF");
    } else {
      player.addTag(TAG_CONSTRUCT);
      player.sendMessage("§aConstruction Mode ON — place on any face; no absolute up");
      player.sendMessage("§7(Full adaptive placement is in the Fabric PC build)");
    }
  }
  if (id === "drmd:gravity_torch_item" || id === "minecraft:torch") {
    player.sendMessage("§aGravity Torch — place to mark local DOWN for this section");
  }
});

world.afterEvents.playerPlaceBlock.subscribe((ev) => {
  const player = ev.player;
  const block = ev.block;
  if (!player.hasTag(TAG_CONSTRUCT)) return;
  // Soft feedback for face-relative building
  player.onScreenDisplay.setActionBar(`§aPlace @ ${block.x} ${block.y} ${block.z} · construct`);
});

/** Tick: simplified 6DoF inertia + HUD */
system.runInterval(() => {
  for (const player of world.getPlayers()) {
    if (!player.hasTag(TAG_6DOF)) continue;
    const v = velOf(player);
    const view = player.getViewDirection();
    const sneak = player.isSneaking;
    const jump = player.isJumping;

    // Thrust along look when jump; brake when sneak
    if (jump) {
      v.x += view.x * 0.08;
      v.y += view.y * 0.08;
      v.z += view.z * 0.08;
    }
    if (sneak) {
      v.x *= 0.85;
      v.y *= 0.85;
      v.z *= 0.85;
    } else {
      v.x *= 0.97;
      v.y *= 0.97;
      v.z *= 0.97;
    }

    // Clamp
    const speed = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
    if (speed > 1.6) {
      const s = 1.6 / speed;
      v.x *= s; v.y *= s; v.z *= s;
    }

    try {
      player.applyKnockback(v.x, v.z, Math.min(1.2, speed * 0.9), v.y * 0.35);
    } catch (_) {
      // older API
      try { player.applyImpulse({ x: v.x * 0.2, y: v.y * 0.2, z: v.z * 0.2 }); } catch (__) {}
    }

    const spd = (Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20).toFixed(1);
    const mode = player.hasTag(TAG_CONSTRUCT) ? "CONSTRUCT" : "FLIGHT";
    player.onScreenDisplay.setActionBar(
      `§a6DOF §f${mode} §7SPD ${spd} §aX${player.location.x.toFixed(0)} Y${player.location.y.toFixed(0)} Z${player.location.z.toFixed(0)}`
    );
  }
}, 1);

world.afterEvents.playerLeave.subscribe((ev) => {
  velocities.delete(ev.playerId);
});

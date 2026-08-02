package com.terminaldetector.drmd.pickup;

import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Separate loot-capture field around a 6DoF pilot — pulls nearby item entities
 * and auto-consumes shield/energy orbs (Descent powerup feel).
 */
public final class LootField {
	/** Capture radius in blocks while 6DoF is enabled. */
	public static final double RADIUS = 6.5;
	public static final double PULL = 0.28;

	private LootField() {}

	public static void tick(PlayerEntity player, DescentPlayerData data) {
		if (!(player instanceof ServerPlayerEntity) || !data.isEnabled()) return;
		Box box = player.getBoundingBox().expand(RADIUS);
		for (ItemEntity entity : player.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> !e.cannotPickup())) {
			ItemStack stack = entity.getStack();
			double dist = entity.squaredDistanceTo(player);
			if (dist < 1.6) {
				if (stack.isOf(com.terminaldetector.drmd.weapon.items.ModItems.SHIELD_ORB)) {
					ShieldOrbItem.apply(player, ShieldOrbItem.RESTORE * stack.getCount());
					entity.discard();
					continue;
				}
				if (stack.isOf(com.terminaldetector.drmd.weapon.items.ModItems.ENERGY_ORB_PICKUP)) {
					com.terminaldetector.drmd.energy.EnergySystem.add(data, EnergyOrbItem.RESTORE * stack.getCount());
					entity.discard();
					continue;
				}
			}
			// Soft pull toward the ship
			Vec3d to = player.getPos().add(0, 0.6, 0).subtract(entity.getPos());
			if (to.lengthSquared() > 1e-6) {
				Vec3d pull = to.normalize().multiply(PULL * (1.0 - Math.min(1.0, Math.sqrt(dist) / RADIUS)));
				entity.setVelocity(entity.getVelocity().multiply(0.85).add(pull));
				entity.velocityModified = true;
			}
		}
	}
}

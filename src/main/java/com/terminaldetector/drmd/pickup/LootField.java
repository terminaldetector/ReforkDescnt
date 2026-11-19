package com.terminaldetector.drmd.pickup;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.world.contraption.boarding.LooseItemBodyAccess;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * Separate loot-capture field around a 6DoF pilot — pulls nearby item entities
 * and auto-consumes shield/energy orbs (Descent powerup feel).
 */
public final class LootField {
	/** Capture radius in blocks while 6DoF is enabled. */
	public static final double RADIUS = 6.5;
	public static final double PULL = 0.28;
	public static final int MAX_ITEMS_PER_TICK = 64;
	private static final int OVERLOAD_PHASES = 8;
	private static final TypeFilter<net.minecraft.entity.Entity, ItemEntity> ITEMS = TypeFilter.instanceOf(ItemEntity.class);

	private LootField() {}

	public static void tick(PlayerEntity player, DescentPlayerData data) {
		if (!(player instanceof ServerPlayerEntity pilot) || !data.isEnabled() || player.isSpectator()) return;
		var world = pilot.getServerWorld();
		Box box = player.getBoundingBox().expand(RADIUS);
		Vec3d pullPoint = player.getPos().add(0, 0.6, 0);
		Predicate<ItemEntity> eligible = item -> item.isAlive() && !item.cannotPickup()
			&& item.squaredDistanceTo(player) < RADIUS * RADIUS
			&& (!(item instanceof LooseItemBodyAccess access) || access.drmd$canPickUpAs(player.getUuid()));
		var candidates = new ArrayList<ItemEntity>();
		world.collectEntitiesByType(ITEMS, box, eligible, candidates, MAX_ITEMS_PER_TICK + 1);
		if (candidates.size() > MAX_ITEMS_PER_TICK) {
			candidates.clear();
			int phase = (int) Math.floorMod(world.getTime(), OVERLOAD_PHASES);
			world.collectEntitiesByType(ITEMS, box,
				item -> Math.floorMod(item.getId(), OVERLOAD_PHASES) == phase && eligible.test(item),
				candidates, MAX_ITEMS_PER_TICK);
		}
		for (ItemEntity entity : candidates) {
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
			Vec3d to = pullPoint.subtract(entity.getPos());
			double length = to.lengthSquared();
			if (length > 1e-6) {
				Vec3d pull = to.multiply(PULL * (1.0 - Math.sqrt(dist) / RADIUS) / Math.sqrt(length));
				Vec3d current = entity.getVelocity();
				Vec3d next = current.multiply(0.85).add(pull);
				if (next.squaredDistanceTo(current) < 1e-8) continue;
				entity.setVelocity(next);
				entity.velocityModified = true;
			}
		}
	}
}

package com.terminaldetector.drmd.pickup;

import com.terminaldetector.drmd.weapon.items.ModItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

/**
 * Descent-style shield/energy orbs dropping from DRMD hostiles.
 */
public final class EnemyDrops {
	private EnemyDrops() {}

	public static void onDroneDeath(LivingEntity drone, DamageSource source, float shieldChance, float energyChance) {
		if (!(drone.getWorld() instanceof ServerWorld sw)) return;
		Random r = drone.getRandom();
		if (r.nextFloat() < shieldChance) {
			spawn(sw, drone, new ItemStack(ModItems.SHIELD_ORB));
		}
		if (r.nextFloat() < energyChance) {
			spawn(sw, drone, new ItemStack(ModItems.ENERGY_ORB_PICKUP));
		}
	}

	private static void spawn(ServerWorld sw, LivingEntity at, ItemStack stack) {
		ItemEntity e = new ItemEntity(sw, at.getX(), at.getY() + 0.4, at.getZ(), stack);
		e.setPickupDelay(10);
		e.setVelocity((sw.random.nextDouble() - 0.5) * 0.15, 0.25, (sw.random.nextDouble() - 0.5) * 0.15);
		sw.spawnEntity(e);
	}
}

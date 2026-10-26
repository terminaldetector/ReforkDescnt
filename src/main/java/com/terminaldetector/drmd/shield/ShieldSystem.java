package com.terminaldetector.drmd.shield;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Player shields — port of d6_shield.lua.
 * Absorbs damage before HP when 6DOF is on. Regen delay 4s after hit;
 * rate = 12 * (0.5 + shieldsAlloc) /s.
 */
public final class ShieldSystem {
	public static final float SHIELD_MAX = 100f;
	public static final float REGEN_DELAY = 4f;
	public static final float REGEN_BASE = 12f;

	private ShieldSystem() {}

	public static void regenTick(PlayerEntity player, DescentPlayerData data) {
		float dt = 1f / 20f;
		if (data.getShieldRegenDelay() > 0) {
			data.setShieldRegenDelay(data.getShieldRegenDelay() - dt);
			return;
		}
		float rate = REGEN_BASE * (0.5f + data.getAllocShields());
		rate *= com.terminaldetector.drmd.world.trap.ShieldProjectors.regenBoost(
				player.getWorld(), player.getPos());
		data.setShield(Math.min(data.getShieldMax(), data.getShield() + rate * dt));
	}

	/**
	 * Absorb damage into shields. Returns remaining damage that should hit HP.
	 */
	public static float absorb(DescentPlayerData data, float amount, DamageClass dmgClass) {
		if (!data.isEnabled() || amount <= 0) return amount;
		float resist = resistFor(dmgClass);
		float effective = amount * (1f - resist);
		float shield = data.getShield();
		if (shield <= 0) return effective;
		data.setShieldRegenDelay(REGEN_DELAY);
		if (shield >= effective) {
			data.setShield(shield - effective);
			return 0f;
		}
		data.setShield(0f);
		return effective - shield;
	}

	private static float resistFor(DamageClass c) {
		// Player shields: mild resist by class (drones have full resist tables)
		return switch (c) {
			case KINETIC -> 0.05f;
			case ENERGY -> 0.15f;
			case EXPLOSIVE -> 0.0f;
			case EXOTIC -> 0.10f;
		};
	}

	public static void onHit(DescentPlayerData data) {
		data.setShieldRegenDelay(REGEN_DELAY);
	}
}

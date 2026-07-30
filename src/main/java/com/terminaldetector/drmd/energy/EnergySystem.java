package com.terminaldetector.drmd.energy;

import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared energy pool — port of d6_energy.lua.
 * Channels (weapons/shields/engines) are allocation weights; consumption is from the shared pool.
 */
public final class EnergySystem {
	public static final float ENERGY_MAX = 100f;
	public static final float ENERGY_REGEN_BASE = 8f;
	public static final float ALWAYS_RUN_COST_PER_SEC = 6f;
	public static final float DASH_COST = 15f;

	private EnergySystem() {}

	public static void regenTick(PlayerEntity player, DescentPlayerData data) {
		float dt = 1f / 20f;
		float rate = ENERGY_REGEN_BASE * (0.5f + data.getAllocWeapons());
		float next = Math.min(data.getEnergyMax(), data.getEnergy() + rate * dt);
		data.setEnergy(next);

		// Gravy railgun has its own pool (regen 10/s)
		float gravy = Math.min(100f, data.getGravyEnergy() + 10f * dt);
		data.setGravyEnergy(gravy);
	}

	public static boolean tryConsume(DescentPlayerData data, String channel, float amount) {
		if (amount <= 0) return true;
		if (data.getEnergy() < amount) return false;
		data.setEnergy(data.getEnergy() - amount);
		return true;
	}

	public static void add(DescentPlayerData data, float amount) {
		data.setEnergy(Math.min(data.getEnergyMax(), data.getEnergy() + amount));
	}

	public static void setPreset(DescentPlayerData data, EnergyPreset preset) {
		data.setPreset(preset);
		data.setAlloc(preset.weapons, preset.shields, preset.engines);
	}

	public static float getAlloc(DescentPlayerData data, String channel) {
		return switch (channel) {
			case "weapons" -> data.getAllocWeapons();
			case "shields" -> data.getAllocShields();
			case "engines" -> data.getAllocEngines();
			default -> 0f;
		};
	}
}

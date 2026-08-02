package com.terminaldetector.drmd.energy;

/**
 * Energy allocation presets from d6_energy.lua.
 * Regen formula: ENERGY_REGEN_BASE * (0.5 + weaponsAlloc) where base = 8/s.
 */
public enum EnergyPreset {
	BALANCED("balanced", 0.34f, 0.33f, 0.33f),
	ASSAULT("assault", 0.55f, 0.25f, 0.20f),
	INTERCEPTOR("interceptor", 0.25f, 0.20f, 0.55f),
	SIEGE("siege", 0.45f, 0.45f, 0.10f);

	public final String id;
	public final float weapons;
	public final float shields;
	public final float engines;

	EnergyPreset(String id, float weapons, float shields, float engines) {
		this.id = id;
		this.weapons = weapons;
		this.shields = shields;
		this.engines = engines;
	}

	public static EnergyPreset fromId(String id) {
		if (id == null || id.isEmpty()) return BALANCED;
		for (EnergyPreset p : values()) {
			if (p.id.equalsIgnoreCase(id)) return p;
		}
		return BALANCED;
	}
}

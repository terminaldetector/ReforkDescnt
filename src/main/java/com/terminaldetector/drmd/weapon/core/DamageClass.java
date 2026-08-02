package com.terminaldetector.drmd.weapon.core;

/**
 * Damage taxonomy from d6_weapon_core.lua — drives tracer color and shield resist channels.
 */
public enum DamageClass {
	KINETIC("kinetic", 180, 220, 255),
	ENERGY("energy", 80, 255, 140),
	EXPLOSIVE("explosive", 255, 140, 40),
	EXOTIC("exotic", 0, 220, 255);

	public final String id;
	public final int r, g, b;

	DamageClass(String id, int r, int g, int b) {
		this.id = id;
		this.r = r;
		this.g = g;
		this.b = b;
	}

	public static DamageClass fromId(String id) {
		if (id == null) return KINETIC;
		for (DamageClass c : values()) {
			if (c.id.equalsIgnoreCase(id)) return c;
		}
		return KINETIC;
	}
}

package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.weapon.core.DamageClass;

/**
 * Role loadouts from d6_ai_roles.lua → D6_BuildDrone.
 */
public enum AiRole {
	ASSAULT("assault", "pressure", 200, 540, 50, 0, 0f, 0.1f, 0f,
			2f, 0.15f, 10f, 0f, 0f, 5000f, DamageClass.ENERGY, false, false),
	INTERCEPTOR("interceptor", "flank", 150, 860, 30, 0, 0f, 0f, 0f,
			1f, 0.06f, 8f, 0f, 0f, 6000f, DamageClass.ENERGY, false, false),
	ARTILLERY("artillery", "standoff", 180, 240, 0, 60, 0.2f, 0f, 0.1f,
			20f, 2.5f, 40f, 30f, 120f, 2200f, DamageClass.EXPLOSIVE, true, true),
	SUPPORT("support", "support", 220, 600, 40, 20, 0f, 0.15f, 0f,
			0f, 0.5f, 0f, 0f, 0f, 0f, DamageClass.ENERGY, false, false),
	HEAVY_ELITE("heavy_elite", "anchor", 800, 200, 150, 100, 0.2f, 0.2f, 0.3f,
			30f, 3.5f, 90f, 60f, 180f, 1100f, DamageClass.EXPLOSIVE, false, false),
	// Legacy variants
	MG("mg", "pressure", 120, 480, 0, 0, 0f, 0f, 0f,
			1f, 0.08f, 9f, 0f, 0f, 5000f, DamageClass.KINETIC, false, false),
	LASER("laser", "flank", 150, 520, 20, 0, 0f, 0.1f, 0f,
			1f, 0.10f, 9f, 0f, 0f, 6000f, DamageClass.ENERGY, false, false),
	RPG("rpg", "standoff", 150, 400, 0, 20, 0.1f, 0f, 0f,
			15f, 1.8f, 50f, 40f, 140f, 2800f, DamageClass.EXPLOSIVE, false, true),
	HEAVY("heavy", "anchor", 450, 200, 40, 40, 0.15f, 0.1f, 0.2f,
			25f, 2.2f, 90f, 60f, 180f, 1100f, DamageClass.EXPLOSIVE, false, false),
	SEEKER("seeker", "flank", 280, 500, 30, 0, 0f, 0.05f, 0f,
			18f, 2.0f, 70f, 40f, 160f, 2200f, DamageClass.EXPLOSIVE, true, true),
	GRAV("grav", "pressure", 150, 450, 20, 0, 0f, 0f, 0f,
			10f, 1.5f, 25f, 0f, 0f, 3000f, DamageClass.EXOTIC, false, false);

	public final String id, ai;
	public final float hullHp, speed, shield, armor;
	public final float resistKinetic, resistEnergy, resistExplosive;
	public final float weaponCost, weaponCooldown, weaponDamage, weaponSplash, weaponSplashRadius, weaponSpeed;
	public final DamageClass weaponClass;
	public final boolean weaponHoming;
	/** True → drone will climb over villages and drop aerial bombs. */
	public final boolean villageBomber;

	AiRole(String id, String ai, float hullHp, float speed, float shield, float armor,
		   float rk, float re, float rx,
		   float cost, float cd, float dmg, float splash, float splashR, float spd,
		   DamageClass cls, boolean homing, boolean villageBomber) {
		this.id = id; this.ai = ai; this.hullHp = hullHp; this.speed = speed;
		this.shield = shield; this.armor = armor;
		this.resistKinetic = rk; this.resistEnergy = re; this.resistExplosive = rx;
		this.weaponCost = cost; this.weaponCooldown = cd; this.weaponDamage = dmg;
		this.weaponSplash = splash; this.weaponSplashRadius = splashR; this.weaponSpeed = spd;
		this.weaponClass = cls; this.weaponHoming = homing;
		this.villageBomber = villageBomber;
	}

	public static AiRole fromId(String id) {
		if (id == null || id.isEmpty()) return ASSAULT;
		for (AiRole r : values()) if (r.id.equalsIgnoreCase(id)) return r;
		return ASSAULT;
	}
}

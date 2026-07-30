package com.terminaldetector.drmd.weapon.projectile;

import com.terminaldetector.drmd.weapon.core.DamageClass;

/**
 * Unified projectile kinds — one collision / world-interaction pipeline.
 */
public enum ProjectileKind {
	LASER(DamageClass.ENERGY, 0.0f, 0.0f, false, 0x66FFAA),
	PLASMA(DamageClass.EXOTIC, 0.05f, 0.02f, false, 0xAA66FF),
	KINETIC(DamageClass.KINETIC, 0.15f, 0.01f, false, 0xCCDDEE),
	ROCKET(DamageClass.EXPLOSIVE, 0.08f, 0.005f, true, 0xFF8844),
	GRAVITY_SPHERE(DamageClass.EXOTIC, 0.0f, 0.04f, false, 0x44FFAA),
	ENERGY_ORB(DamageClass.ENERGY, 0.02f, 0.03f, false, 0x44AAFF),
	DRILL_CHARGE(DamageClass.KINETIC, 0.2f, 0.0f, false, 0xFFAA33),
	/** Seeded charge that settles where it lands and waits on a proximity fuse. */
	PROXIMITY_MINE(DamageClass.EXOTIC, 0.35f, 0.10f, false, 0xB36BFF),
	/** Flak: a timed fuse bursts it into a shrapnel cloud short of the target. */
	AIRBURST(DamageClass.EXPLOSIVE, 0.10f, 0.01f, false, 0xFFB347),
	/** Burn lance: a slower energy bolt that leaves fire behind it. */
	BURN_LANCE(DamageClass.ENERGY, 0.0f, 0.0f, false, 0xFF6A2B);

	public final DamageClass damageClass;
	public final float gravity;
	public final float drag;
	public final boolean defaultHoming;
	public final int colorRgb;

	ProjectileKind(DamageClass damageClass, float gravity, float drag, boolean homing, int colorRgb) {
		this.damageClass = damageClass;
		this.gravity = gravity;
		this.drag = drag;
		this.defaultHoming = homing;
		this.colorRgb = colorRgb;
	}
}

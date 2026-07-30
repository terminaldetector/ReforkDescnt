package com.terminaldetector.drmd.world.atmosphere;

/**
 * Atmospheric / pressure bands — Minecraft stays recognizable, rules shift with altitude.
 *
 * Speculative continuum (−50k…100k) mapped to practical Overworld until custom height ships.
 */
public enum AtmosphereBand {
	/** −50k…−20k speculative / deep Overworld — high pressure, magma, long blast tunnels. */
	DEEP_PRESSURE(
			"Deep Pressure",
			-50_000, -20_000,
			-512, -20,
			1.35f, 1.6f, 0.55f, true, false),
	/** Classic physics — water/lava as players expect. */
	CLASSIC(
			"Classic Atmosphere",
			0, 10_000,
			-20, 200,
			1.0f, 1.0f, 1.0f, false, false),
	/** 10k–30k — thin air; water slows/droplets; smoke lasts; bombs accelerate. */
	THIN(
			"Thin Atmosphere",
			10_000, 30_000,
			200, 420,
			0.45f, 0.7f, 1.35f, false, true),
	/** 30k+ — near-space; no free water sources; thrusters primary; full 6DoF combat. */
	NEAR_SPACE(
			"Near Space",
			30_000, 120_000,
			420, 1024,
			0.05f, 0.35f, 1.8f, false, true);

	public final String label;
	public final int specYMin, specYMax;
	public final int practicalYMin, practicalYMax;
	/** Air drag multiplier on flight / falling bombs (1 = normal). */
	public final float airDrag;
	/** Explosion blast radius scale in tunnels / open air. */
	public final float blastScale;
	/** Engine / dash thrust bonus (near-space needs thrusters). */
	public final float thrustScale;
	public final boolean highPressure;
	/** Free water sources suppressed (game rule, not realism sim). */
	public final boolean suppressFreeWater;

	AtmosphereBand(String label, int specMin, int specMax, int pracMin, int pracMax,
				   float airDrag, float blastScale, float thrustScale,
				   boolean highPressure, boolean suppressFreeWater) {
		this.label = label;
		this.specYMin = specMin;
		this.specYMax = specMax;
		this.practicalYMin = pracMin;
		this.practicalYMax = pracMax;
		this.airDrag = airDrag;
		this.blastScale = blastScale;
		this.thrustScale = thrustScale;
		this.highPressure = highPressure;
		this.suppressFreeWater = suppressFreeWater;
	}

	/** Resolve band from world Y (practical Overworld mapping). */
	public static AtmosphereBand at(double y) {
		if (y < DEEP_PRESSURE.practicalYMax) return DEEP_PRESSURE;
		if (y < CLASSIC.practicalYMax) return CLASSIC;
		if (y < THIN.practicalYMax) return THIN;
		return NEAR_SPACE;
	}

	/** Dimension-aware: true End vacuum uses near-space flight rules. */
	public static AtmosphereBand at(net.minecraft.world.World world, double y) {
		if (world != null && world.getRegistryKey() == net.minecraft.world.World.END) {
			return NEAR_SPACE;
		}
		return at(y);
	}

	/** Smoke particle lifetime multiplier. */
	public float smokeLifeScale() {
		return switch (this) {
			case CLASSIC -> 1.0f;
			case THIN -> 1.8f;
			case NEAR_SPACE -> 2.4f;
			case DEEP_PRESSURE -> 1.3f;
		};
	}
}

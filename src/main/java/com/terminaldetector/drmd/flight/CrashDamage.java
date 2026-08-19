package com.terminaldetector.drmd.flight;

/**
 * How much a flight-speed collision hurts. A tunable starting point, not a measured constant —
 * matching {@code docs/MOVEMENT.md}'s own stated idiom for this project's other flight-feel
 * numbers ("a reasoned starting point, not a tuned one").
 *
 * <p>{@link #THRESHOLD} sits above the base, no-afterburner top speed and below a typical
 * afterburner-boosted one: {@code DescentPlayerData}'s default {@code maxSpeed=2200f} source units,
 * scaled by {@code DescentMod.UNIT_SCALE=1/80}, is ~27.5 blocks/s with no burn; a red-tier
 * afterburner at full engine allocation (~2.6x per {@code AfterburnerTiers.speedMult}) plus vacuum
 * atmosphere (+15%) can reach roughly 70-80 blocks/s. So ordinary cruising, even at its own hard
 * cap, never triggers this; sustained hard burning into something can.
 */
public final class CrashDamage {
	public static final double THRESHOLD = 40.0;
	public static final double SCALE = 10.0;
	public static final float MAX_DAMAGE = 60f;

	private CrashDamage() {}

	/** Damage for hitting something at {@code speedBlocksPerSecond} — 0 at/under the threshold. */
	public static float damageFor(double speedBlocksPerSecond) {
		if (speedBlocksPerSecond <= THRESHOLD) return 0f;
		double over = (speedBlocksPerSecond - THRESHOLD) / SCALE;
		return (float) Math.min(over * over, MAX_DAMAGE);
	}
}

package com.terminaldetector.drmd.flight;

/**
 * Playable speed envelope relative to vanilla elytra.
 *
 * <p>Old Source-unit defaults (maxSpeed 2200 × 1/80) allowed ~550 blocks/s and tunneled
 * through walls (no-clip). Caps are in blocks/second; convert to per-tick with /20.
 */
public final class FlightSpeeds {
	private FlightSpeeds() {}

	/** Typical sustained elytra glide. */
	public static final double ELYTRA_BPS = 30.0;

	/** Cruise thrusters — a bit above elytra. */
	public static final double CRUISE_MAX_BPS = ELYTRA_BPS * 1.2; // 36

	/** Always-Run / форсаж — four elytra. */
	public static final double AFTERBURN_MAX_BPS = ELYTRA_BPS * 4.0; // 120

	/** Accel (blocks/s²) — snappy spool without instant redline. */
	public static final double CRUISE_ACCEL = 26.0;
	public static final double AFTERBURN_ACCEL = 48.0;

	/** Dash impulse in blocks/tick (was ~40 — pure tunnel). */
	public static final double DASH_IMPULSE = 2.4;

	/** Max movement per collision sub-step — prevents wall tunneling. */
	public static final double COLLISION_STEP = 0.55;
	public static final int COLLISION_MAX_STEPS = 12;

	public static double maxBlocksPerTick(boolean afterburner, float spool) {
		double cap = afterburner ? AFTERBURN_MAX_BPS : CRUISE_MAX_BPS;
		// Dynamic: spool raises the ceiling from 45% → 100%
		double spoolScale = 0.45 + 0.55 * Math.max(0.0, Math.min(1.0, spool));
		return (cap * spoolScale) / 20.0;
	}

	public static double accelBlocksPerSec2(boolean afterburner) {
		return afterburner ? AFTERBURN_ACCEL : CRUISE_ACCEL;
	}
}

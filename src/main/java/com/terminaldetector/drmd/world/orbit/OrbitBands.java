package com.terminaldetector.drmd.world.orbit;

import com.terminaldetector.drmd.world.level.WorldLevels;

/**
 * Orbit ≠ End. High stock-world altitude holds junk, inverted villages, artifacts.
 * The techno-ring is the backbone; End requires overcoming max column height.
 *
 * <pre>
 *   ORBITAL band (Y WorldLevels.SKY_TOP…ORBITAL_TOP):
 *     RING zone   — dense techno-ring islands / satellites / artifacts (XZ ring cells)
 *     LAYER_A     — space junk field (one high slab inside orbital)
 *     LAYER_B     — second junk / satellite slab, also left/right of the ring
 *
 *   Above ORBITAL_TOP → End level (separate task / techno-ring vista at the seam)
 * </pre>
 */
public final class OrbitBands {
	/** Major techno-ring radius in blocks (procedural island anchors). */
	public static final int RING_RADIUS = 2048;
	public static final int RING_WIDTH = 220;

	/** Layer A — lower orbital junk slab. */
	public static final int LAYER_A_Y = WorldLevels.SKY_TOP + 40;
	/** Layer B — upper orbital junk / side-of-ring slab. */
	public static final int LAYER_B_Y = WorldLevels.SKY_TOP + 140;
	/** Techno-ring deck height. */
	public static final int RING_Y = WorldLevels.SKY_TOP + 90;

	public enum Zone {
		RING("Techno-ring"),
		LAYER_A("Orbit junk A"),
		LAYER_B("Orbit junk B"),
		VOID("Orbital void");

		public final String label;
		Zone(String label) { this.label = label; }
	}

	private OrbitBands() {}

	public static boolean inOrbitalBand(double y) {
		return y >= WorldLevels.SKY_TOP && y < WorldLevels.ORBITAL_TOP;
	}

	/** Horizontal distance from world origin used as ring centre for stock worlds. */
	public static double ringDist(int x, int z) {
		return Math.sqrt((double) x * x + (double) z * z);
	}

	public static boolean inRing(int x, int z) {
		double d = ringDist(x, z);
		return Math.abs(d - RING_RADIUS) <= RING_WIDTH;
	}

	/** Left / right of ring = radial outside or inside the ring band. */
	public static boolean besideRing(int x, int z) {
		double d = ringDist(x, z);
		return Math.abs(d - RING_RADIUS) > RING_WIDTH
				&& Math.abs(d - RING_RADIUS) < RING_WIDTH * 3;
	}

	public static Zone zoneAt(int x, int y, int z) {
		if (!inOrbitalBand(y)) return Zone.VOID;
		if (inRing(x, z) && Math.abs(y - RING_Y) < 48) return Zone.RING;
		if (besideRing(x, z) || Math.abs(y - LAYER_A_Y) < 36) {
			if (y < (LAYER_A_Y + LAYER_B_Y) / 2) return Zone.LAYER_A;
			return Zone.LAYER_B;
		}
		if (Math.abs(y - LAYER_B_Y) < 40) return Zone.LAYER_B;
		if (Math.abs(y - LAYER_A_Y) < 40) return Zone.LAYER_A;
		return Zone.VOID;
	}

	public static String describe(int x, int y, int z) {
		Zone z0 = zoneAt(x, y, z);
		return z0.label + " · ringR=" + RING_RADIUS
				+ " · dist=" + (int) ringDist(x, z)
				+ (y >= WorldLevels.ORBITAL_TOP - 8
				? " · §dapproach End seam / techno-ring vista"
				: "");
	}
}

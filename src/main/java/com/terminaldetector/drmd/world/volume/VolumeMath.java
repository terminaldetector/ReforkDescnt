package com.terminaldetector.drmd.world.volume;

/** Scalar field operations shared by the procedural volume presets. */
public final class VolumeMath {
	public static final int SPAWN_Y = 80;
	public static final double DENSITY_LIMIT = 32.0;

	private VolumeMath() {}

	public static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public static double limitDensity(double value) {
		return clamp(value, -DENSITY_LIMIT, DENSITY_LIMIT);
	}

	public static double lerp(double from, double to, double t) {
		return from + (to - from) * t;
	}

	public static double smoothstep(double edge0, double edge1, double value) {
		double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	/** Coordinates relative to the nearest cell center, across positive and negative axes. */
	public static double repeat(double coordinate, double period) {
		return coordinate - Math.floor(coordinate / period + 0.5) * period;
	}

	public static double maxAbs(double x, double y, double z) {
		return Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
	}

	public static double median(double a, double b, double c) {
		return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
	}

	public static double box(double x, double y, double z, double halfX, double halfY, double halfZ) {
		return Math.min(halfX - Math.abs(x), Math.min(halfY - Math.abs(y), halfZ - Math.abs(z)));
	}

	/** Reserve a spawn deck and safe walking space in the generated occupancy field. */
	public static double launchDeck(double density, int x, int y, int z) {
		if (y < SPAWN_Y - 9 || y > SPAWN_Y + 22 || Math.abs((double) x) > 24 || Math.abs((double) z) > 28) {
			return density;
		}
		double dx = x + 0.5;
		double dz = z + 0.5;
		double dy = y - SPAWN_Y + 0.5;
		double clearance = box(dx, dy - 6, dz, 24, 15, 28);
		double slab = box(dx, dy + 1.5, dz, 20, 1.5, 24);
		return Math.max(Math.min(density, -clearance), slab);
	}

	public static boolean isLaunchDeck(int x, int y, int z) {
		return y >= SPAWN_Y - 3 && y < SPAWN_Y && x >= -20 && x < 20 && z >= -24 && z < 24;
	}

	public static VolumeField.Material deckMaterial(int x, int y, int z) {
		return y == SPAWN_Y - 1 && (x == -18 || x == 17) && Math.floorMod(z, 8) == 0
				? VolumeField.Material.LIGHT : VolumeField.Material.ENGINEERED;
	}

	public static long hash(long seed, long x, long y, long z) {
		long value = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xBF58476D1CE4E5B9L ^ z * 0x94D049BB133111EBL;
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}
}

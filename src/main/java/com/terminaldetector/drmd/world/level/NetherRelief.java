package com.terminaldetector.drmd.world.level;

/**
 * The shape of the Nether band's floor and ceiling.
 *
 * <p>The band used to be a slab of floor, a slab of ceiling and a hundred and eighty blocks of
 * nothing in between — a room, not a place. What makes the Nether read as the Nether from a cockpit
 * is relief: a netherrack surface that rises and falls, a lava sea filling what it does not reach,
 * and a ceiling that comes down to meet it in places. This supplies the two heights; the fill is
 * {@code LevelBuilder}'s.
 *
 * <p>Value noise rather than the world generator's, because it has to be a pure function of world
 * coordinates. The band is written chunk by chunk by a background stream, so two neighbours built
 * seconds apart on different ticks must agree about the block they share — anything carrying state
 * from chunk to chunk would put a cliff on every chunk border, which is the exact seam the column is
 * meant not to have.
 */
public final class NetherRelief {
	/** Coarse rolling shape. */
	private static final int CELL_BROAD = 48;
	/** Detail on top of it. */
	private static final int CELL_FINE = 13;

	/** Floor relief above the floor slab, in blocks. */
	public static final int FLOOR_RELIEF = 22;
	/** How far the ceiling can hang down below the ceiling slab, in blocks. */
	public static final int CEILING_DROP = 16;

	private NetherRelief() {}

	/** Top of the walkable ground at this column — the first air block sits above it. */
	public static int floorTop(long seed, int x, int z) {
		int base = WorldLevels.NETHER_FLOOR + WorldLevels.NETHER_FLOOR_THICKNESS;
		return base + (int) (fractal(seed, x, z) * FLOOR_RELIEF);
	}

	/** Lowest block of the ceiling at this column — air runs from here down. */
	public static int ceilingBottom(long seed, int x, int z) {
		int base = WorldLevels.NETHER_CEILING - WorldLevels.NETHER_CEILING_THICKNESS;
		// Offset the seed so the ceiling is not a mirror of the floor under it.
		return base - (int) (fractal(seed ^ 0x5DEECE66DL, x, z) * CEILING_DROP);
	}

	/**
	 * Height of the lava sea.
	 *
	 * <p>Sits low in the relief so most of the floor stands clear of it and the rest becomes coast.
	 * A sea that covered everything would read as a lava plain, and one nothing reached would not
	 * read at all.
	 */
	public static int lavaLevel() {
		return WorldLevels.NETHER_FLOOR + WorldLevels.NETHER_FLOOR_THICKNESS + 5;
	}

	/** Two octaves of value noise in 0…1. */
	public static float fractal(long seed, int x, int z) {
		float broad = value(seed, x, z, CELL_BROAD);
		float fine = value(seed ^ 0x9E3779B9L, x, z, CELL_FINE);
		return clamp01(broad * 0.72f + fine * 0.28f);
	}

	/** Smoothstep-interpolated value noise on a lattice of {@code cell}-wide squares. */
	public static float value(long seed, int x, int z, int cell) {
		int x0 = Math.floorDiv(x, cell);
		int z0 = Math.floorDiv(z, cell);
		float tx = smooth((x - x0 * cell) / (float) cell);
		float tz = smooth((z - z0 * cell) / (float) cell);
		float a = corner(seed, x0, z0);
		float b = corner(seed, x0 + 1, z0);
		float c = corner(seed, x0, z0 + 1);
		float d = corner(seed, x0 + 1, z0 + 1);
		return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	/** SplitMix-style avalanche on the lattice point, so neighbours are uncorrelated. */
	private static float corner(long seed, int gx, int gz) {
		long h = seed
				+ gx * 0x9E3779B97F4A7C15L
				+ gz * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 30;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 27;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) / (float) (1L << 53);
	}
}

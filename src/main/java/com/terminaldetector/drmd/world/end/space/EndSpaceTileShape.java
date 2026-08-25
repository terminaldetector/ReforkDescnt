package com.terminaldetector.drmd.world.end.space;

/**
 * Pure geometry for the first Layer 2 ("End Space") tile — zero Minecraft imports, same idiom as
 * {@code CitadelDeckShape}: directly unit-testable, caller does all the actual block placement.
 *
 * <p>A handful of thin ring platforms stacked with open air between them, not one solid mass — the
 * whole point of this tile is proving out Phase B1's real vertical headroom with something a pilot
 * flies <em>through</em>, not a taller version of the flat floating islands Layer 2 was until now.
 * Content variety (more shapes, more platforms, hazards) is deliberately out of scope here — see the
 * plan's Phase B4.
 */
public final class EndSpaceTileShape {
	/** Horizontal half-extent of each platform ring — a 25x25 footprint, modest next to the Citadel's 73x73. */
	public static final int HALF_EXTENT = 12;
	/** Inner radius of the ring — the platform is a hollow annulus, not a solid disc, so it reads as an
	 * open-centre deck edge rather than a plugged disc a pilot would have to fly around. */
	public static final int RING_INNER = 8;
	/** Vertical gap between platforms — open air a pilot flies through, the reason this tile exists. */
	public static final int PLATFORM_SPACING = 96;
	/** How many platforms stack in one tile. */
	public static final int PLATFORM_COUNT = 4;
	/** Total local-Y span one tile occupies, top platform inclusive — callers use this to keep
	 * neighbouring tiles (and the dimension's own height limit) clear of a half-built stack. */
	public static final int TILE_HEIGHT = PLATFORM_SPACING * (PLATFORM_COUNT - 1) + 1;

	private EndSpaceTileShape() {}

	public enum Cell { PLATFORM, BEACON, NONE }

	/**
	 * Classify a local cell relative to the tile's own footprint centre and base Y. {@code y} is only
	 * ever meaningful at a platform's own level ({@code y == p * PLATFORM_SPACING} for some
	 * {@code 0 <= p < PLATFORM_COUNT}) — everything between is deliberately {@code NONE}, that gap
	 * being the entire point.
	 */
	public static Cell classify(int x, int y, int z) {
		if (y < 0 || y >= TILE_HEIGHT) return Cell.NONE;
		if (y % PLATFORM_SPACING != 0) return Cell.NONE;

		int ax = Math.abs(x), az = Math.abs(z);
		if (ax > HALF_EXTENT || az > HALF_EXTENT) return Cell.NONE;

		long r2 = (long) x * x + (long) z * z;
		if (r2 < (long) RING_INNER * RING_INNER) return Cell.NONE;

		boolean corner = ax == HALF_EXTENT && az == HALF_EXTENT;
		return corner ? Cell.BEACON : Cell.PLATFORM;
	}
}

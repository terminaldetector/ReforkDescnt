package com.terminaldetector.drmd.world.store;

/**
 * One section's address, packed into a long: level of detail plus a cell-grid coordinate.
 *
 * <p>Keys are the whole interface between what stores sections and what makes them. A backend never
 * needs to know what a section contains, only where it goes — which is what lets the same store hold
 * the surface sections drawn today and the voxel sections that will replace them.
 *
 * <pre>
 *   bits 58..63  level   (0..63)
 *   bits 29..57  x       (29-bit signed)
 *   bits  0..28  z       (29-bit signed)
 * </pre>
 *
 * <p>29 bits of section coordinate at level 0, where a section spans 512 blocks, reaches ±137
 * million blocks — four times the world border. Y is not in the key: a surface section is a column
 * of the world, and there is only one per horizontal cell. When voxel sections arrive they will need
 * a Y, which is what the spare top bits are for.
 */
public final class SectionKey {
	private SectionKey() {}

	/**
	 * Blocks per cell at level 0 — one chunk, deliberately.
	 *
	 * <p>Making a cell exactly a chunk is what lets ingest <em>replace</em> a cell rather than
	 * accumulate into it: one chunk load produces one number for one cell. With a coarser level 0 a
	 * chunk would cover a quarter of a cell, the other three quarters would have to be remembered,
	 * and the only cheap way to combine them is a running maximum — which can never come back down
	 * when the tower in that cell is shot down.
	 */
	public static final int BASE_CELL = 16;

	/** Cells along one edge of a section, at any level. */
	public static final int SECTION_CELLS = 32;

	/** Levels the store holds — level 6 cells are 1024 blocks, past what a pixel can show. */
	public static final int MAX_LEVEL = 6;

	private static final int COORD_BITS = 29;
	private static final long COORD_MASK = (1L << COORD_BITS) - 1L;
	private static final int COORD_MIN = -(1 << (COORD_BITS - 1));
	private static final int COORD_MAX = (1 << (COORD_BITS - 1)) - 1;

	public static long of(int level, int sectionX, int sectionZ) {
		if (level < 0 || level > 63) {
			throw new IllegalArgumentException("level out of range: " + level);
		}
		if (sectionX < COORD_MIN || sectionX > COORD_MAX || sectionZ < COORD_MIN || sectionZ > COORD_MAX) {
			throw new IllegalArgumentException("section coordinate out of range: " + sectionX + "," + sectionZ);
		}
		return ((long) level << (COORD_BITS * 2))
				| ((sectionX & COORD_MASK) << COORD_BITS)
				| (sectionZ & COORD_MASK);
	}

	public static int level(long key) {
		return (int) (key >>> (COORD_BITS * 2));
	}

	public static int sectionX(long key) {
		return signExtend((int) ((key >>> COORD_BITS) & COORD_MASK));
	}

	public static int sectionZ(long key) {
		return signExtend((int) (key & COORD_MASK));
	}

	/** Blocks per cell at a level. */
	public static int cellSize(int level) {
		return BASE_CELL << level;
	}

	/** Blocks along one edge of a section at a level. */
	public static int sectionSize(int level) {
		return cellSize(level) * SECTION_CELLS;
	}

	/** Which section at this level covers a world position. */
	public static int sectionOf(int blockCoordinate, int level) {
		return Math.floorDiv(blockCoordinate, sectionSize(level));
	}

	/** Which cell inside its own section a world position falls in, 0..31. */
	public static int cellInSection(int blockCoordinate, int level) {
		return Math.floorMod(Math.floorDiv(blockCoordinate, cellSize(level)), SECTION_CELLS);
	}

	/** The section one level coarser that contains this one. */
	public static long parent(long key) {
		int level = level(key);
		if (level >= MAX_LEVEL) return key;
		return of(level + 1, sectionX(key) >> 1, sectionZ(key) >> 1);
	}

	private static int signExtend(int value) {
		return (value << (32 - COORD_BITS)) >> (32 - COORD_BITS);
	}

	public static String describe(long key) {
		return "L" + level(key) + " [" + sectionX(key) + "," + sectionZ(key) + "]";
	}
}

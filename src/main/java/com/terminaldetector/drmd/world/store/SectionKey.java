package com.terminaldetector.drmd.world.store;

/**
 * One section's address, packed into a long: level of detail plus a cell-grid coordinate.
 *
 * <p>Keys are the whole interface between what stores sections and what makes them. A backend never
 * needs to know what a section contains, only where it goes — which is what lets the same store hold
 * the surface sections drawn today and the voxel sections that will replace them.
 *
 * <pre>
 *   bits 60..63  level   (0..15)
 *   bits 40..59  x       (20-bit signed)
 *   bits 20..39  y       (20-bit signed, or COLUMN)
 *   bits  0..19  z       (20-bit signed)
 * </pre>
 *
 * <p><b>Y is in the key, and a surface section still has none.</b> Those are not in conflict, and the
 * distinction is the point. A surface section <em>is</em> a column — one value per horizontal cell,
 * because a surface has one height — so giving it a Y would be inventing a coordinate its data does
 * not have. What was wrong before was that the key could not address anything else: the source audit
 * found the same column assumption inside DRMD that it went looking for in vanilla. Now the key can
 * name a volume, and a column says so with {@link #COLUMN} rather than by the key being unable to
 * express the alternative.
 *
 * <p>20 bits an axis at level 0, where a section spans 512 blocks, reaches ±268 million blocks —
 * nearly nine times the world border, and the same in Y as in X and Z. That symmetry is the whole
 * reason for the repack; the previous 29 bits bought horizontal range this project will never use at
 * the price of a vertical axis it needs.
 *
 * <p>Nothing on disk changes: {@code FileSectionStorage} names files by decoded coordinates, not by
 * the packed value, so existing sections are still found. The packed form travels only between the
 * server and a client running the same build.
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

	private static final int COORD_BITS = 20;
	private static final long COORD_MASK = (1L << COORD_BITS) - 1L;
	private static final int MAX_LEVEL_VALUE = 15;

	/**
	 * The Y of a section that has no Y.
	 *
	 * <p>The one value the axis cannot otherwise hold — its most negative — so a column is
	 * unmistakable rather than merely unlikely. Explicit coordinates are rejected at this value for
	 * exactly that reason; it costs one section out of a million, at 268 million blocks down.
	 */
	public static final int COLUMN = -(1 << (COORD_BITS - 1));

	private static final int COORD_MIN = COLUMN + 1;
	private static final int COORD_MAX = (1 << (COORD_BITS - 1)) - 1;

	/** A surface section: a column of the world, with one value per horizontal cell. */
	public static long of(int level, int sectionX, int sectionZ) {
		return pack(level, sectionX, COLUMN, sectionZ);
	}

	/** A volume section, which has a place on all three axes. */
	public static long of(int level, int sectionX, int sectionY, int sectionZ) {
		if (sectionY < COORD_MIN || sectionY > COORD_MAX) {
			throw new IllegalArgumentException("section Y out of range: " + sectionY);
		}
		return pack(level, sectionX, sectionY, sectionZ);
	}

	private static long pack(int level, int sectionX, int sectionY, int sectionZ) {
		if (level < 0 || level > MAX_LEVEL_VALUE) {
			throw new IllegalArgumentException("level out of range: " + level);
		}
		if (sectionX < COORD_MIN || sectionX > COORD_MAX || sectionZ < COORD_MIN || sectionZ > COORD_MAX) {
			throw new IllegalArgumentException("section coordinate out of range: " + sectionX + "," + sectionZ);
		}
		return ((long) level << (COORD_BITS * 3))
				| ((sectionX & COORD_MASK) << (COORD_BITS * 2))
				| ((sectionY & COORD_MASK) << COORD_BITS)
				| (sectionZ & COORD_MASK);
	}

	public static int level(long key) {
		return (int) (key >>> (COORD_BITS * 3));
	}

	public static int sectionX(long key) {
		return signExtend((int) ((key >>> (COORD_BITS * 2)) & COORD_MASK));
	}

	/** {@link #COLUMN} for a surface section. */
	public static int sectionY(long key) {
		return signExtend((int) ((key >>> COORD_BITS) & COORD_MASK));
	}

	public static int sectionZ(long key) {
		return signExtend((int) (key & COORD_MASK));
	}

	/** Whether this addresses a column of the world rather than a box inside it. */
	public static boolean isColumn(long key) {
		return sectionY(key) == COLUMN;
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

	/**
	 * The section one level coarser that contains this one.
	 *
	 * <p>A column's parent is a column. Halving {@link #COLUMN} would produce an ordinary coordinate
	 * and quietly turn a surface section into a volume one.
	 */
	public static long parent(long key) {
		int level = level(key);
		if (level >= MAX_LEVEL) return key;
		int x = sectionX(key) >> 1;
		int z = sectionZ(key) >> 1;
		return isColumn(key) ? of(level + 1, x, z) : of(level + 1, x, sectionY(key) >> 1, z);
	}

	private static int signExtend(int value) {
		return (value << (32 - COORD_BITS)) >> (32 - COORD_BITS);
	}

	public static String describe(long key) {
		return isColumn(key)
				? "L" + level(key) + " [" + sectionX(key) + ",column," + sectionZ(key) + "]"
				: "L" + level(key) + " [" + sectionX(key) + "," + sectionY(key) + "," + sectionZ(key) + "]";
	}
}

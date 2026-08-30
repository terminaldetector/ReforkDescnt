package com.terminaldetector.drmd.client.planet;

/**
 * Plans the horizon field's concentric rings — how far each reaches and how coarse its cells are.
 * Pure arithmetic, zero Minecraft imports, same idiom as {@link SkirtGeometry}: the caller samples
 * the facts (where real chunks stop, how high the eye is above the ground under it), this file only
 * decides the grid.
 *
 * <p>Extracted from {@code PlanetSurfaceMesh.build}, which planned the rings inline and so had no
 * test of its own — the sizing rule is the single thing that decides whether the distant world reads
 * as terrain or as a handful of enormous blocks, and it was the one part of the field with nothing
 * pinning it.
 *
 * <h2>Why altitude no longer coarsens the grid</h2>
 *
 * <p>The previous rule sized a cell by the <em>slant</em> eye-distance, {@code √(horizontal² +
 * drop²)}, to hold a column's angular size constant however high the pilot climbed. That is the
 * textbook LOD rule and it does bound the cost — but it also means the ground dissolves exactly when
 * there is most of it to look at: at 2000 blocks up the ring directly below the ship was sized at
 * 287 blocks a cell, about 8° of view per column. From orbit the planet was rendered as a few dozen
 * enormous slabs.
 *
 * <p>So the drop term is gone from the size, and altitude now <em>refines</em> instead: cells get
 * finer as the eye climbs, up to {@link #REFINE_MAX}. The cost is real and bounded — cell count goes
 * as the square of the refinement, which is why the cap is modest rather than open-ended, and why
 * {@code PlanetSurfaceMesh.MAX_QUADS} had to grow alongside it.
 */
public final class HorizonGrid {
	private HorizonGrid() {}

	/**
	 * How many cells fit across a ring's inner radius — sets the angular size of one column, and so
	 * the whole field's chunkiness. Raised from the original 7 (≈8° a column, the blockiness that
	 * prompted this rework) to 10 (≈5.7°), which costs about twice the cells for the same coverage.
	 */
	public static final double CELLS_PER_RADIUS = 10.0;
	/** Each ring reaches this many times further than the one inside it. */
	public static final double RING_GROWTH = 1.8;
	/** Finest cell, so the field never dissolves into tens of thousands of tiny columns underfoot. */
	public static final int MIN_CELL = 16;
	/** Enough rings to walk from the chunk edge out to the far radius at that growth. */
	public static final int MAX_RINGS = 9;

	/**
	 * Most the grid may refine when high above the ground. 1.25 buys a visibly finer field from
	 * altitude for ~1.6× the cells; the cap exists because cell count grows as its square and the
	 * renderer re-buffers every quad each frame.
	 */
	public static final double REFINE_MAX = 1.25;
	/**
	 * Eye-height above the ground at which {@link #REFINE_MAX} is reached. Roughly the top of the sky
	 * band — the altitude by which the map is fully faded in and owns the whole view down. Kept as a
	 * literal rather than importing {@code WorldLevels} so this class stays free of the game's
	 * classpath and directly testable.
	 */
	public static final double REFINE_FULL_DROP = 1020.0;

	/** A planned field: {@code rings} valid entries, with {@code edges} one longer than {@code cells}. */
	public record Plan(double[] edges, int[] cells, int rings) {
		/** Outer radius of the whole field — where the outermost ring has to fade out. */
		public double outer() {
			return edges[rings];
		}
	}

	/**
	 * How much finer than the ground-level grid the field is drawn at an eye {@code drop} blocks
	 * above the terrain under it. Always ≥ 1: altitude never coarsens.
	 */
	public static double refineForDrop(double drop) {
		double t = Math.abs(drop) / REFINE_FULL_DROP;
		if (t > 1.0) t = 1.0;
		return 1.0 + (REFINE_MAX - 1.0) * t;
	}

	/** Cell edge length, in blocks, for a ring whose inner radius is {@code ringRadius}. */
	public static int cellSizeFor(double ringRadius, double drop) {
		double size = ringRadius / (CELLS_PER_RADIUS * refineForDrop(drop));
		return (int) Math.max(MIN_CELL, size);
	}

	/**
	 * Walk the rings outward from where real chunks stop to the far radius.
	 *
	 * @param innerRadius where the game still draws real ground; the field starts here so the
	 *                    compression's identity region lines the two up with no step at the join
	 * @param maxRadius   the outermost true distance worth sampling
	 * @param drop        eye height above the ground beneath it — refines the grid, never coarsens it
	 */
	public static Plan plan(double innerRadius, double maxRadius, double drop) {
		double[] edges = new double[MAX_RINGS + 1];
		int[] cells = new int[MAX_RINGS];
		int rings = 0;
		double ringInner = Math.max(0.0, innerRadius);
		// The walk needs a non-zero radius to size the first cell from, even at the nadir.
		double sizeFrom = Math.max(MIN_CELL * CELLS_PER_RADIUS, ringInner);
		edges[0] = ringInner;
		while (rings < MAX_RINGS && ringInner < maxRadius) {
			cells[rings] = cellSizeFor(sizeFrom, drop);
			ringInner = Math.min(sizeFrom * RING_GROWTH, maxRadius);
			edges[++rings] = ringInner;
			sizeFrom = ringInner;
		}
		return new Plan(edges, cells, rings);
	}
}

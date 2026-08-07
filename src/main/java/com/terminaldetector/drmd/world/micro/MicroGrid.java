package com.terminaldetector.drmd.world.micro;

import java.util.ArrayList;
import java.util.List;

/**
 * A block, subdivided — and the same bits, damaged.
 *
 * <p>Four cells along each edge is sixty-four cells, which is exactly the number of bits in a long.
 * That is the whole design: a partially carved block and a partially destroyed one are the same
 * value, and chiselling a corner off and blowing a hole through it are the same operation. Stage 10
 * and stage 11 stop being two systems that have to agree with each other, because there is only one.
 *
 * <p>The alternative was Chisels &amp; Bits' resolution — sixteen cells an edge, four thousand
 * ninety-six per block — which buys detail nobody flying past can see and costs a data structure per
 * block instead of a primitive. Eight bytes and no allocation is what makes it affordable to have
 * every block in the world damageable rather than a special few.
 *
 * <p>Everything here is pure arithmetic on that long: no world, no block state, no allocation except
 * where boxes are asked for. What a carved block <em>is</em> lives elsewhere; this is only what the
 * shape can do.
 */
public final class MicroGrid {
	private MicroGrid() {}

	/** Cells along one block edge. */
	public static final int EDGE = 4;

	/** Cells in a block. Also the number of bits in the mask. */
	public static final int CELLS = EDGE * EDGE * EDGE;

	/** Size of one cell as a fraction of a block. */
	public static final double CELL_SIZE = 1.0 / EDGE;

	/** Every cell present — an ordinary, undamaged block. */
	public static final long FULL = -1L;

	/** Nothing left. */
	public static final long EMPTY = 0L;

	/** How far along the ladder from whole to dust a shape has come. */
	public enum Stage {
		/** Untouched. */
		INTACT,
		/** Chipped — over three quarters standing. */
		CHIPPED,
		/** Half gone. */
		HALVED,
		/** A quarter or less: barely holding. */
		CRITICAL,
		/** Fragments. Anything still standing is rubble, not structure. */
		DEBRIS,
		/** Dust. The block is gone. */
		GONE
	}

	public static int index(int x, int y, int z) {
		return (y * EDGE + z) * EDGE + x;
	}

	public static boolean inRange(int x, int y, int z) {
		return x >= 0 && x < EDGE && y >= 0 && y < EDGE && z >= 0 && z < EDGE;
	}

	public static boolean get(long mask, int x, int y, int z) {
		if (!inRange(x, y, z)) return false;
		return (mask & (1L << index(x, y, z))) != 0L;
	}

	public static long set(long mask, int x, int y, int z) {
		if (!inRange(x, y, z)) return mask;
		return mask | (1L << index(x, y, z));
	}

	public static long clear(long mask, int x, int y, int z) {
		if (!inRange(x, y, z)) return mask;
		return mask & ~(1L << index(x, y, z));
	}

	public static int count(long mask) {
		return Long.bitCount(mask);
	}

	public static boolean isFull(long mask) {
		return mask == FULL;
	}

	public static boolean isEmpty(long mask) {
		return mask == EMPTY;
	}

	/** What fraction of the block is still there, 0..1. */
	public static double integrity(long mask) {
		return count(mask) / (double) CELLS;
	}

	public static Stage stage(long mask) {
		int n = count(mask);
		if (n == CELLS) return Stage.INTACT;
		if (n == 0) return Stage.GONE;
		// Inclusive at each rung, so exactly three quarters standing is the three-quarter stage
		// rather than the one below it — the ladder reads 100 / 75 / 50 / 25 / debris.
		double f = n / (double) CELLS;
		if (f >= 0.75) return Stage.CHIPPED;
		if (f >= 0.5) return Stage.HALVED;
		if (f >= 0.25) return Stage.CRITICAL;
		return Stage.DEBRIS;
	}

	/**
	 * The shape as one of vanilla's ten crack overlays.
	 *
	 * <p>Minecraft already draws destruction progress on a block and every resource pack already has
	 * art for it. Borrowing it means the damage ladder is visible from the first commit, with no
	 * model, no block entity and no renderer — those buy real geometry later, not the ability to see
	 * that something is breaking.
	 *
	 * @return 0..9 for a damaged block, or −1 for one that is whole and should show nothing
	 */
	public static int crackStage(long mask) {
		if (mask == FULL) return -1;
		if (mask == EMPTY) return 9;
		// Ten steps across the missing fraction, never reaching 9 while anything still stands.
		int step = (int) ((1.0 - integrity(mask)) * 10.0);
		return Math.max(0, Math.min(8, step));
	}

	/**
	 * Take a bite out of the shape.
	 *
	 * <p>Coordinates and radius are fractions of a block, so a hit anywhere on or in the block can be
	 * passed straight through without knowing the resolution. A cell goes when its centre is inside
	 * the sphere — which makes damage a crater around where it landed rather than an even thinning,
	 * and means two hits in the same place dig deeper while two hits apart leave two marks.
	 */
	public static long carve(long mask, double x, double y, double z, double radius) {
		if (radius <= 0) return mask;
		double r2 = radius * radius;
		long out = mask;
		for (int cy = 0; cy < EDGE; cy++) {
			for (int cz = 0; cz < EDGE; cz++) {
				for (int cx = 0; cx < EDGE; cx++) {
					double dx = (cx + 0.5) * CELL_SIZE - x;
					double dy = (cy + 0.5) * CELL_SIZE - y;
					double dz = (cz + 0.5) * CELL_SIZE - z;
					if (dx * dx + dy * dy + dz * dz <= r2) {
						out &= ~(1L << index(cx, cy, cz));
					}
				}
			}
		}
		return out;
	}

	/** The opposite: fill the cells a sphere covers. Placement, repair, growth. */
	public static long fill(long mask, double x, double y, double z, double radius) {
		if (radius <= 0) return mask;
		double r2 = radius * radius;
		long out = mask;
		for (int cy = 0; cy < EDGE; cy++) {
			for (int cz = 0; cz < EDGE; cz++) {
				for (int cx = 0; cx < EDGE; cx++) {
					double dx = (cx + 0.5) * CELL_SIZE - x;
					double dy = (cy + 0.5) * CELL_SIZE - y;
					double dz = (cz + 0.5) * CELL_SIZE - z;
					if (dx * dx + dy * dy + dz * dz <= r2) {
						out |= 1L << index(cx, cy, cz);
					}
				}
			}
		}
		return out;
	}

	/** A test against one cell's grid coordinates, each 0..EDGE-1. */
	@FunctionalInterface
	public interface CellTest {
		boolean inside(int x, int y, int z);
	}

	/**
	 * Build a mask cell by cell from an arbitrary test, rather than the sphere {@link #carve}/
	 * {@link #fill} already cover.
	 *
	 * <p>What this buys over calling {@code fill} with a huge radius and then clearing the cells a
	 * shape does not want: {@code fill}/{@code carve} both describe round regions in block-local
	 * <em>units</em> — one block's own 0..1 cube. A shape that spans many blocks the same way a shot
	 * or an explosion does not — a cylinder's axis, a tunnel's boundary — needs each block's cells
	 * tested against a condition defined in <em>world</em> space, which only the caller knows how to
	 * state. This is that hook: {@code test} decides per cell, in this block's own 0..EDGE-1 grid
	 * coordinates, and owns whatever world-space math got it there.
	 */
	public static long build(CellTest test) {
		long out = EMPTY;
		for (int cy = 0; cy < EDGE; cy++) {
			for (int cz = 0; cz < EDGE; cz++) {
				for (int cx = 0; cx < EDGE; cx++) {
					if (test.inside(cx, cy, cz)) out |= 1L << index(cx, cy, cz);
				}
			}
		}
		return out;
	}

	/** Which cell a block-local position falls in. Values outside 0..1 clamp to the nearest cell. */
	public static int cellOf(double blockLocal) {
		int c = (int) Math.floor(blockLocal * EDGE);
		return Math.max(0, Math.min(EDGE - 1, c));
	}

	/** A box in block-local units, ready to become a collision shape. */
	public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		public double volume() {
			return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
		}
	}

	/**
	 * The shape as boxes, merged.
	 *
	 * <p>Sixty-four separate cubes would be a correct collision shape and a ruinous one — every
	 * entity test walks the list. Runs are merged along X and then rows merged along Z within a
	 * layer, which is cheap to compute and turns the common cases (an intact block, a block with one
	 * corner missing, a slab) into one or two boxes.
	 */
	public static List<Box> boxes(long mask) {
		List<Box> out = new ArrayList<>();
		if (mask == EMPTY) return out;
		if (mask == FULL) {
			out.add(new Box(0, 0, 0, 1, 1, 1));
			return out;
		}
		for (int y = 0; y < EDGE; y++) {
			// Rows of X-runs in this layer, then merge identical rows along Z.
			int z = 0;
			while (z < EDGE) {
				int x = 0;
				while (x < EDGE) {
					if (!get(mask, x, y, z)) {
						x++;
						continue;
					}
					int runEnd = x;
					while (runEnd + 1 < EDGE && get(mask, runEnd + 1, y, z)) runEnd++;
					// How many following rows have exactly this run.
					int depth = 1;
					while (z + depth < EDGE && rowMatches(mask, y, z + depth, x, runEnd)) depth++;
					out.add(new Box(
							x * CELL_SIZE, y * CELL_SIZE, z * CELL_SIZE,
							(runEnd + 1) * CELL_SIZE, (y + 1) * CELL_SIZE, (z + depth) * CELL_SIZE));
					// Consume the merged rows so they are not emitted again.
					mask = clearRun(mask, y, z, depth, x, runEnd);
					x = runEnd + 1;
				}
				z++;
			}
		}
		return out;
	}

	private static boolean rowMatches(long mask, int y, int z, int fromX, int toX) {
		for (int x = 0; x < EDGE; x++) {
			boolean wanted = x >= fromX && x <= toX;
			if (get(mask, x, y, z) != wanted) return false;
		}
		return true;
	}

	private static long clearRun(long mask, int y, int z, int depth, int fromX, int toX) {
		for (int dz = 0; dz < depth; dz++) {
			for (int x = fromX; x <= toX; x++) {
				mask = clear(mask, x, y, z + dz);
			}
		}
		return mask;
	}
}

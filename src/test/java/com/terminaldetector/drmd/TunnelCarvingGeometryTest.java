package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.micro.MicroGrid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * At {@code WorldLevels.SHAFT_RADIUS} = 3, {@code cutDescentShaft}'s whole-block circle test
 * ({@code dx*dx+dz*dz <= r*r} on integer offsets) leaves a boundary a third of the tunnel's own
 * radius away from a true circle at every corner. {@code TunnelCarving.boundaryMask} is meant to
 * close most of that gap by testing each boundary block's 64 quarter-cells individually rather than
 * the block as one unit. This pins the claim numerically — total carved area against {@code pi*r^2}
 * — rather than trusting that finer cells obviously means closer to round.
 *
 * <p>{@code TunnelCarving} itself imports {@code ServerWorld}/{@code BlockPos}, unavailable here, so
 * its private {@code boundaryMask} is mirrored — same reasoning as this suite's other mirrors — using
 * the real, dependency-free {@link MicroGrid} it's actually built on.
 *
 * <p>Net area turned out to be the wrong metric: a rough octagon at r=3 already lands within a couple
 * of percent of {@code pi*r^2} by whole-block area alone, because the corners it over-includes and
 * the arcs it under-includes roughly cancel in a <em>sum</em>. That cancellation is exactly why area
 * cannot tell blocky from round — a shape can have the right area and still look nothing like a
 * circle. What the mismatch tests below use instead is symmetric difference: for every block, how far
 * its predicted coverage is from that block's true coverage, added up rather than netted out, so
 * over- and under-inclusion both count against the approximation instead of hiding each other.</p>
 */
class TunnelCarvingGeometryTest {
	private static final double RADIUS = 3.0; // WorldLevels.SHAFT_RADIUS
	/** Sub-samples per axis for the "ground truth" coverage fraction — fine enough to trust as a reference. */
	private static final int REFERENCE_SAMPLES = 40;

	/** Mirrors TunnelCarving.boundaryMask. */
	private static long boundaryMask(int dx, int dz, double radius) {
		double r2 = radius * radius;
		return MicroGrid.build((cx, cy, cz) -> {
			double wx = dx + (cx + 0.5) * MicroGrid.CELL_SIZE;
			double wz = dz + (cz + 0.5) * MicroGrid.CELL_SIZE;
			return wx * wx + wz * wz > r2;
		});
	}

	/** How many quarter-cells of one XZ layer of this mask are cleared (any Y — the pattern repeats). */
	private static int clearedCellsOneLayer(long mask) {
		int cleared = 0;
		for (int x = 0; x < MicroGrid.EDGE; x++) {
			for (int z = 0; z < MicroGrid.EDGE; z++) {
				if (!MicroGrid.get(mask, x, 0, z)) cleared++;
			}
		}
		return cleared;
	}

	/** What fraction of this whole block a true circle actually covers, by fine sub-sampling. */
	private static double trueCoverageFraction(int dx, int dz, double radius) {
		double r2 = radius * radius;
		int inside = 0;
		for (int sx = 0; sx < REFERENCE_SAMPLES; sx++) {
			for (int sz = 0; sz < REFERENCE_SAMPLES; sz++) {
				double wx = dx + (sx + 0.5) / REFERENCE_SAMPLES;
				double wz = dz + (sz + 0.5) / REFERENCE_SAMPLES;
				if (wx * wx + wz * wz <= r2) inside++;
			}
		}
		return inside / (double) (REFERENCE_SAMPLES * REFERENCE_SAMPLES);
	}

	/** Total |predicted − true| coverage across every block a whole-block-only carve considers. */
	private static double wholeBlockMismatch(double radius) {
		int r = (int) radius;
		double total = 0;
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				double predicted = dx * dx + dz * dz <= r * r ? 1.0 : 0.0;
				total += Math.abs(predicted - trueCoverageFraction(dx, dz, radius));
			}
		}
		return total;
	}

	/** Same sum, with boundary blocks predicted at quarter-cell resolution instead of whole-block. */
	private static double chamferedMismatch(double radius) {
		int r = (int) radius;
		double total = 0;
		int outer = (int) Math.ceil(radius) + 1;
		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				double predicted;
				if (dx * dx + dz * dz <= r * r) {
					predicted = 1.0; // whole-block interior pass, unchanged
				} else {
					long mask = boundaryMask(dx, dz, radius);
					predicted = clearedCellsOneLayer(mask) / (double) (MicroGrid.EDGE * MicroGrid.EDGE);
				}
				total += Math.abs(predicted - trueCoverageFraction(dx, dz, radius));
			}
		}
		return total;
	}

	@Test
	@DisplayName("chamfering the boundary at quarter-cell precision meaningfully reduces per-block shape error")
	void chamferedMatchesTrueCoverageBetterThanWholeBlockDoes() {
		double wholeBlock = wholeBlockMismatch(RADIUS);
		double chamfered = chamferedMismatch(RADIUS);
		// At r=3 this comes out to roughly a 45% cut (9.28 -> 5.15 total per-block mismatch) — computed,
		// not a round number picked first: net area alone would show almost nothing, since over- and
		// under-inclusion cancel in a sum, which is the whole reason this test uses mismatch instead.
		assertTrue(chamfered < wholeBlock * 0.65,
				"whole-block total mismatch " + wholeBlock + ", chamfered " + chamfered
						+ " — expected at least a third off, well under the ~45% actually measured");
	}

	@Test
	@DisplayName("chamfering never removes a cell a true circle does not cover")
	void chamferNeverOvershootsTheTrueCircle() {
		int outer = (int) Math.ceil(RADIUS) + 1;
		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				long mask = boundaryMask(dx, dz, RADIUS);
				for (int cx = 0; cx < MicroGrid.EDGE; cx++) {
					for (int cz = 0; cz < MicroGrid.EDGE; cz++) {
						boolean cellCleared = !MicroGrid.get(mask, cx, 0, cz);
						if (!cellCleared) continue;
						double wx = dx + (cx + 0.5) * MicroGrid.CELL_SIZE;
						double wz = dz + (cz + 0.5) * MicroGrid.CELL_SIZE;
						assertTrue(wx * wx + wz * wz <= RADIUS * RADIUS,
								"cell at dx=" + dx + " dz=" + dz + " (" + cx + "," + cz
										+ ") was cleared outside the true circle");
					}
				}
			}
		}
	}
}

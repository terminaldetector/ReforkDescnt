package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.micro.MicroGrid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MicroGrid} has no Minecraft dependency at all — pure arithmetic on a {@code long}, by its
 * own design — so unlike most of this suite, this compiles and tests the real class directly rather
 * than mirroring its formulas.
 *
 * <p>{@code build} is the hook {@code TunnelCarving} (and anything else that wants a mask shaped by
 * something other than one block's own sphere, like a cylinder spanning many blocks) needs: a cell
 * test in the caller's own world-space math, not a shape {@code carve}/{@code fill} already know.
 */
class MicroGridBuildTest {
	@Test
	@DisplayName("an always-true test builds FULL, an always-false test builds EMPTY")
	void trivialTestsMatchTheConstants() {
		assertEquals(MicroGrid.FULL, MicroGrid.build((x, y, z) -> true));
		assertEquals(MicroGrid.EMPTY, MicroGrid.build((x, y, z) -> false));
	}

	@Test
	@DisplayName("build reproduces carve's own sphere test when given the identical predicate")
	void matchesCarveForAnEquivalentSpherePredicate() {
		double cx = 0.5, cy = 0.5, cz = 0.5, r = 0.4;
		long viaCarve = MicroGrid.carve(MicroGrid.FULL, cx, cy, cz, r);
		double r2 = r * r;
		long viaBuild = MicroGrid.build((x, y, z) -> {
			double dx = (x + 0.5) * MicroGrid.CELL_SIZE - cx;
			double dy = (y + 0.5) * MicroGrid.CELL_SIZE - cy;
			double dz = (z + 0.5) * MicroGrid.CELL_SIZE - cz;
			return dx * dx + dy * dy + dz * dz > r2; // build keeps cells the test returns true for
		});
		assertEquals(viaCarve, viaBuild);
	}

	@Test
	@DisplayName("build honors per-axis-only tests, e.g. a Y-invariant column mask")
	void supportsShapesNarrowerThanASphere() {
		// Only the x==0 column of cells, every y and z — not expressible as a single carve/fill call.
		long mask = MicroGrid.build((x, y, z) -> x == 0);
		assertEquals(MicroGrid.EDGE * MicroGrid.EDGE, MicroGrid.count(mask),
				"one quarter-slice of the 4x4x4 grid should be exactly EDGE*EDGE cells");
		for (int y = 0; y < MicroGrid.EDGE; y++) {
			for (int z = 0; z < MicroGrid.EDGE; z++) {
				assertTrue(MicroGrid.get(mask, 0, y, z));
				assertTrue(!MicroGrid.get(mask, 1, y, z));
			}
		}
	}
}

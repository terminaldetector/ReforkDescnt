package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.planet.SkirtGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Calls {@link SkirtGeometry} directly — zero imports, same idiom as {@code StructureFaceCullerTest}. */
class SkirtGeometryTest {
	@Test
	@DisplayName("a strictly higher cell draws a skirt toward its lower neighbor")
	void higherCellDrawsSkirt() {
		assertTrue(SkirtGeometry.drawsSkirt(10.0, 4.0));
	}

	@Test
	@DisplayName("a strictly lower cell does not draw toward its higher neighbor")
	void lowerCellDrawsNothing() {
		assertFalse(SkirtGeometry.drawsSkirt(4.0, 10.0));
	}

	@Test
	@DisplayName("equal heights draw nothing on either side — flat ground needs no wall")
	void equalHeightsDrawNothing() {
		assertFalse(SkirtGeometry.drawsSkirt(7.0, 7.0));
		assertFalse(SkirtGeometry.drawsSkirt(7.0, 7.0));
	}

	@Test
	@DisplayName("skirtBottom stops at the neighbor's height when that's above the floor")
	void skirtBottomUsesNeighborHeight() {
		assertEquals(4.0, SkirtGeometry.skirtBottom(4.0, -6.0));
	}

	@Test
	@DisplayName("skirtBottom clamps to the floor when the neighbor dips below it")
	void skirtBottomClampsToFloor() {
		assertEquals(-6.0, SkirtGeometry.skirtBottom(-40.0, -6.0));
	}

	/**
	 * The regression case that would have caught the original bug: a single low "pit" cell surrounded
	 * on all 4 sides by higher neighbors. A 2-direction check (only +X, +Z) can only ever see this pit
	 * from 2 of its 4 sides — the other 2 higher neighbors, when they are the ones being evaluated,
	 * would need to look at their own -X/-Z side to find it, which the old code never did. Checking all
	 * 4 directions means every one of the 4 higher neighbors independently reports the shared edge from
	 * its own side, regardless of which cell the mesh builder happens to visit first.
	 */
	@Test
	@DisplayName("all 4 higher neighbors of a low pit independently draw toward it")
	void pitIsClosedFromAllFourSides() {
		double pitTop = 0.0;
		double[] higherNeighbors = {10.0, 12.0, 8.0, 9.0}; // +X, -X, +Z, -Z
		for (double neighborTop : higherNeighbors) {
			assertTrue(SkirtGeometry.drawsSkirt(neighborTop, pitTop),
					"neighbor at " + neighborTop + " must draw down toward the pit from its own side");
		}
	}
}

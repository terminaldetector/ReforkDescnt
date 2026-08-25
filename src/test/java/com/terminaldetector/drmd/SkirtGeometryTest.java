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
}

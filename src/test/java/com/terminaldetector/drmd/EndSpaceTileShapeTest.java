package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.end.space.EndSpaceTileShape;
import com.terminaldetector.drmd.world.end.space.EndSpaceTileShape.Cell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Exercises {@link EndSpaceTileShape} directly — pure geometry, mirrors the {@code CitadelDeckShapeTest} idiom. */
class EndSpaceTileShapeTest {
	private static final int HALF = EndSpaceTileShape.HALF_EXTENT;

	@Test
	@DisplayName("every platform level has an open centre, a ring, and corner beacons")
	void everyPlatformLevelHasAllThreeZones() {
		for (int p = 0; p < EndSpaceTileShape.PLATFORM_COUNT; p++) {
			int y = p * EndSpaceTileShape.PLATFORM_SPACING;
			assertEquals(Cell.NONE, EndSpaceTileShape.classify(0, y, 0), "p=" + p + ": centre should be open");
			assertEquals(Cell.PLATFORM, EndSpaceTileShape.classify(HALF, y, 0), "p=" + p + ": mid-ring should be deck");
			assertEquals(Cell.BEACON, EndSpaceTileShape.classify(HALF, y, HALF), "p=" + p + ": corner should be a beacon");
		}
	}

	@Test
	@DisplayName("the gap between platforms is open air — that's the entire point of this tile")
	void betweenPlatformsIsOpenAir() {
		int mid = EndSpaceTileShape.PLATFORM_SPACING / 2;
		assertEquals(Cell.NONE, EndSpaceTileShape.classify(HALF, mid, 0),
				"halfway between two platforms, even on the ring footprint, should be open");
		assertEquals(Cell.NONE, EndSpaceTileShape.classify(HALF, 1, 0),
				"one block above a platform should already be open");
	}

	@Test
	@DisplayName("outside the footprint, below the stack, or above the top platform is NONE")
	void outsideTheTileIsNone() {
		int topPlatformY = (EndSpaceTileShape.PLATFORM_COUNT - 1) * EndSpaceTileShape.PLATFORM_SPACING;
		assertEquals(topPlatformY, EndSpaceTileShape.TILE_HEIGHT - 1,
				"TILE_HEIGHT should be exactly one past the top platform's own Y");
		assertEquals(Cell.PLATFORM, EndSpaceTileShape.classify(HALF, topPlatformY, 0), "top platform should still exist");
		assertEquals(Cell.NONE, EndSpaceTileShape.classify(HALF, EndSpaceTileShape.TILE_HEIGHT, 0),
				"one block above the top platform should be NONE");
		assertEquals(Cell.NONE, EndSpaceTileShape.classify(HALF, -1, 0), "below the stack should be NONE");
		assertEquals(Cell.NONE, EndSpaceTileShape.classify(HALF + 1, 0, 0), "past the horizontal footprint should be NONE");
	}

	@Test
	@DisplayName("the ring is hollow — strictly inside RING_INNER is open even at a platform's own Y")
	void ringInteriorIsHollow() {
		assertEquals(Cell.NONE, EndSpaceTileShape.classify(EndSpaceTileShape.RING_INNER - 1, 0, 0),
				"just inside the ring's inner radius should be open");
		assertNotEquals(Cell.NONE, EndSpaceTileShape.classify(EndSpaceTileShape.RING_INNER, 0, 0),
				"at/past the inner radius should already be deck");
	}

	@Test
	@DisplayName("classify is deterministic")
	void isDeterministic() {
		assertEquals(EndSpaceTileShape.classify(HALF, 96, -3), EndSpaceTileShape.classify(HALF, 96, -3));
	}
}

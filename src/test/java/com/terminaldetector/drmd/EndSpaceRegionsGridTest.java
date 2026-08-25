package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code EndSpaceRegions}' grid math without compiling it directly — like
 * {@code InfiniteMegacityGridTest}, {@code anchorForCell} returns {@code BlockPos}, a real Minecraft
 * type unavailable to a plain JUnit run, so the {@code cellOf}/{@code anchorForCell}/
 * {@code isBeyondInnerRadius} formulas are mirrored here as plain arithmetic instead.
 */
class EndSpaceRegionsGridTest {
	/** Mirrors EndSpaceRegions.PITCH. */
	private static final int PITCH = 64;
	/** Mirrors EndSpaceRegions.INNER_RADIUS_CELLS. */
	private static final int INNER_RADIUS_CELLS = 3;
	/** Mirrors EndSpaceTileShape.HALF_EXTENT — the widest thing PITCH has to clear. */
	private static final int TILE_HALF_EXTENT = 12;

	private static int cellOf(int block) {
		return Math.floorDiv(block, PITCH);
	}

	private static int anchorForCell(int cell) {
		return cell * PITCH + PITCH / 2;
	}

	private static boolean isBeyondInnerRadius(int cellX, int cellZ) {
		long dx = cellX, dz = cellZ;
		return dx * dx + dz * dz >= (long) INNER_RADIUS_CELLS * INNER_RADIUS_CELLS;
	}

	/** Mirrors EndSpaceRegions.nearestTileAnchor's cell math (pre-anchorForCell). */
	private static int[] nearestTileCell(int worldX, int worldZ) {
		int cellX = cellOf(worldX);
		int cellZ = cellOf(worldZ);
		if (isBeyondInnerRadius(cellX, cellZ)) return new int[] {cellX, cellZ};

		int pushed = INNER_RADIUS_CELLS + 1;
		boolean leanX = Math.abs(cellX) >= Math.abs(cellZ);
		int pushedX = leanX ? (cellX < 0 ? -pushed : pushed) : 0;
		int pushedZ = leanX ? 0 : (cellZ < 0 ? -pushed : pushed);
		return new int[] {pushedX, pushedZ};
	}

	@Test
	@DisplayName("PITCH clears the tile's own footprint with room to spare, both axes")
	void pitchClearsTheTileFootprint() {
		assertTrue(PITCH / 2 > TILE_HALF_EXTENT,
				"half the pitch (" + PITCH / 2 + ") must exceed the tile half-extent (" + TILE_HALF_EXTENT
						+ ") or two adjacent tiles can draw into each other");
	}

	@Test
	@DisplayName("every block position maps to exactly one cell, and that cell's anchor is its centre")
	void cellAssignmentIsConsistentAndCentred() {
		for (int block = -PITCH * 5; block <= PITCH * 5; block += 7) {
			int cell = cellOf(block);
			int anchor = anchorForCell(cell);
			int offsetFromAnchor = block - anchor;
			assertTrue(Math.abs(offsetFromAnchor) <= PITCH / 2,
					"block " + block + " in cell " + cell + " is " + offsetFromAnchor
							+ " from that cell's own anchor — should never exceed half the pitch");
		}
	}

	@Test
	@DisplayName("adjacent cells' anchors are exactly one pitch apart — no gaps, no overlaps in the grid itself")
	void adjacentAnchorsAreExactlyOnePitchApart() {
		for (int cell = -20; cell <= 20; cell++) {
			assertEquals(PITCH, anchorForCell(cell + 1) - anchorForCell(cell));
		}
	}

	@Test
	@DisplayName("the origin cell and its immediate neighbours are inside the inner radius, not tiled")
	void originIsInsideTheInnerRadius() {
		assertFalse(isBeyondInnerRadius(0, 0), "the origin cell itself must be excluded");
		assertFalse(isBeyondInnerRadius(1, 1), "diagonally adjacent to the origin should still be excluded");
		assertFalse(isBeyondInnerRadius(INNER_RADIUS_CELLS - 1, 0), "just inside the radius should be excluded");
	}

	@Test
	@DisplayName("cells at/past the inner radius on an axis are tiled")
	void axisCellsAtTheRadiusAreTiled() {
		assertTrue(isBeyondInnerRadius(INNER_RADIUS_CELLS, 0), "exactly at the radius on the X axis should be tiled");
		assertTrue(isBeyondInnerRadius(0, INNER_RADIUS_CELLS), "exactly at the radius on the Z axis should be tiled");
		assertTrue(isBeyondInnerRadius(-INNER_RADIUS_CELLS, 0), "the radius applies symmetrically in -X too");
	}

	@Test
	@DisplayName("a point already past the inner radius keeps its own cell as its nearest tile")
	void pointBeyondRadiusKeepsItsOwnCell() {
		assertArrayEquals(new int[] {10, 0}, nearestTileCell(anchorForCell(10), anchorForCell(0)));
		assertArrayEquals(new int[] {-5, 5}, nearestTileCell(anchorForCell(-5), anchorForCell(5)));
	}

	@Test
	@DisplayName("the origin — the real End's arena position — pushes out to a deterministic tiled cell")
	void originPushesOutToATiledCell() {
		int[] cell = nearestTileCell(0, 0);
		assertTrue(isBeyondInnerRadius(cell[0], cell[1]), "the pushed cell must actually be tiled");
		assertEquals(INNER_RADIUS_CELLS + 1, cell[0], "origin has no lean; pushes +X by this class's own tie-break");
		assertEquals(0, cell[1]);
	}

	@Test
	@DisplayName("nearestTileCell always lands on a tiled cell, for every point inside the excluded zone")
	void nearestTileCellNeverUndershootsTheRadius() {
		int span = (INNER_RADIUS_CELLS + 2) * PITCH;
		for (int worldX = -span; worldX <= span; worldX += 17) {
			for (int worldZ = -span; worldZ <= span; worldZ += 17) {
				int[] cell = nearestTileCell(worldX, worldZ);
				assertTrue(isBeyondInnerRadius(cell[0], cell[1]),
						"(" + worldX + "," + worldZ + ") -> cell (" + cell[0] + "," + cell[1] + ") should be tiled");
			}
		}
	}

	@Test
	@DisplayName("a point leaning off-axis pushes along whichever single axis it already favours")
	void leaningPointPushesAlongItsDominantAxis() {
		assertArrayEquals(new int[] {INNER_RADIUS_CELLS + 1, 0}, nearestTileCell(anchorForCell(1), anchorForCell(0)));
		assertArrayEquals(new int[] {0, INNER_RADIUS_CELLS + 1}, nearestTileCell(anchorForCell(0), anchorForCell(1)));
		assertArrayEquals(new int[] {-(INNER_RADIUS_CELLS + 1), 0}, nearestTileCell(anchorForCell(-2), anchorForCell(1)));
	}
}

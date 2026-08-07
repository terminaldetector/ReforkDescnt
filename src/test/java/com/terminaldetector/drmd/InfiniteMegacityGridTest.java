package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the one number {@code InfiniteMegacityRegions.PITCH} actually depends on: how far off-centre
 * {@code MegacityGenerator} ever draws a block. Packing plates edge to edge only works if no plate's
 * geometry reaches into its neighbour's half of the cell — this derives that reach from
 * {@code MegacityGenerator}'s own constants (mirrored below, since importing the real class would
 * drag in the whole {@code WorldAccess}/{@code ServerWorld} surface for five ints) and checks the
 * grid math against it, rather than trusting the 160/76 numbers already written in both files to
 * still agree after some future change to one of them.
 *
 * <p>{@code InfiniteMegacityRegions} itself returns {@code BlockPos}, a real Minecraft type not
 * available to a plain {@code javac}/JUnit run outside the game, so its {@code cellOf}/
 * {@code anchorForCell} formulas are mirrored here as plain {@code int} arithmetic instead of
 * compiling the class directly — same reasoning as every other pure-formula mirror in this suite.
 */
class InfiniteMegacityGridTest {
	/** Mirrors InfiniteMegacityRegions.PITCH. */
	private static final int PITCH = 160;

	// Mirrors MegacityGenerator's own private layout constants.
	private static final int GRID = 5;
	private static final int BLOCK_SIZE = 16;
	private static final int STREET = 8;
	private static final int SPAN = GRID * BLOCK_SIZE + (GRID - 1) * STREET;
	private static final int HALF = SPAN / 2;
	/** plateRim's outer edge, mirrored: half + 10. */
	private static final int RIM_REACH = HALF + 10;
	/** artifactHangar's south edge, mirrored: half + 4 (door) + 16 (hangar depth). */
	private static final int HANGAR_REACH = HALF + 4 + 16;

	private static int cellOf(int block) {
		return Math.floorDiv(block, PITCH);
	}

	private static int anchorForCell(int cell) {
		return cell * PITCH + PITCH / 2;
	}

	@Test
	@DisplayName("the widest thing MegacityGenerator draws off-centre is the hangar, at half+20")
	void hangarIsTheDominantReach() {
		assertEquals(76, HANGAR_REACH, "SPAN/GRID/BLOCK_SIZE/STREET drifted — re-derive PITCH's margin");
		assertTrue(HANGAR_REACH > RIM_REACH, "if this ever flips, the rim becomes the constant to pack against");
	}

	@Test
	@DisplayName("PITCH keeps every plate's full reach inside its own half of the cell, both axes")
	void pitchClearsTheWorstReachSymmetrically() {
		assertTrue(PITCH / 2 > HANGAR_REACH,
				"half the pitch (" + PITCH / 2 + ") must exceed the worst single-direction reach ("
						+ HANGAR_REACH + ") or two adjacent plates can draw into each other");
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
}

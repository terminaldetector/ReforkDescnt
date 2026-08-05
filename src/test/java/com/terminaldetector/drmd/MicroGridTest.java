package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.micro.MicroGrid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The microblock grid, which is arithmetic on a long and therefore provable without a game running.
 *
 * <p>The collision invariants are the ones worth having: a shape that does not cover exactly the
 * cells that are set, or that overlaps itself, is a shape entities fall through or catch on, and
 * neither failure is obvious from looking at it.
 */
class MicroGridTest {

	@Test
	@DisplayName("every cell has its own bit")
	void indicesAreDistinct() {
		boolean[] seen = new boolean[MicroGrid.CELLS];
		for (int x = 0; x < MicroGrid.EDGE; x++) {
			for (int y = 0; y < MicroGrid.EDGE; y++) {
				for (int z = 0; z < MicroGrid.EDGE; z++) {
					int i = MicroGrid.index(x, y, z);
					assertTrue(i >= 0 && i < MicroGrid.CELLS, "index out of range: " + i);
					assertFalse(seen[i], "two cells share bit " + i);
					seen[i] = true;
				}
			}
		}
	}

	@Test
	@DisplayName("set and clear reach the cell they name and no other")
	void setAndClearAreLocal() {
		for (int x = 0; x < MicroGrid.EDGE; x++) {
			for (int y = 0; y < MicroGrid.EDGE; y++) {
				for (int z = 0; z < MicroGrid.EDGE; z++) {
					long one = MicroGrid.set(MicroGrid.EMPTY, x, y, z);
					assertEquals(1, MicroGrid.count(one));
					assertTrue(MicroGrid.get(one, x, y, z));
					long gone = MicroGrid.clear(MicroGrid.FULL, x, y, z);
					assertEquals(MicroGrid.CELLS - 1, MicroGrid.count(gone));
					assertFalse(MicroGrid.get(gone, x, y, z));
				}
			}
		}
	}

	@Test
	@DisplayName("out-of-range cells are ignored, not wrapped into a neighbour")
	void outOfRangeIsIgnored() {
		assertEquals(MicroGrid.EMPTY, MicroGrid.set(MicroGrid.EMPTY, 4, 0, 0));
		assertEquals(MicroGrid.EMPTY, MicroGrid.set(MicroGrid.EMPTY, -1, 0, 0));
		assertEquals(MicroGrid.FULL, MicroGrid.clear(MicroGrid.FULL, 0, 9, 0));
		assertFalse(MicroGrid.get(MicroGrid.FULL, 0, 0, -1));
	}

	@Test
	@DisplayName("the damage ladder reads 100 / 75 / 50 / 25 / debris / dust")
	void stageLadder() {
		assertEquals(MicroGrid.Stage.INTACT, MicroGrid.stage(MicroGrid.FULL));
		assertEquals(MicroGrid.Stage.GONE, MicroGrid.stage(MicroGrid.EMPTY));
		assertEquals(MicroGrid.Stage.CHIPPED, MicroGrid.stage(maskOf(63)));
		assertEquals(MicroGrid.Stage.CHIPPED, MicroGrid.stage(maskOf(48)), "exactly three quarters");
		assertEquals(MicroGrid.Stage.HALVED, MicroGrid.stage(maskOf(47)));
		assertEquals(MicroGrid.Stage.HALVED, MicroGrid.stage(maskOf(32)), "exactly half");
		assertEquals(MicroGrid.Stage.CRITICAL, MicroGrid.stage(maskOf(31)));
		assertEquals(MicroGrid.Stage.CRITICAL, MicroGrid.stage(maskOf(16)), "exactly a quarter");
		assertEquals(MicroGrid.Stage.DEBRIS, MicroGrid.stage(maskOf(15)));
		assertEquals(MicroGrid.Stage.DEBRIS, MicroGrid.stage(maskOf(1)));

		assertEquals(1.0, MicroGrid.integrity(MicroGrid.FULL), 1e-9);
		assertEquals(0.5, MicroGrid.integrity(maskOf(32)), 1e-9);
		assertEquals(0.0, MicroGrid.integrity(MicroGrid.EMPTY), 1e-9);
	}

	@Test
	@DisplayName("crack overlay tracks the damage, and a whole block shows none")
	void crackOverlayTracksDamage() {
		assertEquals(-1, MicroGrid.crackStage(MicroGrid.FULL), "an untouched block shows nothing");
		assertEquals(9, MicroGrid.crackStage(MicroGrid.EMPTY));
		assertTrue(MicroGrid.crackStage(maskOf(63)) >= 0, "one chipped cell already shows");
		assertTrue(MicroGrid.crackStage(maskOf(1)) <= 8, "something still standing is not stage 9");
		int previous = -1;
		for (int n = 64; n >= 1; n--) {
			int stage = MicroGrid.crackStage(maskOf(n));
			assertTrue(stage >= previous, "cracks must not heal as cells are lost");
			assertTrue(stage >= -1 && stage <= 8, "stage " + stage + " out of range at " + n);
			previous = stage;
		}
	}

	@Test
	@DisplayName("a hit craters where it landed and leaves the far side standing")
	void damageIsLocal() {
		// Hit the lower-x corner.
		long hit = MicroGrid.carve(MicroGrid.FULL, 0.0, 0.0, 0.0, 0.4);
		assertFalse(MicroGrid.get(hit, 0, 0, 0), "the cell at the impact should go");
		assertTrue(MicroGrid.get(hit, 3, 3, 3), "the opposite corner should not");
		assertTrue(MicroGrid.count(hit) < MicroGrid.CELLS && MicroGrid.count(hit) > 0);

		// Same place again digs deeper; somewhere else leaves a second mark.
		long deeper = MicroGrid.carve(hit, 0.0, 0.0, 0.0, 0.6);
		assertTrue(MicroGrid.count(deeper) < MicroGrid.count(hit));
		long second = MicroGrid.carve(hit, 1.0, 1.0, 1.0, 0.4);
		assertFalse(MicroGrid.get(second, 3, 3, 3), "the second hit should land too");
		assertFalse(MicroGrid.get(second, 0, 0, 0), "and the first mark should remain");
	}

	@Test
	@DisplayName("a big enough hit takes the whole block")
	void enoughDamageIsDust() {
		long gone = MicroGrid.carve(MicroGrid.FULL, 0.5, 0.5, 0.5, 2.0);
		assertEquals(MicroGrid.EMPTY, gone);
		assertEquals(MicroGrid.Stage.GONE, MicroGrid.stage(gone));
	}

	@Test
	@DisplayName("filling is carving's inverse over the same sphere")
	void fillUndoesCarve() {
		long carved = MicroGrid.carve(MicroGrid.FULL, 0.3, 0.7, 0.2, 0.35);
		assertTrue(MicroGrid.count(carved) < MicroGrid.CELLS);
		long repaired = MicroGrid.fill(carved, 0.3, 0.7, 0.2, 0.35);
		assertEquals(MicroGrid.FULL, repaired);
	}

	@Test
	@DisplayName("a whole block is one box, and nothing is none")
	void trivialShapes() {
		assertEquals(1, MicroGrid.boxes(MicroGrid.FULL).size());
		assertEquals(1.0, MicroGrid.boxes(MicroGrid.FULL).get(0).volume(), 1e-9);
		assertTrue(MicroGrid.boxes(MicroGrid.EMPTY).isEmpty());
	}

	@Test
	@DisplayName("a slab collapses to a single box")
	void slabMerges() {
		long slab = MicroGrid.EMPTY;
		for (int x = 0; x < MicroGrid.EDGE; x++) {
			for (int z = 0; z < MicroGrid.EDGE; z++) {
				slab = MicroGrid.set(slab, x, 0, z);
			}
		}
		List<MicroGrid.Box> boxes = MicroGrid.boxes(slab);
		assertEquals(1, boxes.size(), "16 cells in one layer should merge into one box");
		assertEquals(0.25, boxes.get(0).volume(), 1e-9);
	}

	/**
	 * The invariant that matters for collision: the boxes cover exactly the cells that are set. Too
	 * little and entities fall through the shape; too much and they catch on air.
	 */
	@Test
	@DisplayName("boxes cover exactly the cells that are set, and never each other")
	void boxesCoverExactlyAndDoNotOverlap() {
		Random random = new Random(20260805L);
		for (int trial = 0; trial < 2000; trial++) {
			long mask = random.nextLong();
			if (trial < 64) mask = MicroGrid.clear(MicroGrid.FULL, trial % 4, (trial / 4) % 4, (trial / 16) % 4);
			List<MicroGrid.Box> boxes = MicroGrid.boxes(mask);

			double cellVolume = MicroGrid.CELL_SIZE * MicroGrid.CELL_SIZE * MicroGrid.CELL_SIZE;
			double total = 0;
			for (MicroGrid.Box b : boxes) total += b.volume();
			assertEquals(MicroGrid.count(mask) * cellVolume, total, 1e-9,
					"volume mismatch for mask " + Long.toHexString(mask));

			for (int i = 0; i < boxes.size(); i++) {
				for (int j = i + 1; j < boxes.size(); j++) {
					assertFalse(overlaps(boxes.get(i), boxes.get(j)),
							"boxes " + i + " and " + j + " overlap for mask " + Long.toHexString(mask));
				}
			}

			// And every set cell is inside some box.
			for (int x = 0; x < MicroGrid.EDGE; x++) {
				for (int y = 0; y < MicroGrid.EDGE; y++) {
					for (int z = 0; z < MicroGrid.EDGE; z++) {
						if (!MicroGrid.get(mask, x, y, z)) continue;
						assertTrue(covered(boxes, x, y, z),
								"cell " + x + "," + y + "," + z + " uncovered for mask "
										+ Long.toHexString(mask));
					}
				}
			}
		}
	}

	private static boolean covered(List<MicroGrid.Box> boxes, int x, int y, int z) {
		double cx = (x + 0.5) * MicroGrid.CELL_SIZE;
		double cy = (y + 0.5) * MicroGrid.CELL_SIZE;
		double cz = (z + 0.5) * MicroGrid.CELL_SIZE;
		for (MicroGrid.Box b : boxes) {
			if (cx > b.minX() && cx < b.maxX() && cy > b.minY() && cy < b.maxY()
					&& cz > b.minZ() && cz < b.maxZ()) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(MicroGrid.Box a, MicroGrid.Box b) {
		return a.minX() < b.maxX() - 1e-9 && b.minX() < a.maxX() - 1e-9
				&& a.minY() < b.maxY() - 1e-9 && b.minY() < a.maxY() - 1e-9
				&& a.minZ() < b.maxZ() - 1e-9 && b.minZ() < a.maxZ() - 1e-9;
	}

	/** A mask with exactly {@code n} cells set, in bit order. */
	private static long maskOf(int n) {
		return n >= 64 ? MicroGrid.FULL : (1L << n) - 1L;
	}
}

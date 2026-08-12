package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.structure.StructureDelta;
import com.terminaldetector.drmd.world.structure.StructureDelta.Cell;
import com.terminaldetector.drmd.world.structure.StructureDelta.Diff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls {@link StructureDelta} directly — zero Minecraft dependency, same idiom as
 * {@code AerisDensity}/{@code AerisDensityTest}. These tests are about {@code diff()}/{@code translate()}'s
 * own set-algebra guarantees, not about any particular shape, so the sample shape below is an arbitrary
 * small filled box.
 */
class StructureDeltaTest {
	private static Set<Cell> sampleShape() {
		Set<Cell> cells = new HashSet<>();
		for (int x = -2; x <= 2; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -2; z <= 2; z++) {
					cells.add(new Cell(x, y, z));
				}
			}
		}
		return cells;
	}

	@Test
	@DisplayName("diff(X, X) is empty in both directions — an unmoved structure touches nothing")
	void diffOfIdenticalSetsIsEmpty() {
		Set<Cell> shape = sampleShape();
		Diff same = StructureDelta.diff(shape, shape);
		assertTrue(same.toClear().isEmpty(), "toClear should be empty when old == new");
		assertTrue(same.toPlace().isEmpty(), "toPlace should be empty when old == new");

		// Equal-but-distinct Set instance, not just object identity.
		Diff copy = StructureDelta.diff(shape, new HashSet<>(shape));
		assertTrue(copy.toClear().isEmpty());
		assertTrue(copy.toPlace().isEmpty());
	}

	@Test
	@DisplayName("pure translation: |toClear| == |toPlace| — exactly as many cells leave as enter")
	void translationClearAndPlaceCountsMatch() {
		Set<Cell> shape = sampleShape();
		int[][] steps = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {2, -1, 3}, {-3, 0, -1}};
		for (int[] step : steps) {
			Set<Cell> moved = StructureDelta.translate(shape, step[0], step[1], step[2]);
			Diff d = StructureDelta.diff(shape, moved);
			assertEquals(d.toClear().size(), d.toPlace().size(),
					"step (" + step[0] + "," + step[1] + "," + step[2] + "): a pure translation must shed "
							+ "exactly as many cells as it gains, since translate() preserves shape size");
		}
	}

	@Test
	@DisplayName("toClear and toPlace are always disjoint, by construction")
	void clearAndPlaceAreDisjoint() {
		Set<Cell> shape = sampleShape();
		Set<Cell> moved = StructureDelta.translate(shape, 1, 1, -2);
		Diff d = StructureDelta.diff(shape, moved);
		Set<Cell> intersection = new HashSet<>(d.toClear());
		intersection.retainAll(d.toPlace());
		assertTrue(intersection.isEmpty(), "a cell cannot need both clearing and placing at once");
	}

	@Test
	@DisplayName("a translation far beyond the shape's own extent has zero overlap: toClear == old, toPlace == new")
	void translationBeyondExtentHasNoOverlap() {
		Set<Cell> shape = sampleShape(); // spans x,z in [-2,2], y in [-1,1]
		Set<Cell> moved = StructureDelta.translate(shape, 1000, 1000, 1000);
		Diff d = StructureDelta.diff(shape, moved);
		assertEquals(shape, d.toClear(), "with zero overlap, every old cell should need clearing");
		assertEquals(moved, d.toPlace(), "with zero overlap, every new cell should need placing");
	}

	@Test
	@DisplayName("translate is deterministic and size-preserving")
	void translateIsDeterministicAndSizePreserving() {
		Set<Cell> shape = sampleShape();
		Set<Cell> a = StructureDelta.translate(shape, 5, -3, 2);
		Set<Cell> b = StructureDelta.translate(shape, 5, -3, 2);
		assertEquals(a, b);
		assertEquals(shape.size(), a.size());
	}
}

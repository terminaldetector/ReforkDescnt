package com.terminaldetector.drmd.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedSpatialIndexTest {
	@Test
	void sparseQueryMatchesBruteForceAcrossNegativeCellBoundaries() {
		BoundedSpatialIndex<Integer> index = new BoundedSpatialIndex<>(5);
		List<BoundedSpatialIndex.Point> positions = new ArrayList<>();
		for (int value = 0; value < 80; value++) {
			var position = new BoundedSpatialIndex.Point((value % 8) - 3.7, ((value / 8) % 5) - 2.2,
					(value / 40) * 6 - 3.1);
			positions.add(position);
			index.add(value, position);
		}
		var center = new BoundedSpatialIndex.Point(-0.15, 0.2, -0.1);
		double radius = 5.4;
		List<Integer> expected = new ArrayList<>();
		for (int value = 0; value < positions.size(); value++) {
			if ((value & 1) == 0 && positions.get(value).squaredDistanceTo(center) <= radius * radius) {
				expected.add(value);
			}
		}
		expected.sort((left, right) -> Double.compare(positions.get(left).squaredDistanceTo(center),
				positions.get(right).squaredDistanceTo(center)));

		var query = index.nearest(center, radius, 80, 128, 17, value -> (value & 1) == 0);
		assertEquals(expected, query.hits().stream().map(BoundedSpatialIndex.Hit::value).toList());
		assertTrue(query.examinedCandidates() <= positions.size(), "an under-budget query remains exact");
	}

	@Test
	void denseBucketHasAHardCandidateCeilingAndRotatingSample() {
		BoundedSpatialIndex<Integer> index = new BoundedSpatialIndex<>(5);
		for (int value = 0; value < 4096; value++) {
			index.add(value, new BoundedSpatialIndex.Point((value & 15) * 0.01, ((value >> 4) & 15) * 0.01,
					((value >> 8) & 15) * 0.01));
		}
		var center = new BoundedSpatialIndex.Point(.075, .075, .075);
		var first = index.nearest(center, 5, 32, 96, 1, value -> true);
		var second = index.nearest(center, 5, 32, 96, 2, value -> true);

		assertEquals(32, first.hits().size());
		assertEquals(96, first.examinedCandidates());
		assertEquals(1, first.occupiedBuckets());
		assertEquals(32, new HashSet<>(first.hits().stream().map(BoundedSpatialIndex.Hit::value).toList()).size());
		assertNotEquals(first.hits().stream().map(BoundedSpatialIndex.Hit::value).toList(),
				second.hits().stream().map(BoundedSpatialIndex.Hit::value).toList(),
				"changing the tick/caller seed must rotate a saturated bucket sample");
	}

	@Test
	void denseCloudAcrossACellBoundarySharesTheBudget() {
		BoundedSpatialIndex<Integer> index = new BoundedSpatialIndex<>(5);
		for (int value = 0; value < 4096; value++) {
			// Odd values occupy the query's own cell; the slightly nearer even values occupy its
			// neighbour. A local-first implementation must still reserve work for that neighbour.
			double x = (value & 1) == 0 ? 4.999 : 5.02;
			index.add(value, new BoundedSpatialIndex.Point(x, .1, .1));
		}
		var query = index.nearest(new BoundedSpatialIndex.Point(5, .1, .1), 5, 64, 96, 31,
				value -> true);

		assertEquals(96, query.examinedCandidates());
		assertEquals(2, query.occupiedBuckets());
		assertEquals(64, query.hits().size());
		assertEquals(32, query.hits().stream().filter(value -> (value.value() & 1) == 0).count(),
				"the adjacent cell receives the budget it needs to contribute its nearer members");
	}

	@Test
	void thousandAgentPassStaysLinearInItsDeclaredBudget() {
		BoundedSpatialIndex<Integer> index = new BoundedSpatialIndex<>(5);
		int agents = 2048;
		for (int value = 0; value < agents; value++) {
			double angle = value * 2.399963229728653;
			double radius = 2.5 + (value % 64) * .02;
			index.add(value, new BoundedSpatialIndex.Point(Math.cos(angle) * radius, (value % 31) * .03,
					Math.sin(angle) * radius));
		}
		long examined = 0;
		for (int value = 0; value < agents; value++) {
			int self = value;
			var query = index.nearest(new BoundedSpatialIndex.Point(0, 0.45, 0), 5, 32, 96, value,
					candidate -> candidate != self);
			examined += query.examinedCandidates();
			assertTrue(query.examinedCandidates() <= 96);
			assertTrue(query.hits().size() <= 32);
		}
		assertTrue(examined <= (long) agents * 96,
				"pair work is bounded by agents*candidateBudget, never agents squared");
	}
}

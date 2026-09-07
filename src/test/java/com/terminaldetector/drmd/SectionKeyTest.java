package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.store.SectionKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repacked key: three symmetric axes, and a column that says it is one.
 *
 * <p>The audit found the same assumption inside DRMD that it went looking for in vanilla — a store
 * whose key could only name a place on two axes. These are the properties that fix has to have.
 */
class SectionKeyTest {

	@Test
	@DisplayName("a volume section round-trips on all three axes, at every level")
	void volumeRoundTrip() {
		for (int level = 0; level <= SectionKey.MAX_LEVEL; level++) {
			for (int x : new int[] { -524287, -1000, -1, 0, 1, 1000, 524287 }) {
				for (int y : new int[] { -524287, -1, 0, 1, 524287 }) {
					for (int z : new int[] { -524287, -1, 0, 1, 524287 }) {
						long key = SectionKey.of(level, x, y, z);
						assertEquals(level, SectionKey.level(key), "level");
						assertEquals(x, SectionKey.sectionX(key), "x");
						assertEquals(y, SectionKey.sectionY(key), "y");
						assertEquals(z, SectionKey.sectionZ(key), "z");
						assertFalse(SectionKey.isColumn(key), "has a Y, so not a column");
					}
				}
			}
		}
	}

	@Test
	@DisplayName("Y reaches as far as X and Z, which is the point of the repack")
	void theAxesAreSymmetric() {
		// The same extreme on each axis, and none of them is the one that runs out first.
		long key = SectionKey.of(0, 524287, 524287, 524287);
		assertEquals(524287, SectionKey.sectionX(key), "x");
		assertEquals(524287, SectionKey.sectionY(key), "y");
		assertEquals(524287, SectionKey.sectionZ(key), "z");

		assertThrows(IllegalArgumentException.class, () -> SectionKey.of(0, 524288, 0, 0), "x past it");
		assertThrows(IllegalArgumentException.class, () -> SectionKey.of(0, 0, 524288, 0), "y past it");
		assertThrows(IllegalArgumentException.class, () -> SectionKey.of(0, 0, 0, 524288), "z past it");
	}

	@Test
	@DisplayName("a surface section is a column, and says so rather than being indistinguishable")
	void columnsAreExplicit() {
		long column = SectionKey.of(3, -17, 42);

		assertTrue(SectionKey.isColumn(column), "it is one");
		assertEquals(SectionKey.COLUMN, SectionKey.sectionY(column), "and its Y is the marker");
		assertEquals(-17, SectionKey.sectionX(column), "x");
		assertEquals(42, SectionKey.sectionZ(column), "z");
		assertEquals(3, SectionKey.level(column), "level");
	}

	@Test
	@DisplayName("the column marker cannot be given as a real coordinate, so it stays unambiguous")
	void theMarkerIsReserved() {
		assertThrows(IllegalArgumentException.class,
				() -> SectionKey.of(0, 0, SectionKey.COLUMN, 0), "as an explicit Y");
		assertThrows(IllegalArgumentException.class,
				() -> SectionKey.of(0, SectionKey.COLUMN, 0), "and on the other axes too");
		assertThrows(IllegalArgumentException.class,
				() -> SectionKey.of(0, 0, 0, SectionKey.COLUMN), "z");
	}

	@Test
	@DisplayName("a column and a volume section over the same ground are different keys")
	void aColumnIsNotTheVolumeAtZero() {
		assertTrue(SectionKey.of(0, 5, 7) != SectionKey.of(0, 5, 0, 7),
				"otherwise a surface section and the box at Y=0 would collide");
	}

	@Test
	@DisplayName("a column's parent is a column, and a volume section's keeps its Y")
	void parentsKeepTheirKind() {
		long column = SectionKey.parent(SectionKey.of(0, 9, -9));
		assertTrue(SectionKey.isColumn(column), "still a column");
		assertEquals(4, SectionKey.sectionX(column), "9 >> 1");
		assertEquals(-5, SectionKey.sectionZ(column), "-9 >> 1, floored not truncated");

		long volume = SectionKey.parent(SectionKey.of(0, 9, -9, 9));
		assertFalse(SectionKey.isColumn(volume), "still a volume");
		assertEquals(-5, SectionKey.sectionY(volume), "its Y halved with the rest");

		long top = SectionKey.of(SectionKey.MAX_LEVEL, 1, 1, 1);
		assertEquals(top, SectionKey.parent(top), "the coarsest level is its own parent");
	}

	@Test
	@DisplayName("distinct addresses give distinct keys, columns and volumes together")
	void noCollisions() {
		Set<Long> seen = new HashSet<>();
		for (int level = 0; level <= SectionKey.MAX_LEVEL; level++) {
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					assertTrue(seen.add(SectionKey.of(level, x, z)), "column collision");
					for (int y = -4; y <= 4; y++) {
						assertTrue(seen.add(SectionKey.of(level, x, y, z)), "volume collision");
					}
				}
			}
		}
		// 7 levels * 81 columns, plus 7 * 81 * 9 volumes.
		assertEquals(7 * 81 + 7 * 81 * 9, seen.size(), "every address its own key");
	}
}

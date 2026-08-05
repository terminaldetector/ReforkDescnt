package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.store.CompressedSectionStorage;
import com.terminaldetector.drmd.world.store.MemorySectionStorage;
import com.terminaldetector.drmd.world.store.SectionKey;
import com.terminaldetector.drmd.world.store.SurfaceSection;
import com.terminaldetector.drmd.world.store.SurfaceStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LOD store, which is all plain arithmetic and bytes and therefore the one part of this that can
 * be held to account without a running game.
 */
class SurfaceStoreTest {

	@Test
	@DisplayName("keys survive a round trip, negative coordinates included")
	void keysRoundTrip() {
		int[] coords = {0, 1, -1, 37, -37, 1 << 20, -(1 << 20), (1 << 28) - 1, -(1 << 28)};
		for (int level = 0; level <= SectionKey.MAX_LEVEL; level++) {
			for (int x : coords) {
				for (int z : coords) {
					long key = SectionKey.of(level, x, z);
					assertEquals(level, SectionKey.level(key), "level");
					assertEquals(x, SectionKey.sectionX(key), "x at level " + level);
					assertEquals(z, SectionKey.sectionZ(key), "z at level " + level);
				}
			}
		}
	}

	@Test
	@DisplayName("distinct addresses never collide on one key")
	void keysAreDistinct() {
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (int level = 0; level <= SectionKey.MAX_LEVEL; level++) {
			for (int x = -8; x <= 8; x++) {
				for (int z = -8; z <= 8; z++) {
					assertTrue(seen.add(SectionKey.of(level, x, z)),
							"collision at " + level + " " + x + "," + z);
				}
			}
		}
	}

	/**
	 * The one that matters for placement: a block position has to land in the same cell whichever
	 * side of the origin it is on. Integer division truncating toward zero is the classic way this
	 * breaks, and it breaks silently — half the world lands one cell off.
	 */
	@Test
	@DisplayName("negative coordinates land in the cell that contains them")
	void cellMathIsFloorBased() {
		for (int level = 0; level <= SectionKey.MAX_LEVEL; level++) {
			int cell = SectionKey.cellSize(level);
			int span = SectionKey.sectionSize(level);
			for (int block : new int[]{-span * 2, -span - 1, -cell - 1, -1, 0, 1, cell, span, span * 3 + 5}) {
				int sx = SectionKey.sectionOf(block, level);
				int inSection = SectionKey.cellInSection(block, level);
				assertTrue(inSection >= 0 && inSection < SectionKey.SECTION_CELLS,
						"cell index " + inSection + " out of range");
				// Reconstruct the cell's own origin from section + index and check it contains it.
				long cellOrigin = (long) sx * span + (long) inSection * cell;
				assertTrue(block >= cellOrigin && block < cellOrigin + cell,
						"block " + block + " at level " + level + " outside its cell " + cellOrigin);
			}
		}
	}

	@Test
	@DisplayName("a section survives being written and read back")
	void sectionRoundTrips() {
		SurfaceSection s = new SurfaceSection();
		s.set(0, 0, 64, 0x336699);
		s.set(31, 31, -47, 0xFF0000);
		s.set(17, 4, 300, 0x00FF88);

		SurfaceSection back = SurfaceSection.fromBytes(s.toBytes());
		assertEquals(64, back.height(0, 0));
		assertEquals(0x336699, back.colour(0, 0));
		assertEquals(-47, back.height(31, 31));
		assertEquals(300, back.height(17, 4));
		assertEquals(0x00FF88, back.colour(17, 4));
		assertTrue(back.known(0, 0));
		assertTrue(!back.known(5, 5), "never-observed cell should read as unknown");
	}

	@Test
	@DisplayName("a section from another format is refused, not misread")
	void badBytesAreRefused() {
		assertNull(SurfaceSection.fromBytes(null));
		assertNull(SurfaceSection.fromBytes(new byte[3]));
		byte[] wrongVersion = new SurfaceSection().toBytes();
		wrongVersion[0] = 99;
		assertNull(SurfaceSection.fromBytes(wrongVersion));
	}

	@Test
	@DisplayName("a cell takes the newest sample, not the tallest")
	void cellsTakeTheNewestSample() {
		SurfaceSection s = new SurfaceSection();
		s.set(3, 3, 200, 0x222222);
		s.set(3, 3, 90, 0x333333);
		assertEquals(90, s.height(3, 3), "a levelled cell has to be able to go down");
		assertEquals(0x333333, s.colour(3, 3));
	}

	@Test
	@DisplayName("but a coarse cell takes the tallest of the four under it")
	void mipKeepsTheSilhouette() {
		SurfaceSection child = new SurfaceSection();
		// The 2x2 of child cells that folds into parent cell (0,0).
		child.set(0, 0, 70, 0x111111);
		child.set(1, 0, 240, 0x222222);
		child.set(0, 1, 65, 0x333333);
		child.set(1, 1, 68, 0x444444);

		SurfaceSection parent = new SurfaceSection();
		parent.mipFrom(child, 0, 0);
		assertEquals(240, parent.height(0, 0), "the tower has to survive into the coarse level");
		assertEquals(0x222222, parent.colour(0, 0), "colour follows the height that won");
	}

	@Test
	@DisplayName("compression is transparent to whatever is above it")
	void compressionRoundTrips() {
		MemorySectionStorage backing = new MemorySectionStorage();
		CompressedSectionStorage store = new CompressedSectionStorage(backing);
		SurfaceSection s = new SurfaceSection();
		for (int x = 0; x < 32; x++) {
			for (int z = 0; z < 32; z++) {
				s.set(x, z, 64 + ((x + z) & 7), 0x4C7638);
			}
		}
		byte[] raw = s.toBytes();
		long key = SectionKey.of(0, -3, 12);
		store.write(key, raw);
		assertArrayEquals(raw, store.read(key));
		assertNull(store.read(SectionKey.of(0, 99, 99)), "nothing stored there");

		byte[] packed = backing.read(key);
		assertTrue(packed.length < raw.length / 2,
				"a smooth section should compress well, got " + packed.length + " of " + raw.length);
	}

	@Test
	@DisplayName("observations land where they were made, at every level")
	void observationsReachEveryLevel() {
		SurfaceStore store = new SurfaceStore(new MemorySectionStorage());
		store.set(1000, -2000, 128, 0xABCDEF);
		assertEquals(128, store.heightAt(1000, -2000, 0));

		// Coarse levels are empty until the rebuild pass runs.
		assertEquals(SurfaceSection.NO_HEIGHT, store.heightAt(1000, -2000, 1));
		assertTrue(store.dirtyParentCount() > 0);
		while (store.rebuildDirty(16) > 0) { /* drain */ }

		for (int level = 1; level <= SectionKey.MAX_LEVEL; level++) {
			assertEquals(128, store.heightAt(1000, -2000, level), "level " + level);
			assertEquals(0xABCDEF, store.colourAt(1000, -2000, level), "colour at level " + level);
		}
	}

	/**
	 * The reason coarse levels are rebuilt instead of accumulated. Stage 10 turns terrain into
	 * rubble; a running maximum would keep drawing the tower that was shot down.
	 */
	@Test
	@DisplayName("a coarse level comes back down when the ground does")
	void coarseLevelsFollowDemolition() {
		SurfaceStore store = new SurfaceStore(new MemorySectionStorage());
		store.set(64, 64, 250, 0xFFFFFF);
		while (store.rebuildDirty(16) > 0) { /* drain */ }
		assertEquals(250, store.heightAt(64, 64, 2), "the tower is in the coarse level");

		store.set(64, 64, 70, 0x777777);
		while (store.rebuildDirty(16) > 0) { /* drain */ }
		assertEquals(70, store.heightAt(64, 64, 2), "and comes back out of it once it is gone");
		assertNotEquals(250, store.heightAt(64, 64, 4), "at every coarse level, not just the first");
	}

	@Test
	@DisplayName("what was stored is still there after a flush and a fresh store")
	void survivesRestart() {
		MemorySectionStorage backing = new MemorySectionStorage();
		SurfaceStore store = new SurfaceStore(new CompressedSectionStorage(backing));
		store.set(-5000, 300, 96, 0x123456);
		while (store.rebuildDirty(16) > 0) { /* drain */ }
		store.flush();

		SurfaceStore reopened = new SurfaceStore(new CompressedSectionStorage(backing));
		assertEquals(96, reopened.heightAt(-5000, 300, 0));
		assertEquals(0x123456, reopened.colourAt(-5000, 300, 0));
		assertEquals(96, reopened.heightAt(-5000, 300, 3), "coarse levels persist too");
	}
}

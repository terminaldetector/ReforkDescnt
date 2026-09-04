package com.terminaldetector.drmd;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three-axis chunk position.
 *
 * <p>Negative coordinates are the whole risk here, and they are not an edge case: a world where Y is
 * a real axis spends half its extent below zero. Java's division and remainder truncate toward zero,
 * so a coordinate system built on them is subtly wrong exactly there — and "subtly wrong below y=0"
 * is a bug that looks like broken worldgen a long way from its cause.
 *
 * <p>The cases below are the brief's own list: −1, −16, −17, ±1,000,000, the axis limits, and the
 * boundaries between chunks.
 */
class D6ChunkPosTest {

	@Test
	@DisplayName("block to chunk rounds down, not toward zero")
	void blockToChunkRoundsDown() {
		assertEquals(0, D6ChunkPos.blockToChunk(0));
		assertEquals(0, D6ChunkPos.blockToChunk(15));
		assertEquals(1, D6ChunkPos.blockToChunk(16));
		// The three that division would get wrong: -1/16 and -16/16 are both 0 and -1 in Java, and
		// -17/16 is -1 where the chunk containing block -17 is -2.
		assertEquals(-1, D6ChunkPos.blockToChunk(-1));
		assertEquals(-1, D6ChunkPos.blockToChunk(-16));
		assertEquals(-2, D6ChunkPos.blockToChunk(-17));
		assertEquals(62500, D6ChunkPos.blockToChunk(1_000_000));
		assertEquals(-62500, D6ChunkPos.blockToChunk(-1_000_000));
	}

	@Test
	@DisplayName("the position inside a chunk is always 0 to 15, below zero included")
	void localIsAlwaysNonNegative() {
		assertEquals(0, D6ChunkPos.blockToLocal(0));
		assertEquals(15, D6ChunkPos.blockToLocal(15));
		assertEquals(0, D6ChunkPos.blockToLocal(16));
		assertEquals(15, D6ChunkPos.blockToLocal(-1));
		assertEquals(0, D6ChunkPos.blockToLocal(-16));
		assertEquals(15, D6ChunkPos.blockToLocal(-17));

		// The property that matters: chunk times size plus local reconstructs the block, everywhere.
		for (int block : new int[]{-1_000_000, -17, -16, -1, 0, 15, 16, 1_000_000}) {
			assertEquals(block,
					D6ChunkPos.chunkToMinBlock(D6ChunkPos.blockToChunk(block)) + D6ChunkPos.blockToLocal(block),
					"reconstruction failed for block " + block);
		}
	}

	@Test
	@DisplayName("packing survives a round trip at the axis limits and below zero")
	void packRoundTrips() {
		int lo = D6ChunkPos.MIN_COORDINATE;
		int hi = D6ChunkPos.MAX_COORDINATE;
		assertEquals(-1_048_576, lo, "21 signed bits");
		assertEquals(1_048_575, hi);

		int[][] cases = {
				{0, 0, 0}, {-1, -1, -1}, {lo, lo, lo}, {hi, hi, hi},
				{lo, hi, 0}, {-1, 0, 1}, {1, -1, 0}, {-62500, 62500, -1},
		};
		for (int[] c : cases) {
			D6ChunkPos pos = new D6ChunkPos(c[0], c[1], c[2]);
			assertEquals(pos, D6ChunkPos.fromLong(pos.asLong()), "round trip for " + pos);
		}
	}

	@Test
	@DisplayName("packing survives a round trip over twenty thousand random positions")
	void packRoundTripsRandomly() {
		Random random = new Random(3);
		int lo = D6ChunkPos.MIN_COORDINATE;
		int span = D6ChunkPos.MAX_COORDINATE - lo;
		for (int i = 0; i < 20_000; i++) {
			D6ChunkPos pos = new D6ChunkPos(
					lo + random.nextInt(span), lo + random.nextInt(span), lo + random.nextInt(span));
			assertEquals(pos, D6ChunkPos.fromLong(pos.asLong()));
		}
	}

	@Test
	@DisplayName("distinct positions pack to distinct longs")
	void packingDoesNotCollide() {
		Set<Long> seen = new HashSet<>();
		for (int x = -2; x <= 2; x++) {
			for (int y = -2; y <= 2; y++) {
				for (int z = -2; z <= 2; z++) {
					assertTrue(seen.add(D6ChunkPos.asLong(x, y, z)),
							"collision at " + x + "," + y + "," + z);
				}
			}
		}
		assertEquals(125, seen.size());
	}

	@Test
	@DisplayName("the invalid value cannot be a real position, unlike in the donor")
	void invalidIsUnreachable() {
		// Cubic Chunks had to invert two bits so that Long.MAX_VALUE would not be (-1, -1, -1). Here
		// bit 63 is simply never set by a valid position, so Long.MIN_VALUE is free.
		assertNotEquals(D6ChunkPos.INVALID, D6ChunkPos.asLong(-1, -1, -1));
		int lo = D6ChunkPos.MIN_COORDINATE;
		int hi = D6ChunkPos.MAX_COORDINATE;
		for (int x : new int[]{lo, -1, 0, hi}) {
			for (int y : new int[]{lo, -1, 0, hi}) {
				for (int z : new int[]{lo, -1, 0, hi}) {
					long packed = D6ChunkPos.asLong(x, y, z);
					assertNotEquals(D6ChunkPos.INVALID, packed);
					// The three axes occupy bits 0 to 62, so a valid position never sets the sign bit —
					// which is the whole reason Long.MIN_VALUE is free to mean "none".
					assertTrue(packed >= 0, "a valid position set the top bit: " + x + "," + y + "," + z);
				}
			}
		}
	}

	@Test
	@DisplayName("a coordinate that will not fit is refused, not wrapped")
	void outOfRangeThrows() {
		assertFalse(D6ChunkPos.inRange(D6ChunkPos.MAX_COORDINATE + 1));
		assertFalse(D6ChunkPos.inRange(D6ChunkPos.MIN_COORDINATE - 1));
		// Wrapping would alias this chunk onto one on the far side of the world, and the corruption
		// would surface far from its cause.
		assertThrows(IllegalArgumentException.class,
				() -> D6ChunkPos.asLong(D6ChunkPos.MAX_COORDINATE + 1, 0, 0));
		assertThrows(IllegalArgumentException.class,
				() -> D6ChunkPos.asLong(0, D6ChunkPos.MIN_COORDINATE - 1, 0));
	}

	@Test
	@DisplayName("Y reaches exactly as far as X and Z, which is the entire point")
	void axesAreSymmetric() {
		int hi = D6ChunkPos.MAX_COORDINATE;
		assertEquals(D6ChunkPos.chunkToMinBlock(hi), 16_777_200, "chunk reach in blocks");
		// Same value on every axis, unlike vanilla BlockPos where Y gets twelve bits to X and Z's
		// twenty-six.
		D6ChunkPos far = new D6ChunkPos(hi, hi, hi);
		assertEquals(far.minBlockX(), far.minBlockY());
		assertEquals(far.minBlockY(), far.minBlockZ());
	}

	@Test
	@DisplayName("distance is measured in three dimensions, not around a column")
	void distanceIsThreeDimensional() {
		D6ChunkPos origin = D6ChunkPos.ORIGIN;
		// Straight up is as far as straight out. A horizontal-only measure would call this zero.
		assertEquals(5, origin.chebyshevDistance(new D6ChunkPos(0, 5, 0)));
		assertEquals(5, origin.chebyshevDistance(new D6ChunkPos(5, 0, 0)));
		assertEquals(5, origin.chebyshevDistance(new D6ChunkPos(3, 5, 4)));
		assertEquals(50.0, origin.squaredDistance(new D6ChunkPos(3, 4, 5)), 1e-12);
	}
}

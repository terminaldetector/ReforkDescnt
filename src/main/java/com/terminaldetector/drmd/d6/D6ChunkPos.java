package com.terminaldetector.drmd.d6;

/**
 * A chunk position in three symmetric axes, packed into one {@code long}.
 *
 * <p><b>Why this exists rather than a Minecraft type.</b> Vanilla's {@code BlockPos} packs a position
 * into a long by deriving its bit layout from the horizontal world limit: 26 bits for X, 26 for Z,
 * and Y gets what is left, which is twelve — plus or minus 2048. Y is not merely conventionally
 * special in Minecraft, it is special at the bit level, and the only way to make it equal inside that
 * type is to take bits away from X and Z. Cubic Chunks does exactly that, trading the horizontal
 * limit from thirty million down to one to buy Y twenty-two bits; the whole of its {@code BlockPos}
 * mixin is that one constant. See {@code docs/source-audit/cubic-chunks.md}.
 *
 * <p>So DRMD does not reuse it. Sixty-four bits split three ways is twenty-one apiece, and twenty-one
 * signed bits is −1,048,576 to 1,048,575 chunks per axis — ±16,777,216 blocks, symmetric in all
 * three. Comparable to vanilla's horizontal reach, and the same in Y.
 *
 * <p><b>Chunks are packed; blocks are not.</b> Packing exists to make a map key, and the maps are
 * keyed by chunk. A block position packed into twenty-one bits an axis would cap the world at a
 * million blocks, which is the compromise Cubic Chunks had to accept and DRMD does not have to.
 *
 * <p><b>Shift and mask, never divide and remainder.</b> {@code blockToChunk} is an arithmetic right
 * shift, which is division rounding <em>down</em>: −17 >> 4 is −2, where −17 / 16 is −1. And
 * {@code blockToLocal} is a bitwise and, which gives a non-negative remainder: −1 & 15 is 15, where
 * −1 % 16 is −1. Java's {@code /} and {@code %} truncate toward zero, so a coordinate system built on
 * them breaks at negative coordinates — which is to say it breaks below y=0, which is where a
 * symmetric world spends half its time.
 *
 * <p><b>{@link #INVALID} is bit 63.</b> Every valid position leaves the top bit clear, since the
 * three axes use bits 0 through 62, so {@code Long.MIN_VALUE} cannot collide with any of them. Cubic
 * Chunks could not do this — it needed its packed cube positions to share a long-keyed map with
 * vanilla's packed chunk positions, so it spent the two top bits on a tag and inverted them so that
 * {@code Long.MAX_VALUE} would not be the valid position (−1, −1, −1). DRMD's chunk keys share a map
 * with nothing, so the simpler scheme is available. Worth knowing why the donor's is more
 * complicated: it is not over-engineering, it is a constraint DRMD does not have.
 */
public record D6ChunkPos(int x, int y, int z) {

	/** Blocks along one edge of a chunk. A power of two, which is what lets every conversion be a shift. */
	public static final int CHUNK_SIZE = 16;
	private static final int CHUNK_SIZE_BITS = 4;
	private static final int CHUNK_SIZE_MASK = CHUNK_SIZE - 1;

	/** Bits each axis gets. Three of these plus a spare is sixty-four. */
	public static final int AXIS_BITS = 21;
	private static final long AXIS_MASK = (1L << AXIS_BITS) - 1;
	private static final int Y_SHIFT = AXIS_BITS;
	private static final int X_SHIFT = AXIS_BITS * 2;

	public static final int MIN_COORDINATE = -(1 << (AXIS_BITS - 1));
	public static final int MAX_COORDINATE = (1 << (AXIS_BITS - 1)) - 1;

	/** No position — distinguishable from every real one, because valid positions never set bit 63. */
	public static final long INVALID = Long.MIN_VALUE;

	public static final D6ChunkPos ORIGIN = new D6ChunkPos(0, 0, 0);

	/** Whether a coordinate fits in an axis. Outside this, packing would silently wrap. */
	public static boolean inRange(int coordinate) {
		return coordinate >= MIN_COORDINATE && coordinate <= MAX_COORDINATE;
	}

	/** The chunk containing a block coordinate. */
	public static int blockToChunk(int blockCoordinate) {
		return blockCoordinate >> CHUNK_SIZE_BITS;
	}

	/** Where a block sits inside its chunk, always 0 to 15 — including for negative coordinates. */
	public static int blockToLocal(int blockCoordinate) {
		return blockCoordinate & CHUNK_SIZE_MASK;
	}

	/** The lowest block coordinate belonging to a chunk coordinate. */
	public static int chunkToMinBlock(int chunkCoordinate) {
		return chunkCoordinate << CHUNK_SIZE_BITS;
	}

	public static D6ChunkPos ofBlock(int blockX, int blockY, int blockZ) {
		return new D6ChunkPos(blockToChunk(blockX), blockToChunk(blockY), blockToChunk(blockZ));
	}

	/**
	 * Pack into a long.
	 *
	 * @throws IllegalArgumentException when a coordinate does not fit. Loudly rather than silently:
	 *         a wrapped coordinate is a chunk that quietly aliases another one on the far side of the
	 *         world, and that failure surfaces as corrupted terrain a long way from its cause.
	 */
	public long asLong() {
		return asLong(x, y, z);
	}

	public static long asLong(int x, int y, int z) {
		if (!inRange(x) || !inRange(y) || !inRange(z)) {
			throw new IllegalArgumentException(
					"chunk coordinate out of range (" + x + ", " + y + ", " + z + "), each axis holds "
							+ MIN_COORDINATE + " to " + MAX_COORDINATE);
		}
		return ((x & AXIS_MASK) << X_SHIFT) | ((y & AXIS_MASK) << Y_SHIFT) | (z & AXIS_MASK);
	}

	public static D6ChunkPos fromLong(long packed) {
		return new D6ChunkPos(unpackX(packed), unpackY(packed), unpackZ(packed));
	}

	public static int unpackX(long packed) {
		return signExtend(packed >> X_SHIFT);
	}

	public static int unpackY(long packed) {
		return signExtend(packed >> Y_SHIFT);
	}

	public static int unpackZ(long packed) {
		return signExtend(packed);
	}

	/**
	 * Sign-extend an axis field back to a full int.
	 *
	 * <p>Shift left until the field's top bit is the int's sign bit, then shift right arithmetically
	 * so it propagates. Masking alone would make every negative coordinate a large positive one.
	 */
	private static int signExtend(long field) {
		return (int) (field & AXIS_MASK) << (Integer.SIZE - AXIS_BITS) >> (Integer.SIZE - AXIS_BITS);
	}

	public D6ChunkPos offset(int dx, int dy, int dz) {
		return new D6ChunkPos(x + dx, y + dy, z + dz);
	}

	/** The lowest block corner of this chunk. */
	public int minBlockX() {
		return chunkToMinBlock(x);
	}

	public int minBlockY() {
		return chunkToMinBlock(y);
	}

	public int minBlockZ() {
		return chunkToMinBlock(z);
	}

	/**
	 * Chebyshev distance in chunks — the number of steps a cube-shaped loading volume needs.
	 *
	 * <p>Chebyshev rather than Euclidean because a streaming volume is a box, and rather than
	 * horizontal-only because the whole point of this class is that Y is not different. A volume
	 * measured by {@code horizontalDistance} is a column however cubic its chunks are.
	 */
	public int chebyshevDistance(D6ChunkPos other) {
		return Math.max(Math.abs(x - other.x), Math.max(Math.abs(y - other.y), Math.abs(z - other.z)));
	}

	public double squaredDistance(D6ChunkPos other) {
		double dx = x - other.x;
		double dy = y - other.y;
		double dz = z - other.z;
		return dx * dx + dy * dy + dz * dz;
	}

	@Override
	public String toString() {
		return "D6ChunkPos[" + x + ", " + y + ", " + z + "]";
	}
}

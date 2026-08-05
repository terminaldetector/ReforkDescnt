package com.terminaldetector.drmd.world.store;

/**
 * What the world actually looks like from above, over one section: a top height and a colour for
 * each of 32×32 cells.
 *
 * <p>This is the payload the horizon consumes, so it is what the store holds first. It is a long way
 * short of a voxel section — no overhangs, no interiors, one sample per cell — and that is the
 * point: it is what the current renderer can draw, it costs six kilobytes a section, and it turns
 * the horizon from a plausible planet into the world that was actually flown over. Voxel sections
 * replace it under the same keys when there is something able to draw them.
 *
 * <p>A cell is one chunk at level 0. An empty cell — one nothing has been seen over yet — keeps
 * {@link #NO_HEIGHT}, and the renderer falls back to the procedural map there rather than drawing a
 * hole.
 */
public final class SurfaceSection {
	public static final int CELLS = SectionKey.SECTION_CELLS;
	public static final int AREA = CELLS * CELLS;

	/** Height of a cell nothing has been observed over. */
	public static final short NO_HEIGHT = Short.MIN_VALUE;

	/** Bumped when the layout changes; older sections are then dropped rather than misread. */
	private static final byte FORMAT = 1;
	private static final int BYTES = 1 + AREA * 2 + AREA * 3;

	private final short[] height = new short[AREA];
	private final int[] colour = new int[AREA];
	private boolean dirty;

	public SurfaceSection() {
		java.util.Arrays.fill(height, NO_HEIGHT);
	}

	private static int index(int cellX, int cellZ) {
		return (cellZ & (CELLS - 1)) * CELLS + (cellX & (CELLS - 1));
	}

	public short height(int cellX, int cellZ) {
		return height[index(cellX, cellZ)];
	}

	public int colour(int cellX, int cellZ) {
		return colour[index(cellX, cellZ)];
	}

	public boolean known(int cellX, int cellZ) {
		return height[index(cellX, cellZ)] != NO_HEIGHT;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void clean() {
		dirty = false;
	}

	/**
	 * Record what is on top of a cell, replacing whatever was there.
	 *
	 * <p>Unconditional, because a cell is one chunk and one ingest knows the whole answer for it.
	 * A version that kept the taller of old and new could never let the world get shorter.
	 */
	public void set(int cellX, int cellZ, int worldHeight, int rgb) {
		int i = index(cellX, cellZ);
		short h = (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, worldHeight));
		if (height[i] == h && colour[i] == (rgb & 0xFFFFFF)) return;
		height[i] = h;
		colour[i] = rgb & 0xFFFFFF;
		dirty = true;
	}

	/** True once anything at all has been seen over this section. */
	public boolean hasAny() {
		for (short h : height) {
			if (h != NO_HEIGHT) return true;
		}
		return false;
	}

	/**
	 * Fold a finer section into this one, into the quadrant it belongs to.
	 *
	 * <p>Four child sections tile a parent, so each contributes a 16×16 corner of it: two child
	 * cells across map to one parent cell, and the tallest of the four wins — a horizon is a
	 * silhouette, and averaging a tower into the field beside it loses the thing it is for. The four
	 * quadrants are disjoint, so this assigns rather than merges, and a parent rebuilt from fresh
	 * can come out shorter than the one before it.
	 */
	public void mipFrom(SurfaceSection child, int quadrantX, int quadrantZ) {
		int offsetX = quadrantX * (CELLS / 2);
		int offsetZ = quadrantZ * (CELLS / 2);
		for (int cx = 0; cx < CELLS / 2; cx++) {
			for (int cz = 0; cz < CELLS / 2; cz++) {
				short best = NO_HEIGHT;
				int bestColour = 0;
				for (int dx = 0; dx < 2; dx++) {
					for (int dz = 0; dz < 2; dz++) {
						int ci = index(cx * 2 + dx, cz * 2 + dz);
						short h = child.height[ci];
						if (h == NO_HEIGHT) continue;
						if (best == NO_HEIGHT || h > best) {
							best = h;
							bestColour = child.colour[ci];
						}
					}
				}
				if (best == NO_HEIGHT) continue;
				int i = index(offsetX + cx, offsetZ + cz);
				height[i] = best;
				colour[i] = bestColour;
				dirty = true;
			}
		}
	}

	public byte[] toBytes() {
		byte[] out = new byte[BYTES];
		out[0] = FORMAT;
		int p = 1;
		for (short h : height) {
			out[p++] = (byte) (h >> 8);
			out[p++] = (byte) h;
		}
		for (int c : colour) {
			out[p++] = (byte) (c >> 16);
			out[p++] = (byte) (c >> 8);
			out[p++] = (byte) c;
		}
		return out;
	}

	/** Reads a stored section, or {@code null} for anything this build cannot make sense of. */
	public static SurfaceSection fromBytes(byte[] data) {
		if (data == null || data.length != BYTES || data[0] != FORMAT) return null;
		SurfaceSection s = new SurfaceSection();
		int p = 1;
		for (int i = 0; i < AREA; i++) {
			s.height[i] = (short) (((data[p++] & 0xFF) << 8) | (data[p++] & 0xFF));
		}
		for (int i = 0; i < AREA; i++) {
			s.colour[i] = ((data[p++] & 0xFF) << 16) | ((data[p++] & 0xFF) << 8) | (data[p++] & 0xFF);
		}
		return s;
	}

	public static int byteSize() {
		return BYTES;
	}
}

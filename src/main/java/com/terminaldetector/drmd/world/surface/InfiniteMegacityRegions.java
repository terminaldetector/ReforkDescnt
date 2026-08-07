package com.terminaldetector.drmd.world.surface;

import net.minecraft.util.math.BlockPos;

/**
 * Grid for {@code DrmdServerConfig.WorldKind.INFINITE_MEGACITY} worlds: every cell gets a city, tiled
 * without limit or spawn exclusion — unlike {@link MegacityRegions}, which places a stock world's
 * single sparse plate type on a hashed ~1-in-6-cells roll kept clear of spawn.
 *
 * <p>{@link #PITCH}, the spacing between plate centres, has to clear the widest thing
 * {@code MegacityGenerator} draws off-centre: the artifact hangar, which reaches {@code half+20=76}
 * blocks out (in +Z only — every other feature, including the plate rim, stays inside {@code half+10
 * =66}). 160 keeps each plate's full {@code [-80,+80]} half-cell wider than that 76-block reach in
 * every direction, not just the one the hangar happens to use, so neighbouring plates can never draw
 * into each other regardless of which feature is doing the reaching. The 8 blocks left over
 * ({@code 160 - 2*76}) is deliberately tight, not a generous buffer — the point of this mode is
 * contiguous city, not city with parks between the districts.
 */
public final class InfiniteMegacityRegions {
	/** Spacing between plate centres, both axes. See class doc for the 76-block reach this clears. */
	public static final int PITCH = 160;

	private InfiniteMegacityRegions() {}

	public static int cellOf(int block) {
		return Math.floorDiv(block, PITCH);
	}

	/** Deterministic plate centre for a grid cell — every cell has one, no hash, no exclusion. */
	public static BlockPos anchorForCell(int cellX, int cellZ) {
		return new BlockPos(cellX * PITCH + PITCH / 2, 0, cellZ * PITCH + PITCH / 2);
	}
}

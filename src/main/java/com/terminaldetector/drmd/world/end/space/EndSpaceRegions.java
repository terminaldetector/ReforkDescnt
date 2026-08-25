package com.terminaldetector.drmd.world.end.space;

import net.minecraft.util.math.BlockPos;

/**
 * Grid for Layer 2 ("End Space") tiles — same shape as {@code InfiniteMegacityRegions}: every cell
 * past {@link #INNER_RADIUS_CELLS} gets a tile, tiled without limit, discovered on chunk-load rather
 * than placed by a single fixed-size stamp like {@code CitadelStationGenerator}'s one arena.
 *
 * <p>{@link #PITCH} only has to clear {@code EndSpaceTileShape.HALF_EXTENT} (12, a 25x25 footprint)
 * with room to spare — unlike the Overworld megacity grid, which packs plates edge to edge on purpose,
 * Layer 2 is meant to read as open space between tiles, not contiguous city.
 */
public final class EndSpaceRegions {
	/** Spacing between tile centres, both axes. Generous next to the 25-block-wide tile on purpose. */
	public static final int PITCH = 64;

	/**
	 * Cells closer than this (in cell units, not blocks) to the origin never generate a tile — clear of
	 * the real End's Citadel arena ({@code EndReactorSession#arenaCenter} is origin-XZ-centred too, and
	 * {@code CitadelDeckShape.HALF_EXTENT}=36 bounds its 73x73 footprint) plus its own approach space,
	 * so Layer 2 reads as "past the reactor," not a suburb of the arena a gateway happens to land near.
	 */
	public static final int INNER_RADIUS_CELLS = 3;

	private EndSpaceRegions() {}

	public static int cellOf(int block) {
		return Math.floorDiv(block, PITCH);
	}

	/** Deterministic tile centre for a grid cell — every cell past the inner radius has one. */
	public static BlockPos anchorForCell(int cellX, int cellZ) {
		return new BlockPos(cellX * PITCH + PITCH / 2, 0, cellZ * PITCH + PITCH / 2);
	}

	/** Is this cell far enough from the origin (the Citadel arena) to host a tile at all? */
	public static boolean isBeyondInnerRadius(int cellX, int cellZ) {
		long dx = cellX, dz = cellZ;
		return dx * dx + dz * dz >= (long) INNER_RADIUS_CELLS * INNER_RADIUS_CELLS;
	}

	/**
	 * Nearest tile anchor to a world XZ position, guaranteed to actually be tiled — used to point an
	 * exit gateway somewhere {@link EndSpaceWorldgen} will really build, not at whatever cell the
	 * gateway's own position naively falls in (for the real End that's always the origin itself, deep
	 * inside the excluded Citadel zone). A point already past the inner radius keeps its own cell;
	 * one that isn't gets pushed straight out along whichever single axis it already leans toward, by
	 * exactly {@code INNER_RADIUS_CELLS + 1} cells — enough on its own to clear {@link #isBeyondInnerRadius}
	 * (its square alone already exceeds {@code INNER_RADIUS_CELLS}²) without needing a diagonal
	 * push that could undershoot after rounding.
	 */
	public static BlockPos nearestTileAnchor(int worldX, int worldZ) {
		int cellX = cellOf(worldX);
		int cellZ = cellOf(worldZ);
		if (isBeyondInnerRadius(cellX, cellZ)) return anchorForCell(cellX, cellZ);

		int pushed = INNER_RADIUS_CELLS + 1;
		boolean leanX = Math.abs(cellX) >= Math.abs(cellZ);
		int pushedX = leanX ? (cellX < 0 ? -pushed : pushed) : 0;
		int pushedZ = leanX ? 0 : (cellZ < 0 ? -pushed : pushed);
		return anchorForCell(pushedX, pushedZ);
	}
}

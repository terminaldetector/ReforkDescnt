package com.terminaldetector.drmd.world.llod;

/**
 * Long Level of Detail bands.
 *
 * <p>Remote objects exist as coarse voxel silhouettes. Only nearby volume is full blocks.</p>
 */
public enum LlodLevel {
	/** Full Minecraft blocks — player neighbourhood. */
	FULL(0, 256),
	/** Intermediate geometry — simplified solid shells. */
	MEDIUM(256, 2048),
	/** Coarse voxel silhouette — visible for tens of km. */
	SILHOUETTE(2048, 65_536),
	/** Culled / not rendered. */
	NONE(65_536, Double.POSITIVE_INFINITY);

	public final double minDistance;
	public final double maxDistance;

	LlodLevel(double min, double max) {
		this.minDistance = min;
		this.maxDistance = max;
	}

	public static LlodLevel of(double distanceBlocks) {
		if (distanceBlocks < FULL.maxDistance) return FULL;
		if (distanceBlocks < MEDIUM.maxDistance) return MEDIUM;
		if (distanceBlocks < SILHOUETTE.maxDistance) return SILHOUETTE;
		return NONE;
	}
}

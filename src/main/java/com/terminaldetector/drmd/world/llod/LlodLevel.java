package com.terminaldetector.drmd.world.llod;

/**
 * Voxel LLOD pipeline for 6DoF sandbox volume:
 *
 * <pre>
 * LLOD0  — object silhouette (~thousands of voxels)
 *   ↓
 * LLOD1  — large forms (coarse primitives)
 *   ↓
 * LLOD2  — region proxies
 *   ↓
 * CHUNK  — vanilla Minecraft blocks
 * </pre>
 *
 * Near → far: CHUNK → LLOD2 → LLOD1 → LLOD0.
 *
 * <p>Bands are tuned for Minecraft fog/view-distance (not Source-engine km).
 * Previous LLOD0 min of 3072 sat past fog → empty horizon in flight.
 */
public enum LlodLevel {
	/** Vanilla chunk blocks — player neighbourhood. No LLOD draw. */
	CHUNK(0, 96, 0),
	/** Region proxies — few large AABBs / mega-voxels. */
	LLOD2(96, 384, 12),
	/** Large forms — thick structural voxels. */
	LLOD1(384, 1280, 256),
	/** Far silhouette — dense voxel cloud (fog disabled on draw pass). */
	LLOD0(1280, 48_000, 2800),
	/** Beyond draw budget. */
	NONE(48_000, Double.POSITIVE_INFINITY, 0);

	public final double minDistance;
	public final double maxDistance;
	/** Soft cap on voxel cubes drawn for one object at this band. */
	public final int voxelBudget;

	LlodLevel(double min, double max, int voxelBudget) {
		this.minDistance = min;
		this.maxDistance = max;
		this.voxelBudget = voxelBudget;
	}

	public static LlodLevel of(double distanceBlocks) {
		if (distanceBlocks < CHUNK.maxDistance) return CHUNK;
		if (distanceBlocks < LLOD2.maxDistance) return LLOD2;
		if (distanceBlocks < LLOD1.maxDistance) return LLOD1;
		if (distanceBlocks < LLOD0.maxDistance) return LLOD0;
		return NONE;
	}

	/** True when client should draw procedural voxels instead of relying on chunks alone. */
	public boolean drawsVoxels() {
		return this == LLOD0 || this == LLOD1 || this == LLOD2;
	}
}

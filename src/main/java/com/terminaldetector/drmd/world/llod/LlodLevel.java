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
 */
public enum LlodLevel {
	/** Vanilla chunk blocks — player neighbourhood. No LLOD draw. */
	CHUNK(0, 192, 0),
	/** Region proxies — few large AABBs / mega-voxels. */
	LLOD2(192, 768, 8),
	/** Large forms — thick structural voxels. */
	LLOD1(768, 3072, 192),
	/** Far silhouette — dense voxel cloud, up to thousands of cubes. */
	LLOD0(3072, 96_000, 2800),
	/** Beyond draw budget. */
	NONE(96_000, Double.POSITIVE_INFINITY, 0);

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

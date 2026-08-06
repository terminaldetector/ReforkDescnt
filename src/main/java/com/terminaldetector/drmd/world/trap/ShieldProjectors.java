package com.terminaldetector.drmd.world.trap;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Station shield-generator nodes placed with ring defenses.
 * Nearby 6DoF pilots get faster shield regen — not a force-field bubble.
 */
public final class ShieldProjectors {
	private static final Set<BlockPos> NODES = ConcurrentHashMap.newKeySet();
	private static final double RANGE_SQ = 14 * 14;

	private ShieldProjectors() {}

	public static void clear() {
		NODES.clear();
	}

	public static void register(BlockPos pos) {
		NODES.add(pos.toImmutable());
	}

	/** Regen multiplier: 1 = none, up to ~2.2 near a live projector. */
	public static float regenBoost(World world, Vec3d at) {
		if (NODES.isEmpty()) return 1f;
		float best = 1f;
		for (BlockPos p : NODES) {
			if (world != null && !world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
			double d = p.getSquaredDistance(at);
			if (d > RANGE_SQ) continue;
			float t = 1f - (float) (d / RANGE_SQ);
			best = Math.max(best, 1f + 1.2f * t);
		}
		return best;
	}
}

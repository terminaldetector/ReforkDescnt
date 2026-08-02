package com.terminaldetector.drmd.world.scar;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * When an Overworld chunk loads, burn in the scars {@link ScarMapState} remembers for it, so
 * orbital damage exists on the ground when you descend.
 */
public final class ScarApplier {
	private ScarApplier() {}

	public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		ScarMapState map = ScarMapState.get(world);
		if (map.size() == 0) return;
		int minCx = ScarMapState.cellOf(chunk.getPos().getStartX());
		int maxCx = ScarMapState.cellOf(chunk.getPos().getEndX());
		int minCz = ScarMapState.cellOf(chunk.getPos().getStartZ());
		int maxCz = ScarMapState.cellOf(chunk.getPos().getEndZ());
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				if (!map.scarred(cx, cz)) continue;
				scarTerrain(world, cx, cz);
			}
		}
	}

	private static void scarTerrain(ServerWorld world, int cx, int cz) {
		int x0 = cx * ScarMapState.CELL;
		int z0 = cz * ScarMapState.CELL;
		// Shallow crater + scorched ring — readable, not a nuke.
		int mid = ScarMapState.CELL / 2;
		for (int dx = mid - 4; dx <= mid + 4; dx++) {
			for (int dz = mid - 4; dz <= mid + 4; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				int d2 = (dx - mid) * (dx - mid) + (dz - mid) * (dz - mid);
				if (d2 > 20) continue;
				int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos p = new BlockPos(x, y - 1, z);
				if (d2 < 6) {
					world.setBlockState(p, Blocks.MAGMA_BLOCK.getDefaultState());
					world.setBlockState(p.up(), Blocks.AIR.getDefaultState());
					if (d2 < 2) world.setBlockState(p.down(), Blocks.BLACKSTONE.getDefaultState());
				} else {
					world.setBlockState(p, Blocks.COAL_BLOCK.getDefaultState());
				}
			}
		}
	}
}

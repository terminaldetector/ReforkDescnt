package com.terminaldetector.drmd.world.llod.planet;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * When an Overworld chunk loads, apply persistent reactor scars from the planet map
 * so orbital damage exists on the ground when you descend.
 */
public final class PlanetScarApplier {
	private PlanetScarApplier() {}

	public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		PlanetMapState map = PlanetMapState.get(world);
		int minCx = PlanetCell.cellOf(chunk.getPos().getStartX());
		int maxCx = PlanetCell.cellOf(chunk.getPos().getEndX());
		int minCz = PlanetCell.cellOf(chunk.getPos().getStartZ());
		int maxCz = PlanetCell.cellOf(chunk.getPos().getEndZ());
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				PlanetCell cell = map.get(cx, cz);
				if (cell == null || !cell.scarred()) continue;
				scarTerrain(world, cx, cz);
			}
		}
	}

	private static void scarTerrain(ServerWorld world, int cx, int cz) {
		int x0 = cx * PlanetCell.CELL;
		int z0 = cz * PlanetCell.CELL;
		// Shallow crater + scorched ring — readable, not a nuke.
		int mid = PlanetCell.CELL / 2;
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

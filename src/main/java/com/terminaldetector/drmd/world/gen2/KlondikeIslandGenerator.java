package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;

import java.util.UUID;

/**
 * Simple Spark/Klondike-style floating islands — real blocks, placed sparsely.
 *
 * <p>One shape: dirt/stone disk, grass top, short underside. Sparse sky-band placement replaces
 * the old ARCH/RING/FLOATING_CONTINENT silhouette zoo.
 */
public final class KlondikeIslandGenerator {
	private KlondikeIslandGenerator() {}

	public static MacroEntry generate(WorldAccess world, BlockPos origin, Random random) {
		int rx = 8 + random.nextInt(11);
		int rz = 8 + random.nextInt(11);
		int thick = 3 + random.nextInt(4);
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int x = -rx; x <= rx; x++) {
			for (int z = -rz; z <= rz; z++) {
				double nx = x / (double) rx;
				double nz = z / (double) rz;
				double e = nx * nx + nz * nz;
				if (e > 1.05) continue;
				int depth = (int) Math.max(1, thick * (1.0 - e * 0.65));
				for (int y = -depth; y <= 0; y++) {
					m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					if (!inLimit(world, m)) continue;
					if (y == 0) {
						world.setBlockState(m, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else if (y == -1) {
						world.setBlockState(m, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else if (y >= -depth + 1) {
						world.setBlockState(m, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					} else {
						world.setBlockState(m, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				// Occasional tree stump / bush — no excess decoration
				if (e < 0.35 && random.nextInt(28) == 0) {
					m.set(origin.getX() + x, origin.getY() + 1, origin.getZ() + z);
					if (inLimit(world, m)) {
						world.setBlockState(m, Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}

		if (inLimit(world, origin)) {
			world.setBlockState(origin, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
		}

		MacroEntry e = new MacroEntry(UUID.randomUUID(), MacroEntry.Kind.KLONDIKE_ISLAND,
				WorldRules.Layer.SKY_ARCHIPELAGO, origin.toImmutable(),
				rx * 2, thick + 4, rz * 2, 0x4A8F3C, "Klondike Island");
		// Catalogue entry for radar / debug listings; the island itself is the blocks above.
		MacroWorld.put(e);
		return e;
	}

	/** Sky-band Y for sparse islands (below orbital ring vista). */
	public static int placeY(long seed) {
		int span = Math.max(8, WorldLevels.SKY_TOP - WorldLevels.SURFACE_TOP - 40);
		return WorldLevels.SURFACE_TOP + 24 + (int) Math.floorMod(seed, span);
	}

	private static boolean inLimit(WorldAccess world, BlockPos pos) {
		int y = pos.getY();
		return y >= world.getBottomY() && y < world.getBottomY() + world.getHeight();
	}
}

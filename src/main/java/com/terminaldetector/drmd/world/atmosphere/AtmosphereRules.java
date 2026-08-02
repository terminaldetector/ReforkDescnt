package com.terminaldetector.drmd.world.atmosphere;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Lightweight atmospheric rules applied near players / bombs — keep MC recognizable.
 */
public final class AtmosphereRules {
	private AtmosphereRules() {}

	/** Near-space: convert free water sources to vapor particles / air (game rule). */
	public static void tickWaterSuppression(ServerWorld world, BlockPos center, int radius) {
		AtmosphereBand band = AtmosphereBand.at(center.getY());
		if (!band.suppressFreeWater) return;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (world.getBlockState(m).isOf(Blocks.WATER)) {
						world.setBlockState(m, Blocks.AIR.getDefaultState());
						world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
								m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5,
								4, 0.2, 0.2, 0.2, 0.01);
					}
				}
			}
		}
	}

	/** Deep pressure: occasional steam vents near magma. */
	public static void tickDeepPressure(ServerWorld world, BlockPos center) {
		if (AtmosphereBand.at(center.getY()) != AtmosphereBand.DEEP_PRESSURE) return;
		if (world.getRandom().nextInt(40) != 0) return;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int i = 0; i < 8; i++) {
			m.set(center.getX() + world.getRandom().nextInt(9) - 4,
					center.getY() + world.getRandom().nextInt(5) - 2,
					center.getZ() + world.getRandom().nextInt(9) - 4);
			if (world.getBlockState(m).isOf(Blocks.LAVA) || world.getBlockState(m).isOf(Blocks.MAGMA_BLOCK)) {
				BlockPos above = m.up();
				if (world.getBlockState(above).isAir()) {
					world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
							above.getX() + 0.5, above.getY(), above.getZ() + 0.5,
							8, 0.3, 0.5, 0.3, 0.02);
					world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
							above.getX() + 0.5, above.getY() + 0.5, above.getZ() + 0.5,
							6, 0.2, 0.3, 0.2, 0.01);
				}
				return;
			}
		}
	}

	/** Amplify explosion power by band (tunnel shockwaves in deep pressure). */
	public static float scaleBlast(double y, float base) {
		return base * AtmosphereBand.at(y).blastScale;
	}
}

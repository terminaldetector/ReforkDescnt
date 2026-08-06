package com.terminaldetector.drmd.world.atmosphere;

import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lightweight atmospheric rules applied near players / bombs — keep MC recognizable.
 *
 * <p>End dimension and the atmosphere edge (near-space / orbital / End band) treat free
 * fluid as weightless blobs instead of deleting it or letting it fall.
 */
public final class AtmosphereRules {
	private AtmosphereRules() {}

	/** True End vacuum, orbital belt, or practical near-space — no preferred down for fluids. */
	public static boolean isWeightlessFluid(World world, double y) {
		if (world != null && world.getRegistryKey() == World.END) return true;
		WorldLevels.Level level = WorldLevels.at(y);
		if (level == WorldLevels.Level.END || level == WorldLevels.Level.ORBITAL) return true;
		AtmosphereBand band = world != null ? AtmosphereBand.at(world, y) : AtmosphereBand.at(y);
		return band == AtmosphereBand.NEAR_SPACE;
	}

	/**
	 * Near-space / End: do not erase water — float it.
	 * Classic thin-air still vaporizes free sources (old suppress rule).
	 */
	public static void tickWaterSuppression(ServerWorld world, BlockPos center, int radius) {
		AtmosphereBand band = AtmosphereBand.at(world, center.getY());
		boolean weightless = isWeightlessFluid(world, center.getY());
		if (!band.suppressFreeWater && !weightless) return;

		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (!world.getBlockState(m).isOf(Blocks.WATER)
							&& !world.getBlockState(m).isOf(Blocks.LAVA)) continue;

					if (weightless) {
						// Weightless: keep the source, advertise zero-g with particles.
						world.spawnParticles(ParticleTypes.END_ROD,
								m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5,
								1, 0.15, 0.15, 0.15, 0.002);
						world.spawnParticles(ParticleTypes.CLOUD,
								m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5,
								1, 0.1, 0.1, 0.1, 0.005);
					} else {
						// Thin air (non-weightless suppress): vaporize free water only.
						if (world.getBlockState(m).isOf(Blocks.WATER)) {
							world.setBlockState(m, Blocks.AIR.getDefaultState());
							world.spawnParticles(ParticleTypes.CLOUD,
									m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5,
									4, 0.2, 0.2, 0.2, 0.01);
						}
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
					world.spawnParticles(ParticleTypes.LARGE_SMOKE,
							above.getX() + 0.5, above.getY(), above.getZ() + 0.5,
							8, 0.3, 0.5, 0.3, 0.02);
					world.spawnParticles(ParticleTypes.CLOUD,
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

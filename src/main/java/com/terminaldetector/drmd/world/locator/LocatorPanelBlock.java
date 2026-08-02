package com.terminaldetector.drmd.world.locator;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Decorative locator hull panel — faint sparkle on the tower/dish skin. */
public class LocatorPanelBlock extends Block {
	public LocatorPanelBlock(Settings settings) {
		super(settings);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(12) != 0) return;
		world.addParticle(ParticleTypes.ELECTRIC_SPARK,
				pos.getX() + random.nextDouble(),
				pos.getY() + random.nextDouble(),
				pos.getZ() + random.nextDouble(),
				0, 0.01, 0);
	}
}

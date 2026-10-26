package com.terminaldetector.drmd.world.locator;

import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Small locator node — scheduled pulse that boosts 6DoF pilots and marks the dish network.
 */
public class LocatorResonatorBlock extends Block {
	public LocatorResonatorBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected boolean hasRandomTicks(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		pulse(world, pos);
		world.scheduleBlockTick(pos, this, 60 + random.nextInt(40));
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		pulse(world, pos);
		world.scheduleBlockTick(pos, this, 80 + random.nextInt(40));
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient && world instanceof ServerWorld sw) {
			sw.scheduleBlockTick(pos, this, 40);
		}
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.isClient || !(entity instanceof PlayerEntity player)) return;
		if (player.age % 30 != 0) return;
		if (!DescentPlayerData.get(player).isEnabled()) return;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, true, false, true));
		player.sendMessage(Text.literal("§3Resonator link"), true);
	}

	private static void pulse(ServerWorld world, BlockPos pos) {
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
				pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				8, 0.35, 0.4, 0.35, 0.02);
		Box box = new Box(pos).expand(10);
		for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, box, p -> true)) {
			if (!DescentPlayerData.get(player).isEnabled()) continue;
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 50, 0, true, false, true));
		}
	}
}

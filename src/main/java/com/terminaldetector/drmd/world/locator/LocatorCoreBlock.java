package com.terminaldetector.drmd.world.locator;

import com.mojang.serialization.MapCodec;
import com.terminaldetector.drmd.entity.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Mega / primary locator heart — periodic ping, particles, short glow on nearby pilots.
 */
public class LocatorCoreBlock extends BlockWithEntity {
	public static final MapCodec<LocatorCoreBlock> CODEC = createCodec(LocatorCoreBlock::new);

	public LocatorCoreBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new LocatorCoreBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.LOCATOR_CORE, LocatorCoreBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof LocatorCoreBlockEntity core) {
			player.sendMessage(Text.literal("§bLocator Core §7— " + core.describePing(player)), false);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(4) != 0) return;
		world.addParticle(ParticleTypes.END_ROD,
				pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
				pos.getY() + 1.1,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
				0, 0.02, 0);
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient && world instanceof ServerWorld sw) {
			sw.scheduleBlockTick(pos, this, 40);
		}
	}
}

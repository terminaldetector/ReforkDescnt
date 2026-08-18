package com.terminaldetector.drmd.world.portal.mirror;

import com.mojang.serialization.MapCodec;
import com.terminaldetector.drmd.world.portal.PortalComplexity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A block that ricochets laser projectiles ({@link ReflectiveBlock}) and, when Immersive Portals is
 * installed, is also a literal live mirror via an attached {@code qouteall.imm_ptl.core.portal.Mirror}
 * entity (see {@link ImmPtlMirrorBridge}). Placement, breaking, and laser bounce are all pure DRMD
 * physics with no ImmPtl dependency — the live-reflection half degrades honestly (block still exists,
 * still bounces lasers, just isn't a literal live mirror) when ImmPtl is absent at runtime.
 */
public class MirrorBlock extends BlockWithEntity implements ReflectiveBlock {
	public static final DirectionProperty FACING = Properties.FACING;
	public static final MapCodec<MirrorBlock> CODEC = createCodec(MirrorBlock::new);

	public MirrorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getSide());
	}

	@Override
	public Vec3d getReflectionNormal(BlockState state) {
		return MirrorReflection.normalFor(state.get(FACING));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MirrorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return null; // Nothing to tick: an attached Mirror entity just sits and saves with its chunk.
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (world instanceof ServerWorld sw && PortalComplexity.hasImmersivePortals()
				&& world.getBlockEntity(pos) instanceof MirrorBlockEntity be) {
			UUID id = ImmPtlMirrorBridge.attach(sw, pos, state.get(FACING));
			be.setMirrorEntityId(id);
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld sw
				&& PortalComplexity.hasImmersivePortals()
				&& world.getBlockEntity(pos) instanceof MirrorBlockEntity be) {
			ImmPtlMirrorBridge.detach(sw, be.getMirrorEntityId());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}

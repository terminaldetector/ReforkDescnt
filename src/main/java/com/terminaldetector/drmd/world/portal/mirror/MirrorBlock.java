package com.terminaldetector.drmd.world.portal.mirror;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * A block that ricochets laser projectiles ({@link ReflectiveBlock}) and, when Immersive Portals is
 * installed, is also a literal live mirror — see {@code ImmPtlMirrorBridge} and {@code MirrorBlockEntity}
 * for that half, added in a later phase. This class alone works standalone with no ImmPtl dependency
 * at all: placement, breaking, and laser bounce are all pure DRMD physics.
 */
public class MirrorBlock extends Block implements ReflectiveBlock {
	public static final DirectionProperty FACING = Properties.FACING;

	public MirrorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
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
}

package com.terminaldetector.drmd.world.portal.mirror;

import com.mojang.serialization.MapCodec;
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

/** Native reflective block; saved external attachments are retired without loading their API. */
public class MirrorBlock extends BlockWithEntity implements ReflectiveBlock {
	public static final DirectionProperty FACING = Properties.FACING;
	public static final MapCodec<MirrorBlock> CODEC = createCodec(MirrorBlock::new);

	public MirrorBlock(Settings settings) {
		super(settings);
		setDefaultState(disconnected(getStateManager().getDefaultState().with(FACING, Direction.NORTH)));
	}

    private static BlockState disconnected(BlockState state) {
        for (var property : MirrorEdges.FRAME) state=state.with(property,true);
        return state;
    }
    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbour,
            net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighbourPos) {
        return MirrorEdges.update(state,world,pos);
    }

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected net.minecraft.block.BlockRenderType getRenderType(BlockState state) {
		return net.minecraft.block.BlockRenderType.MODEL;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
        builder.add(MirrorEdges.FRAME);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return MirrorEdges.update(getDefaultState().with(FACING, ctx.getSide()), ctx.getWorld(), ctx.getBlockPos());
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
		return world.isClient ? null : validateTicker(type,
                com.terminaldetector.drmd.entity.ModBlockEntities.MIRROR,
                (w,p,s,be) -> {LegacyPortalEntities.detach((ServerWorld)w, be.getMirrorEntityId());be.refreshConnections();});
	}

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if(!state.isOf(next.getBlock()) && world instanceof ServerWorld server
                && world.getBlockEntity(pos) instanceof MirrorBlockEntity be)
            LegacyPortalEntities.detach(server,be.getMirrorEntityId());
        super.onStateReplaced(state,world,pos,next,moved);
    }
}

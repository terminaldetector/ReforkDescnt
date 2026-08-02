package com.terminaldetector.drmd.world.gravity;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Gravity Generator — technological local-gravity source (radius / direction / shape / power).
 */
public class GravityGeneratorBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.FACING;
	public static final MapCodec<GravityGeneratorBlock> CODEC = createCodec(GravityGeneratorBlock::new);

	public GravityGeneratorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.DOWN));
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
		return getDefaultState().with(FACING, ctx.getPlayerLookDirection().getOpposite());
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new GravityGeneratorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type,
				com.terminaldetector.drmd.entity.ModBlockEntities.GRAVITY_GENERATOR,
				GravityGeneratorBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof GravityGeneratorBlockEntity gen) {
			if (player.isSneaking()) {
				gen.cycleShape();
				player.sendMessage(Text.literal("§aGravity field shape: §f" + gen.getShape()), true);
			} else {
				gen.cyclePower();
				player.sendMessage(Text.literal(String.format("§aGravity power: §f%.1f  radius=%.0f",
						gen.getPower(), gen.getRadius())), true);
			}
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof GravityGeneratorBlockEntity gen) {
			gen.unregister();
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}

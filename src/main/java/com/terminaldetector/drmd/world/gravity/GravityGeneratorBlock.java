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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Gravity Generator — same local-gravity rules as {@link GravityTorchBlock}, larger radius.
 * Mount face becomes the floor; half-space clip; players + mobs via shared gravity systems.
 */
public class GravityGeneratorBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.FACING;
	public static final MapCodec<GravityGeneratorBlock> CODEC = createCodec(GravityGeneratorBlock::new);
	/** Default reach — torch is 8; generator covers a room. */
	public static final float DEFAULT_RADIUS = 24f;
	public static final float DEFAULT_POWER = 1.0f;

	public GravityGeneratorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.UP));
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
		// Same as torch: clicked face becomes the floor.
		return getDefaultState().with(FACING, ctx.getSide());
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
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient && world.getBlockEntity(pos) instanceof GravityGeneratorBlockEntity gen) {
			gen.registerField();
			world.scheduleBlockTick(pos, this, 10);
		}
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (world.getBlockEntity(pos) instanceof GravityGeneratorBlockEntity gen) {
			gen.registerField();
		}
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.15f, 0.85f, 1.0f), 1.1f),
				pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0.01);
		world.scheduleBlockTick(pos, this, 10);
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

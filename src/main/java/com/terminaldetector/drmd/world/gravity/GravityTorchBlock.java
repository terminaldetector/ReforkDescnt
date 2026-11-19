package com.terminaldetector.drmd.world.gravity;

import com.mojang.serialization.MapCodec;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Gravity Torch — compact local gravity source (radius {@link #RADIUS}).
 * Mount face becomes the floor: FACING points out from the surface, gravity pulls into it.
 * Does not affect Pyro GX. Mobs are handled by {@link EntityGravitySystem}; players by
 * {@link FootGravitySystem} + {@link GravityMount}.
 */
public class GravityTorchBlock extends Block {
	public static final DirectionProperty FACING = Properties.FACING;
	public static final MapCodec<GravityTorchBlock> CODEC = createCodec(GravityTorchBlock::new);
	public static final float RADIUS = 8f;
	public static final float POWER = 0.85f;

	public GravityTorchBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// Stem points away from the clicked face → that face is the floor (UP = FACING)
		return getDefaultState().with(FACING, ctx.getSide());
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.cuboid(0.35, 0.0, 0.35, 0.65, 0.7, 0.65);
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		register(world, pos, state);
		if (!world.isClient) {
			world.scheduleBlockTick(pos, this, 10);
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (world instanceof ServerWorld server) {
            if (!state.isOf(newState.getBlock())) GravityTorchFields.remove(server, pos);
            else if (state.get(FACING) != newState.get(FACING)) register(server, pos, newState);
        }
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected boolean hasRandomTicks(BlockState state) {
		return true;
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		register(world, pos, state);
		pulseParticles(world, pos);
		world.scheduleBlockTick(pos, this, 10);
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		register(world, pos, state);
		pulseParticles(world, pos);
		world.scheduleBlockTick(pos, this, 10);
	}

	/** Visual source cue only; player feedback belongs to the actual transition, never a torch scan. */
    private static void pulseParticles(ServerWorld world, BlockPos pos) {
        world.spawnParticles(new DustParticleEffect(new Vector3f(0.2f, 1f, 0.45f), 1.0f),
            pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 3, 0.15, 0.15, 0.15, 0.01);
    }

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.isClient) return;
		if (entity instanceof PyroShipEntity) return;
		if (entity.getVehicle() instanceof PyroShipEntity) return;
		if (!(entity instanceof PlayerEntity p)) return;
		if (p instanceof ServerPlayerEntity sp
				&& com.terminaldetector.drmd.DescentPlayerData.get(sp).isEnabled()) {
			return;
		}
		register(world, pos, state);
	}

	static void register(World world, BlockPos pos, BlockState state) {
        // Client chunk callbacks share a JVM with an integrated server. They must never mutate
        // its authoritative catalogue, including on a same-coordinate client unload.
        if (world instanceof ServerWorld server) GravityTorchFields.publish(server, pos, state);
    }
}

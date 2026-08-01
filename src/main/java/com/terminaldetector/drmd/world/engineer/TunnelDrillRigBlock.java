package com.terminaldetector.drmd.world.engineer;

import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Horizontal tunnel drill rig — redstone-powered bore that advances a walkable tunnel
 * along its facing. Progress is stored in blockstate so reloads resume the cut.
 */
public class TunnelDrillRigBlock extends Block {
	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
	public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

	private static final int MAX_LENGTH = 64;
	private static final int PULSE_TICKS = 8;
	private static final int TUNNEL_RADIUS = 2;

	public TunnelDrillRigBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState()
				.with(ACTIVE, false)
				.with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE, FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState()
				.with(FACING, ctx.getHorizontalPlayerFacing())
				.with(ACTIVE, ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos()));
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient) {
			world.setBlockState(pos, state.with(ACTIVE, world.isReceivingRedstonePower(pos)), Block.NOTIFY_ALL);
			world.scheduleBlockTick(pos, this, PULSE_TICKS);
		}
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
								  BlockPos sourcePos, boolean notify) {
		if (world.isClient) return;
		boolean powered = world.isReceivingRedstonePower(pos);
		if (powered != state.get(ACTIVE)) {
			world.setBlockState(pos, state.with(ACTIVE, powered), Block.NOTIFY_ALL);
			if (powered) world.scheduleBlockTick(pos, this, PULSE_TICKS);
		}
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		world.scheduleBlockTick(pos, this, PULSE_TICKS);
		if (!state.get(ACTIVE)) return;

		Direction face = state.get(FACING);
		// Scan ahead for the next solid face within MAX_LENGTH.
		BlockPos cut = null;
		for (int d = 1; d <= MAX_LENGTH; d++) {
			BlockPos probe = pos.offset(face, d);
			if (world.isOutOfHeightLimit(probe)) break;
			BlockState st = world.getBlockState(probe);
			if (st.isAir() || !st.getFluidState().isEmpty()) continue;
			if (st.isOf(Blocks.BEDROCK) || st.getHardness(world, probe) < 0) break;
			cut = probe;
			break;
		}
		if (cut == null) {
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5 + face.getOffsetX() * 0.6,
					pos.getY() + 0.5,
					pos.getZ() + 0.5 + face.getOffsetZ() * 0.6,
					4, 0.1, 0.1, 0.1, 0.0);
			return;
		}

		Vec3d from = Vec3d.ofCenter(pos).add(face.getOffsetX() * 0.55, 0, face.getOffsetZ() * 0.55);
		Vec3d to = Vec3d.ofCenter(cut);
		WeaponFx.beamDrill(world, from, to, true);
		WeaponFx.drillTunnel(world, cut, Vec3d.of(face.getVector()), null, TUNNEL_RADIUS, 2);

		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.5f, 0.45f);
		SmokeSystem.emit(to, SmokeSystem.Source.INDUSTRIAL, 2.0f, 0.6f, 100);
		SmokeSystem.emit(from, SmokeSystem.Source.ENGINE, 0.8f, 0.3f, 40);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, to.x, to.y, to.z, 8, 0.6, 0.5, 0.6, 0.02);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		Direction face = state.get(FACING);
		double fx = pos.getX() + 0.5 + face.getOffsetX() * 0.55;
		double fz = pos.getZ() + 0.5 + face.getOffsetZ() * 0.55;
		if (!state.get(ACTIVE)) {
			if (random.nextInt(6) == 0) {
				world.addParticle(ParticleTypes.SMOKE, fx, pos.getY() + 0.7, fz, 0, 0.02, 0);
			}
			return;
		}
		world.addParticle(ParticleTypes.FLAME, fx, pos.getY() + 0.5, fz,
				face.getOffsetX() * 0.08, 0, face.getOffsetZ() * 0.08);
		world.addParticle(ParticleTypes.LARGE_SMOKE,
				pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.05, 0);
		world.addParticle(new DustParticleEffect(new Vector3f(1f, 0.25f, 0.05f), 1.4f),
				fx, pos.getY() + 0.5, fz, face.getOffsetX() * 0.15, 0, face.getOffsetZ() * 0.15);
	}
}

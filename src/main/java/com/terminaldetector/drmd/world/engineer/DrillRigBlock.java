package com.terminaldetector.drmd.world.engineer;

import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Laser drill rig — a redstone-powered mining emplacement.
 *
 * <p>Sinks a beam straight down, melting one layer at a time and dropping what it cuts. Depth is
 * kept in blockstate so a rig resumes where it left off across reloads, and it stops at bedrock or
 * at the bottom of the column rather than running forever.
 *
 * <p>Shares the engineer laser's FX and the {@link WeaponFx#melt} block pipeline, so its smoke and
 * heat behave like every other DRMD cutting tool.
 */
public class DrillRigBlock extends Block {
	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
	/** Layers already cut, 0..15; the rig re-seeks past that on each pulse. */
	public static final IntProperty DEPTH = IntProperty.of("depth", 0, 15);

	private static final int MAX_DEPTH = 64;
	private static final int PULSE_TICKS = 12;

	public DrillRigBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false).with(DEPTH, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE, DEPTH);
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

		BlockPos target = findCutFace(world, pos);
		if (target == null) {
			// Nothing left to bite into — idle with a short pilot beam.
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5, pos.getY() - 0.2, pos.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
			return;
		}

		Vec3d from = Vec3d.ofCenter(pos).add(0, -0.4, 0);
		Vec3d to = Vec3d.ofCenter(target);
		WeaponFx.beamDrill(world, from, to);
		// Break outright rather than going through melt(): melt's staged path turns stone into
		// cobble, which the rig would then find again next pulse and never sink past.
		world.breakBlock(target, true);
		// Heat wash on the shaft walls, which melt() is exactly right for.
		for (Direction side : Direction.Type.HORIZONTAL) {
			if (random.nextInt(3) == 0) WeaponFx.melt(world, target.offset(side), 1, null);
		}
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.35f, 0.6f);
		com.terminaldetector.drmd.world.smoke.SmokeSystem.emit(to,
				com.terminaldetector.drmd.world.smoke.SmokeSystem.Source.INDUSTRIAL, 1.0f, 0.35f, 60);

		int depth = Math.min(15, pos.getY() - target.getY());
		if (depth != state.get(DEPTH)) {
			world.setBlockState(pos, state.with(DEPTH, depth), Block.NOTIFY_LISTENERS);
		}
	}

	/** First cuttable block under the rig, or null when the shaft is finished. */
	private static BlockPos findCutFace(ServerWorld world, BlockPos rig) {
		BlockPos.Mutable probe = new BlockPos.Mutable();
		for (int d = 1; d <= MAX_DEPTH; d++) {
			probe.set(rig.getX(), rig.getY() - d, rig.getZ());
			if (world.isOutOfHeightLimit(probe)) return null;
			BlockState st = world.getBlockState(probe);
			if (st.isAir()) continue;
			if (st.isOf(Blocks.BEDROCK)) return null;
			float hardness = st.getHardness(world, probe);
			if (hardness < 0) return null;
			if (!st.getFluidState().isEmpty()) continue;
			return probe.toImmutable();
		}
		return null;
	}

	// Block declares this one public, unlike its other protected callbacks.
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!state.get(ACTIVE)) return;
		world.addParticle(ParticleTypes.FLAME,
				pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
				pos.getY() - 0.1,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
				0, -0.05, 0);
	}

	@Override
	protected boolean emitsRedstonePower(BlockState state) {
		return false;
	}

	/** Rigs read the block below them, so keep the head clear on placement. */
	public static boolean canOperate(World world, BlockPos pos) {
		return !world.getBlockState(pos.offset(Direction.DOWN)).isOf(Blocks.BEDROCK);
	}
}

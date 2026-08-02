package com.terminaldetector.drmd.world.engineer;

import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.DustParticleEffect;
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
import org.joml.Vector3f;

/**
 * Laser drill rig — redstone-powered mining emplacement.
 * Sinks a wide (3×3) shaft straight down with melt wash on the walls and industrial smoke.
 */
public class DrillRigBlock extends Block {
	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
	/** Layers already cut, 0..15; the rig re-seeks past that on each pulse. */
	public static final IntProperty DEPTH = IntProperty.of("depth", 0, 15);

	private static final int MAX_DEPTH = 80;
	private static final int PULSE_TICKS = 10;
	/** Half-width of the shaft (1 → 3×3). */
	private static final int SHAFT_RADIUS = 1;

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
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5, pos.getY() - 0.2, pos.getZ() + 0.5, 3, 0.15, 0.1, 0.15, 0.0);
			return;
		}

		Vec3d from = Vec3d.ofCenter(pos).add(0, -0.45, 0);
		Vec3d to = Vec3d.ofCenter(target);
		WeaponFx.beamDrill(world, from, to, true);

		// Wide shaft: clear the cut layer in a 3×3 disc.
		int carved = 0;
		BlockPos.Mutable probe = new BlockPos.Mutable();
		for (int dx = -SHAFT_RADIUS; dx <= SHAFT_RADIUS; dx++) {
			for (int dz = -SHAFT_RADIUS; dz <= SHAFT_RADIUS; dz++) {
				if (dx * dx + dz * dz > SHAFT_RADIUS * SHAFT_RADIUS + 1) continue;
				probe.set(target.getX() + dx, target.getY(), target.getZ() + dz);
				BlockState st = world.getBlockState(probe);
				float h = st.getHardness(world, probe);
				if (st.isAir() || h < 0 || st.isOf(Blocks.BEDROCK)) continue;
				world.breakBlock(probe.toImmutable(), true);
				carved++;
			}
		}
		// Heat wash on the outer ring.
		for (int dx = -SHAFT_RADIUS - 1; dx <= SHAFT_RADIUS + 1; dx++) {
			for (int dz = -SHAFT_RADIUS - 1; dz <= SHAFT_RADIUS + 1; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 <= SHAFT_RADIUS * SHAFT_RADIUS || d2 > (SHAFT_RADIUS + 1) * (SHAFT_RADIUS + 1) + 1) continue;
				if (random.nextInt(2) == 0) {
					WeaponFx.melt(world, target.add(dx, 0, dz), 2, null);
				}
			}
		}

		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.45f, 0.55f);
		SmokeSystem.emit(to, SmokeSystem.Source.INDUSTRIAL, 1.8f, 0.55f, 90);
		SmokeSystem.emit(from, SmokeSystem.Source.ENGINE, 0.6f, 0.25f, 35);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, to.x, to.y, to.z, 6, 0.5, 0.2, 0.5, 0.02);
		world.spawnParticles(ParticleTypes.LAVA, to.x, to.y, to.z, 5, 0.4, 0.15, 0.4, 0.02);

		int depth = Math.min(15, pos.getY() - target.getY());
		if (depth != state.get(DEPTH) || carved > 0) {
			world.setBlockState(pos, state.with(DEPTH, depth), Block.NOTIFY_LISTENERS);
		}
	}

	/** First cuttable block under the rig centre column, or null when the shaft is finished. */
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

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!state.get(ACTIVE)) {
			if (random.nextInt(8) == 0) {
				world.addParticle(ParticleTypes.SMOKE,
						pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
						0, 0.02, 0);
			}
			return;
		}
		world.addParticle(ParticleTypes.FLAME,
				pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.35,
				pos.getY() - 0.05,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.35,
				0, -0.08, 0);
		world.addParticle(ParticleTypes.LARGE_SMOKE,
				pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2,
				pos.getY() + 0.9,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2,
				0, 0.04, 0);
		world.addParticle(new DustParticleEffect(new Vector3f(1f, 0.4f, 0.1f), 1.2f),
				pos.getX() + 0.5, pos.getY() - 0.2, pos.getZ() + 0.5,
				0, -0.1, 0);
	}

	@Override
	protected boolean emitsRedstonePower(BlockState state) {
		return false;
	}

	public static boolean canOperate(World world, BlockPos pos) {
		return !world.getBlockState(pos.offset(Direction.DOWN)).isOf(Blocks.BEDROCK);
	}
}

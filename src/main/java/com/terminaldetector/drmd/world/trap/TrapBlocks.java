package com.terminaldetector.drmd.world.trap;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Navigation-centric traps for Industrial Underground:
 * hermetic gates, laser barriers, volume turrets, magnetic anomalies.
 */
public final class TrapBlocks {
	private TrapBlocks() {}

	/** Closing hermetic blast door — toggles solid/open on schedule. */
	public static class HermeticGateBlock extends Block {
		public static final BooleanProperty OPEN = BooleanProperty.of("open");
		public static final IntProperty CYCLE = IntProperty.of("cycle", 0, 15);

		public HermeticGateBlock(Settings settings) {
			super(settings);
			setDefaultState(getStateManager().getDefaultState().with(OPEN, true).with(CYCLE, 0));
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
			b.add(OPEN, CYCLE);
		}

		@Override
		protected boolean hasRandomTicks(BlockState state) { return true; }

		@Override
		protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			boolean open = !state.get(OPEN);
			world.setBlockState(pos, state.with(OPEN, open).with(CYCLE, (state.get(CYCLE) + 1) & 15), Block.NOTIFY_ALL);
			// Expand gate plane
			Direction.Axis axis = Direction.Axis.X;
			for (int i = -2; i <= 2; i++) {
				for (int j = -2; j <= 2; j++) {
					BlockPos p = pos.add(0, i, j);
					if (p.equals(pos)) continue;
					if (open) world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					else if (world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.IRON_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
	}

	/** Laser barrier — damages entities crossing the volume along a line. */
	public static class LaserBarrierBlock extends Block {
		public LaserBarrierBlock(Settings settings) {
			super(settings.nonOpaque().noCollision());
		}

		@Override
		protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
			if (!world.isClient && entity instanceof LivingEntity living) {
				living.damage(world.getDamageSources().magic(), 4f);
				if (world instanceof ServerWorld sw) {
					sw.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
							4, 0.2, 0.2, 0.2, 0.01);
				}
			}
		}

		@Override
		protected boolean hasRandomTicks(BlockState state) { return true; }

		@Override
		protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			// Extend laser beam through air along local look of placement (Y axis default)
			for (int i = 1; i <= 8; i++) {
				BlockPos p = pos.up(i);
				if (!world.getBlockState(p).isAir()) break;
				Box box = new Box(p);
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
					e.damage(world.getDamageSources().magic(), 3f);
				}
				world.spawnParticles(ParticleTypes.CRIT, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0, 0, 0, 0);
			}
		}
	}

	/** Volume turret — periodically damages nearest flyer in a spherical radius. */
	public static class VolumeTurretBlock extends Block {
		public VolumeTurretBlock(Settings settings) {
			super(settings);
		}

		@Override
		protected boolean hasRandomTicks(BlockState state) { return true; }

		@Override
		protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			Box volume = new Box(pos).expand(12);
			LivingEntity target = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, volume, LivingEntity::isAlive)) {
				double d = e.squaredDistanceTo(Vec3d.ofCenter(pos));
				if (d < best) { best = d; target = e; }
			}
			if (target != null) {
				target.damage(world.getDamageSources().magic(), 6f);
				world.spawnParticles(ParticleTypes.FLAME,
						target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
						8, 0.2, 0.2, 0.2, 0.02);
			}
		}
	}

	/**
	 * Magnetic anomaly — redefines local UP for nearby 6DoF players.
	 * Navigation hazard: orientation flips, not just damage.
	 */
	public static class MagneticAnomalyBlock extends Block {
		public MagneticAnomalyBlock(Settings settings) {
			super(settings.luminance(s -> 8));
		}

		@Override
		protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
			if (world.isClient || !(entity instanceof PlayerEntity player)) return;
			DescentPlayerData data = DescentPlayerData.get(player);
			if (!data.isEnabled()) return;
			// Pull local up toward vector from player to block (attract) or away (repel)
			Vec3d to = Vec3d.ofCenter(pos).subtract(player.getPos()).normalize();
			LocalOrientation.setUp(player.getUuid(), to);
			player.addVelocity(to.x * 0.15, to.y * 0.15, to.z * 0.15);
			player.velocityModified = true;
		}

		@Override
		protected boolean hasRandomTicks(BlockState state) { return true; }

		@Override
		protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			Box volume = new Box(pos).expand(8);
			Direction dir = Direction.random(random);
			for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, volume, PlayerEntity::isAlive)) {
				if (DescentPlayerData.get(p).isEnabled()) {
					LocalOrientation.setFromDirection(p.getUuid(), dir);
					world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
							p.getX(), p.getY(), p.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
				}
			}
		}
	}

	/** Unstable reactor zone — periodic knockback + light damage in volume. */
	public static class UnstableReactorBlock extends Block {
		public UnstableReactorBlock(Settings settings) {
			super(settings.luminance(s -> 12));
		}

		@Override
		protected boolean hasRandomTicks(BlockState state) { return true; }

		@Override
		protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			Box volume = new Box(pos).expand(6);
			world.spawnParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
			com.terminaldetector.drmd.world.smoke.SmokeSystem.emit(
					Vec3d.ofCenter(pos), com.terminaldetector.drmd.world.smoke.SmokeSystem.Source.REACTOR, 2.5f, 0.85f, 100);
			if (random.nextInt(3) == 0) {
				com.terminaldetector.drmd.world.fire.FireSystem.igniteBlast(world, pos, 4, 3);
			}
			world.spawnParticles(ParticleTypes.LARGE_SMOKE,
					pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 1.5, 1.0, 1.5, 0.02);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, volume, LivingEntity::isAlive)) {
				e.damage(world.getDamageSources().explosion(null, null), 8f);
				Vec3d push = e.getPos().subtract(Vec3d.ofCenter(pos)).normalize().multiply(1.2);
				e.addVelocity(push.x, push.y, push.z);
				e.velocityModified = true;
			}
			// Emergency lighting cue — redstone lamp flash nearby
			for (BlockPos p : BlockPos.iterate(pos.add(-4, -2, -4), pos.add(4, 4, 4))) {
				if (world.getBlockState(p).isOf(Blocks.REDSTONE_LAMP) && random.nextBoolean()) {
					world.spawnParticles(ParticleTypes.FLAME,
							p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.01);
				}
			}
		}
	}
}

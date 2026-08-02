package com.terminaldetector.drmd.world.trap;

import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.end.EndReactorBossEntity;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.joml.Vector3f;

/**
 * Active defense turrets — scheduled ticks for reliable RoF; prioritize intruders (players).
 * Skips Pyro GX and base allies (giga-reactor / keepers).
 */
public final class DefenseTurrets {
	private DefenseTurrets() {}

	public abstract static class BaseTurretBlock extends Block {
		protected final float damage;
		protected final double range;
		protected final Vector3f beamColor;
		protected final DamageClass dmgClass;
		protected final int interval;

		protected BaseTurretBlock(Settings settings, float damage, double range, Vector3f beamColor,
								  DamageClass dmgClass, int intervalTicks) {
			super(settings);
			this.damage = damage;
			this.range = range;
			this.beamColor = beamColor;
			this.dmgClass = dmgClass;
			this.interval = intervalTicks;
		}

		@Override
		protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
			super.onBlockAdded(state, world, pos, oldState, notify);
			if (!world.isClient) world.scheduleBlockTick(pos, this, interval);
		}

		@Override
		protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			fire(world, pos);
			world.scheduleBlockTick(pos, this, interval);
		}

		@Override
		protected boolean hasRandomTicks(BlockState state) { return true; }

		@Override
		protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			// Backup if schedule was lost (chunk reload edge cases)
			fire(world, pos);
			world.scheduleBlockTick(pos, this, interval);
		}

		protected void fire(ServerWorld world, BlockPos pos) {
			LivingEntity target = pickTarget(world, pos);
			if (target == null) return;
			Vec3d from = Vec3d.ofCenter(pos).add(0, 0.6, 0);
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
			WeaponFx.beam(world, from, to, beamColor, 1.05f);
			target.damage(world.getDamageSources().magic(), damage);
			onHit(world, pos, target);
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.35f, pitch());
		}

		protected void onHit(ServerWorld world, BlockPos pos, LivingEntity target) {}

		protected float pitch() { return 1.4f; }

		protected LivingEntity pickTarget(ServerWorld world, BlockPos pos) {
			Box box = new Box(pos).expand(range);
			LivingEntity bestPlayer = null;
			LivingEntity bestOther = null;
			double bestP = Double.MAX_VALUE, bestO = Double.MAX_VALUE;
			Vec3d origin = Vec3d.ofCenter(pos);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, this::isValidTarget)) {
				double d = e.squaredDistanceTo(origin);
				if (e instanceof PlayerEntity) {
					if (d < bestP) { bestP = d; bestPlayer = e; }
				} else if (d < bestO) {
					bestO = d;
					bestOther = e;
				}
			}
			return bestPlayer != null ? bestPlayer : bestOther;
		}

		protected boolean isValidTarget(LivingEntity e) {
			if (!e.isAlive()) return false;
			if (e instanceof PyroShipEntity) return false;
			if (e instanceof EndReactorBossEntity) return false;
			if (e instanceof ReactorKeeperEntity) return false;
			if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return false;
			return true;
		}

		@Override
		protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
			return true;
		}
	}

	/** Long green laser — melts soft blocks on hit. */
	public static class LaserTurretBlock extends BaseTurretBlock {
		public LaserTurretBlock(Settings settings) {
			super(settings, 7f, 18, new Vector3f(0.3f, 1f, 0.45f), DamageClass.ENERGY, 12);
		}

		@Override
		protected void onHit(ServerWorld world, BlockPos pos, LivingEntity target) {
			WeaponFx.melt(world, target.getBlockPos(), 1, target);
			world.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1, target.getZ(),
					4, 0.2, 0.3, 0.2, 0.01);
		}
	}

	/** Purple plasma bolts — splash. */
	public static class PlasmaTurretBlock extends BaseTurretBlock {
		public PlasmaTurretBlock(Settings settings) {
			super(settings, 10f, 14, new Vector3f(0.7f, 0.3f, 1f), DamageClass.EXOTIC, 16);
		}

		@Override
		protected void onHit(ServerWorld world, BlockPos pos, LivingEntity target) {
			WeaponCore.splashDamage(target, world, target.getPos(), 8f, 2.5f, DamageClass.EXOTIC);
			world.spawnParticles(ParticleTypes.DRAGON_BREATH, target.getX(), target.getY() + 1, target.getZ(),
					8, 0.4, 0.4, 0.4, 0.02);
		}

		@Override
		protected float pitch() { return 0.85f; }
	}

	/** Fast amber point-defense — high RoF. */
	public static class PointDefenseTurretBlock extends BaseTurretBlock {
		public PointDefenseTurretBlock(Settings settings) {
			super(settings, 4f, 12, new Vector3f(1f, 0.7f, 0.2f), DamageClass.KINETIC, 6);
		}

		@Override
		protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
			fire(world, pos);
			if (random.nextBoolean()) fire(world, pos);
			world.scheduleBlockTick(pos, this, interval);
		}

		@Override
		protected void onHit(ServerWorld world, BlockPos pos, LivingEntity target) {
			world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(),
					6, 0.2, 0.2, 0.2, 0.05);
		}

		@Override
		protected float pitch() { return 1.9f; }
	}
}

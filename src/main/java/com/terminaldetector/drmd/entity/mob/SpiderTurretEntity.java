package com.terminaldetector.drmd.entity.mob;

import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Spider turret — a walking emplacement on four legs.
 *
 * <p>Repositions slowly while it has no line of sight, then plants itself and fights from the spot.
 * Two mounts share one head: a kinetic machine gun for close work in three-round bursts, and a
 * laser for anything past its burst range. The head tracks independently of the chassis, so it
 * keeps shooting while the legs are still turning.
 */
public class SpiderTurretEntity extends CyberMobEntity {
	private static final TrackedData<Float> HEAD_YAW =
			DataTracker.registerData(SpiderTurretEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> HEAD_PITCH =
			DataTracker.registerData(SpiderTurretEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> DEPLOYED =
			DataTracker.registerData(SpiderTurretEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	private static final float MG_COST = 4f;
	private static final float LASER_COST = 18f;
	/** Past this the MG is out of its useful envelope and the laser takes over. */
	private static final double MG_RANGE = 18.0;

	private float mgCooldown;
	private int burstLeft;
	private float laserCooldown;

	public SpiderTurretEntity(EntityType<? extends SpiderTurretEntity> type, World world) {
		super(type, world);
		configure(50f, 80f, 2.5f, 0.35f, 0.05f, 0.15f, 110f, 20f);
	}

	public static DefaultAttributeContainer.Builder createSpiderTurretAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 220)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6)
				.add(EntityAttributes.GENERIC_ARMOR, 6)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 52)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.7)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.2);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(HEAD_YAW, 0f);
		builder.add(HEAD_PITCH, 0f);
		builder.add(DEPLOYED, false);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new TurretEngageGoal(this));
		goalSelector.add(7, new WanderAroundFarGoal(this, 0.6));
		targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	public float getHeadYaw2() { return dataTracker.get(HEAD_YAW); }
	public float getHeadPitch2() { return dataTracker.get(HEAD_PITCH); }
	public boolean isDeployed() { return dataTracker.get(DEPLOYED); }

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;
		float dt = 1f / 20f;
		if (mgCooldown > 0) mgCooldown -= dt;
		if (laserCooldown > 0) laserCooldown -= dt;
		if (burstLeft > 0 && mgCooldown <= 0) {
			LivingEntity target = getTarget();
			if (target != null && target.isAlive()) {
				fireMachineGun(target);
			} else {
				burstLeft = 0;
			}
		}
	}

	/** Aim the head at a world point; the chassis is free to lag behind. */
	private void aimHead(LivingEntity target, float dt) {
		Vec3d from = muzzle(0.0, 0.85);
		Vec3d dir = aimAt(from, target);
		float yaw = FlightAttitude.approachAngle(getHeadYaw2(), FlightAttitude.yawOf(dir), 12f, dt);
		float pitch = FlightAttitude.approachAngle(getHeadPitch2(), FlightAttitude.pitchOf(dir), 12f, dt);
		dataTracker.set(HEAD_YAW, yaw);
		dataTracker.set(HEAD_PITCH, pitch);
	}

	private void beginBurst() {
		if (burstLeft > 0 || mgCooldown > 0) return;
		burstLeft = 3;
	}

	private void fireMachineGun(LivingEntity target) {
		if (!drawEnergy(MG_COST)) {
			burstLeft = 0;
			mgCooldown = 1.2f;
			return;
		}
		burstLeft--;
		mgCooldown = burstLeft > 0 ? 0.09f : 1.35f;
		Vec3d from = muzzle(0.75, 0.85);
		Vec3d dir = aimAt(from, target);
		// Slight cone so a burst walks across the target instead of stacking on one pixel.
		dir = dir.add(
				(random.nextDouble() - 0.5) * 0.045,
				(random.nextDouble() - 0.5) * 0.045,
				(random.nextDouble() - 0.5) * 0.045).normalize();
		WeaponCore.FireConfig cfg = shot(from, dir, 5200f, 7f, DamageClass.KINETIC, 2.5f);
		cfg.colorR = 255; cfg.colorG = 214; cfg.colorB = 120;
		cfg.visualScale = 0.7f;
		fire(cfg);
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT,
				SoundCategory.HOSTILE, 0.45f, 1.9f);
		if (getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.CRIT, from.x, from.y, from.z, 2, 0.05, 0.05, 0.05, 0.02);
		}
	}

	private void fireLaser(LivingEntity target) {
		if (laserCooldown > 0 || !drawEnergy(LASER_COST)) return;
		laserCooldown = 1.9f;
		Vec3d from = muzzle(0.7, 0.9);
		Vec3d dir = aimAt(from, target);
		WeaponCore.FireConfig cfg = shot(from, dir, 8000f, 20f, DamageClass.ENERGY, 1.6f);
		cfg.colorR = 90; cfg.colorG = 255; cfg.colorB = 255;
		cfg.pierceCount = 1;
		fire(cfg);
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE,
				SoundCategory.HOSTILE, 0.5f, 2.0f);
	}

	/** Reposition without sight, plant and fight with it. */
	private static class TurretEngageGoal extends Goal {
		private final SpiderTurretEntity turret;

		TurretEngageGoal(SpiderTurretEntity turret) {
			this.turret = turret;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity t = turret.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void stop() {
			turret.getNavigation().stop();
			turret.dataTracker.set(DEPLOYED, false);
		}

		@Override
		public void tick() {
			LivingEntity target = turret.getTarget();
			if (target == null) return;
			float dt = 1f / 20f;
			double dist = turret.distanceTo(target);
			boolean sight = turret.canSee(target);

			turret.aimHead(target, dt);
			// Legs still swing round to face, just far more slowly than the head.
			Vec3d flat = target.getPos().subtract(turret.getPos());
			FlightAttitude.steer(turret, new Vec3d(flat.x, 0, flat.z), 2.5f, dt);

			boolean deployed = sight && dist < 40;
			turret.dataTracker.set(DEPLOYED, deployed);
			if (deployed) {
				turret.getNavigation().stop();
				if (dist <= MG_RANGE) {
					turret.beginBurst();
				} else {
					turret.fireLaser(target);
				}
			} else {
				turret.getNavigation().startMovingTo(target, 1.0);
			}
		}
	}
}

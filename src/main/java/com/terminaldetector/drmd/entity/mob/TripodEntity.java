package com.terminaldetector.drmd.entity.mob;

import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
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
 * Tripod strider — a cubic hull carried by three articulated legs.
 *
 * <p>The roster's heavy ground unit. It holds a mid standoff rather than closing, telegraphs its
 * plasma lance with a visible charge, and stamps a shockwave if anything gets underneath it. Turning
 * is eased about its own axis so the hull visibly pivots instead of sliding to face you.
 */
public class TripodEntity extends CyberMobEntity {
	private static final TrackedData<Float> CHARGE =
			DataTracker.registerData(TripodEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private static final float LANCE_COST = 35f;
	private static final float LANCE_CHARGE_SECONDS = 1.1f;
	private static final double STANDOFF = 12.0;
	private static final double STOMP_RANGE = 4.0;

	private float lanceCooldown;
	private float chargeTimer;
	private float stompCooldown;

	public TripodEntity(EntityType<? extends TripodEntity> type, World world) {
		super(type, world);
		configure(80f, 90f, 3f, 0.25f, 0.10f, 0.30f, 140f, 16f);
	}

	public static DefaultAttributeContainer.Builder createTripodAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 300)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12)
				.add(EntityAttributes.GENERIC_ARMOR, 8)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85)
				// Three long legs stride over terrain instead of pathing around every ledge.
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.6);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(CHARGE, 0f);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new TripodBattleGoal(this));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.7));
		goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24f));
		targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	/** 0..1 lance charge, drives the emissive build-up in the renderer. */
	public float getChargeLevel() {
		return dataTracker.get(CHARGE);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;
		float dt = 1f / 20f;
		if (lanceCooldown > 0) lanceCooldown -= dt;
		if (stompCooldown > 0) stompCooldown -= dt;
		dataTracker.set(CHARGE, Math.min(1f, chargeTimer / LANCE_CHARGE_SECONDS));
		if (chargeTimer > 0 && getWorld() instanceof ServerWorld sw) {
			Vec3d muzzle = muzzle(1.1, 0.82);
			sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, muzzle.x, muzzle.y, muzzle.z,
					2, 0.12, 0.12, 0.12, 0.01);
		}
	}

	/** Wind up, then release a heavy plasma lance. Returns true once it actually fires. */
	private boolean tickLance(LivingEntity target, float dt) {
		if (lanceCooldown > 0) {
			chargeTimer = 0;
			return false;
		}
		chargeTimer += dt;
		if (chargeTimer < LANCE_CHARGE_SECONDS) return false;
		chargeTimer = 0;
		if (!drawEnergy(LANCE_COST)) {
			lanceCooldown = 1.0f;
			return false;
		}
		Vec3d from = muzzle(1.1, 0.82);
		Vec3d dir = aimAt(from, target);
		WeaponCore.FireConfig cfg = shot(from, dir, 3000f, 34f, DamageClass.EXOTIC, 3.5f);
		cfg.splashDamage = 22f;
		cfg.splashRadius = 130f;
		cfg.meshKind = ProjectileEntity.MESH_ORB;
		cfg.visualScale = 1.35f;
		cfg.worldBlast = true;
		cfg.colorR = 190; cfg.colorG = 90; cfg.colorB = 255;
		fire(cfg);
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
				SoundCategory.HOSTILE, 0.8f, 1.4f);
		lanceCooldown = 2.8f;
		return true;
	}

	/** Ground slam when something is standing under the hull. */
	private void stomp() {
		if (stompCooldown > 0 || !(getWorld() instanceof ServerWorld sw)) return;
		stompCooldown = 3.2f;
		WeaponCore.splashDamage(this, sw, getPos(), 16f, (float) STOMP_RANGE, DamageClass.KINETIC);
		sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 6, 1.4, 0.2, 1.4, 0.0);
		sw.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK,
				SoundCategory.HOSTILE, 1.2f, 0.6f);
		for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class,
				getBoundingBox().expand(STOMP_RANGE), t -> t != this && t.isAlive())) {
			Vec3d push = e.getPos().subtract(getPos());
			if (push.lengthSquared() < 1e-6) continue;
			push = push.normalize().multiply(0.7).add(0, 0.45, 0);
			e.addVelocity(push.x, push.y, push.z);
			e.velocityModified = true;
		}
	}

	/** Hold a firing standoff, back off if crowded, lance on cooldown, stomp if underfoot. */
	private static class TripodBattleGoal extends Goal {
		private final TripodEntity tripod;

		TripodBattleGoal(TripodEntity tripod) {
			this.tripod = tripod;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity t = tripod.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void stop() {
			tripod.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = tripod.getTarget();
			if (target == null) return;
			float dt = 1f / 20f;
			double dist = tripod.distanceTo(target);

			// Pivot the hull about its own axis toward the target before anything else.
			Vec3d toTarget = target.getPos().subtract(tripod.getPos());
			FlightAttitude.steer(tripod, new Vec3d(toTarget.x, 0, toTarget.z), 5f, dt);
			tripod.getLookControl().lookAt(target, 30f, 30f);

			if (dist > STANDOFF + 3) {
				tripod.getNavigation().startMovingTo(target, 1.0);
			} else if (dist < STANDOFF - 4) {
				// Too close for the lance — walk backwards along the target vector.
				Vec3d away = tripod.getPos().subtract(target.getPos());
				if (away.lengthSquared() > 1e-6) {
					Vec3d retreat = tripod.getPos().add(away.normalize().multiply(8));
					tripod.getNavigation().startMovingTo(retreat.x, retreat.y, retreat.z, 0.9);
				}
			} else {
				tripod.getNavigation().stop();
			}

			if (dist <= STOMP_RANGE) {
				tripod.stomp();
			}
			if (dist < 34 && tripod.canSee(target)) {
				tripod.tickLance(target, dt);
			}
		}
	}
}

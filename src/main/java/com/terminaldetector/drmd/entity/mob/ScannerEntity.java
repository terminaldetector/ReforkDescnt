package com.terminaldetector.drmd.entity.mob;

import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import com.terminaldetector.drmd.ai.HostileEnvironment;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Flying scanner — the roster's Descent-style sentry drone.
 *
 * <p>Hovers at a standoff, sidles around its target and runs a fixed firing cycle: one laser lance,
 * then three rockets launched one at a time from its rotating pod ring, then a recharge pause. The
 * cycle is deliberate rather than random so the pattern is learnable and dodgeable.
 *
 * <p>Turning goes through {@link FlightAttitude}, so it banks into its strafes and keeps rolling
 * cleanly when it climbs or dives vertically.
 */
public class ScannerEntity extends CyberMobEntity {
	/** Firing phases, in cycle order. */
	public enum Fire { LASER, ROCKET_A, ROCKET_B, ROCKET_C, RECHARGE }

	private static final float LASER_COST = 12f;
	private static final float ROCKET_COST = 20f;
	private static final double STANDOFF = 14.0;

	private Fire fire = Fire.LASER;
	private float fireTimer;
	private float scanSpin;
	private double orbitPhase;

	public ScannerEntity(EntityType<? extends ScannerEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
		configure(60f, 20f, 4f, 0.05f, 0.30f, 0f, 120f, 22f);
		orbitPhase = Math.random() * Math.PI * 2;
	}

	public static DefaultAttributeContainer.Builder createScannerAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 120)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.42)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.9);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new ScannerCombatGoal(this));
		goalSelector.add(6, new ScannerPatrolGoal(this));
		HostileEnvironment.installTargets(this, this.targetSelector);
	}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);
		if (getWorld().isClient) {
			scanSpin += isAlert() ? 14f : 5f;
			return;
		}
		if (age % 6 == 0 && getWorld() instanceof ServerWorld sw) {
			Vec3d p = getPos();
			sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.25, 0.25, 0.25, 0.0);
		}
	}

	/** Client-side sensor ring spin, degrees. */
	public float getScanSpin() {
		return scanSpin;
	}

	public Fire getFirePhase() {
		return fire;
	}

	/**
	 * Advance the firing cycle. Each call either shoots and moves to the next phase, or ticks the
	 * dwell down. Rockets go out singly so the salvo reads as three distinct launches.
	 */
	private void advanceFire(LivingEntity target, float dt) {
		fireTimer -= dt;
		if (fireTimer > 0) return;
		Vec3d from = getPos().add(0, getHeight() * 0.55, 0);
		Vec3d dir = aimAt(from, target);
		switch (fire) {
			case LASER -> {
				if (!drawEnergy(LASER_COST)) {
					fireTimer = 0.4f;
					return;
				}
				WeaponCore.FireConfig cfg = shot(from, dir, 8000f, 16f, DamageClass.ENERGY, 1.4f);
				cfg.colorR = 90; cfg.colorG = 255; cfg.colorB = 200;
				cfg.pierceCount = 1;
				fire(cfg);
				emitSound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
				fire = Fire.ROCKET_A;
				fireTimer = 0.7f;
			}
			case ROCKET_A, ROCKET_B, ROCKET_C -> {
				if (!drawEnergy(ROCKET_COST)) {
					fireTimer = 0.5f;
					return;
				}
				launchRocket(from, dir, target, podOffset(fire));
				fire = switch (fire) {
					case ROCKET_A -> Fire.ROCKET_B;
					case ROCKET_B -> Fire.ROCKET_C;
					default -> Fire.RECHARGE;
				};
				fireTimer = fire == Fire.RECHARGE ? 2.6f : 0.45f;
			}
			case RECHARGE -> {
				fire = Fire.LASER;
				fireTimer = 0.3f;
			}
		}
	}

	/** Lateral spread so the three rockets visibly leave different pods. */
	private Vec3d podOffset(Fire phase) {
		double angle = switch (phase) {
			case ROCKET_A -> 0.0;
			case ROCKET_B -> Math.PI * 2 / 3;
			default -> Math.PI * 4 / 3;
		};
		Vec3d fwd = getRotationVec(1f);
		Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(fwd);
		Vec3d up = com.terminaldetector.drmd.flight.ShipAttitude.levelUpOf(fwd);
		return right.multiply(Math.cos(angle) * 0.55).add(up.multiply(Math.sin(angle) * 0.55));
	}

	private void launchRocket(Vec3d from, Vec3d dir, LivingEntity target, Vec3d offset) {
		WeaponCore.FireConfig cfg = shot(from.add(offset), dir, 2000f, 22f, DamageClass.EXPLOSIVE, 5f);
		cfg.splashDamage = 26f;
		cfg.splashRadius = 110f;
		cfg.homing = true;
		cfg.turnRate = 62f;
		cfg.homeTarget = target;
		cfg.meshKind = ProjectileEntity.MESH_ROCKET;
		cfg.visualScale = 0.9f;
		cfg.colorR = 255; cfg.colorG = 120; cfg.colorB = 60;
		fire(cfg);
		emitSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.3f);
	}

	private void emitSound(net.minecraft.sound.SoundEvent event, float volume, float pitch) {
		getWorld().playSound(null, getX(), getY(), getZ(), event, SoundCategory.HOSTILE, volume, pitch);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("firePhase", fire.name());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("firePhase")) {
			try {
				fire = Fire.valueOf(nbt.getString("firePhase"));
			} catch (IllegalArgumentException ignored) {
				fire = Fire.LASER;
			}
		}
	}

	/** Hold a standoff, sidle around the target, keep the nose on it, run the fire cycle. */
	private static class ScannerCombatGoal extends Goal {
		private final ScannerEntity scanner;

		ScannerCombatGoal(ScannerEntity scanner) {
			this.scanner = scanner;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity t = scanner.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			LivingEntity target = scanner.getTarget();
			if (target == null) return;
			float dt = 1f / 20f;
			Vec3d pos = scanner.getPos();
			Vec3d tpos = target.getPos().add(0, target.getHeight() * 0.6, 0);
			Vec3d toTarget = tpos.subtract(pos);
			double dist = toTarget.length();
			if (dist < 1e-3) return;
			Vec3d radial = toTarget.multiply(1.0 / dist);

			// Sidle: a slow orbit around the target plus a standoff correction.
			scanner.orbitPhase += 0.9 * dt;
			Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(radial);
			Vec3d up = com.terminaldetector.drmd.flight.ShipAttitude.levelUpOf(radial);
			Vec3d tangent = right.multiply(Math.cos(scanner.orbitPhase))
					.add(up.multiply(Math.sin(scanner.orbitPhase) * 0.45));
			double standoffError = MathHelper.clamp((dist - STANDOFF) / STANDOFF, -1.0, 1.0);
			Vec3d desire = tangent.multiply(0.85).add(radial.multiply(standoffError * 1.4));
			if (desire.lengthSquared() < 1e-6) desire = radial;
			desire = desire.normalize();

			double speed = 0.42;
			scanner.setVelocity(scanner.getVelocity().multiply(0.72).add(desire.multiply(speed * 0.28)));
			scanner.velocityModified = true;

			// Nose tracks the target; bank comes from the lateral part of the steering demand.
			FlightAttitude.steer(scanner, radial, 8f, dt);
			float bank = FlightAttitude.bankTarget(radial, desire, 40f);
			scanner.setFlightRoll(FlightAttitude.approachAngle(scanner.getFlightRoll(), bank, 5f, dt));

			if (dist < 42 && scanner.canSee(target)) {
				scanner.advanceFire(target, dt);
			}
		}
	}

	/** Idle drift so an unaware scanner still reads as a patrolling machine. */
	private static class ScannerPatrolGoal extends Goal {
		private final ScannerEntity scanner;
		private Vec3d waypoint;
		private int repathTimer;

		ScannerPatrolGoal(ScannerEntity scanner) {
			this.scanner = scanner;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return scanner.getTarget() == null;
		}

		@Override
		public void tick() {
			float dt = 1f / 20f;
			if (waypoint == null || --repathTimer <= 0 || scanner.getPos().squaredDistanceTo(waypoint) < 4) {
				waypoint = scanner.getPos().add(
						(scanner.getRandom().nextDouble() - 0.5) * 24,
						(scanner.getRandom().nextDouble() - 0.5) * 10,
						(scanner.getRandom().nextDouble() - 0.5) * 24);
				repathTimer = 100;
			}
			Vec3d dir = waypoint.subtract(scanner.getPos());
			if (dir.lengthSquared() < 1e-6) return;
			dir = dir.normalize();
			scanner.setVelocity(scanner.getVelocity().multiply(0.85).add(dir.multiply(0.045)));
			scanner.velocityModified = true;
			FlightAttitude.steer(scanner, dir, 4f, dt);
			scanner.setFlightRoll(FlightAttitude.approachAngle(scanner.getFlightRoll(), 0f, 3f, dt));
		}
	}
}

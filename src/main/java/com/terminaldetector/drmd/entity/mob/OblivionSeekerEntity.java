package com.terminaldetector.drmd.entity.mob;

import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.ai.HostileEnvironment;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * Oblivion-style Seeker (рыскатель) — dark spherical drone, End-faction pre-boss.
 *
 * <p>Placeholder lineage: {@link ScannerEntity} combat + {@link FlightAttitude} 3D bank.
 * Solo predator: sharp dashes, twin-gun MG chatter, 4-rocket salvo. Cooperates by painting
 * the same target onto nearby End-faction machines ({@link HostileEnvironment#isAlly}).
 */
public class OblivionSeekerEntity extends CyberMobEntity {
	public enum Fire { SCAN, MG_BURST, ROCKET_SALVO, RECHARGE, DASH }

	private static final float MG_COST = 4f;
	private static final float ROCKET_COST = 18f;
	private static final double STANDOFF = 20.0;
	private static final double COOP_RADIUS = 48.0;

	private Fire fire = Fire.SCAN;
	private float fireTimer;
	private float ringSpin;
	private double orbitPhase;
	private int dashCooldown;
	private int coopTick;
	private int mgLeft;

	public OblivionSeekerEntity(EntityType<? extends OblivionSeekerEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
		// Pre-boss: tougher than Scanner, below Keeper/Boss.
		configure(90f, 40f, 5f, 0.15f, 0.35f, 0.10f, 160f, 28f);
		orbitPhase = Math.random() * Math.PI * 2;
		experiencePoints = 45;
	}

	public static DefaultAttributeContainer.Builder createSeekerAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 220)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.55)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 1.15)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.55);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new SeekerCombatGoal(this));
		goalSelector.add(6, new SeekerPatrolGoal(this));
		HostileEnvironment.installTargets(this, this.targetSelector);
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);
		if (getWorld().isClient) {
			ringSpin += isAlert() ? 18f : 7f;
			return;
		}
		if (dashCooldown > 0) dashCooldown--;
		if (age % 5 == 0 && getWorld() instanceof ServerWorld sw) {
			Vec3d p = getPos();
			sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 2, 0.35, 0.35, 0.35, 0.0);
			if (isAlert()) {
				sw.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.01);
			}
		}
		coopTick++;
		if (coopTick % 20 == 0) paintAllies();
	}

	/** Share aggro with End-faction machines in range (solo hunter that still calls backup). */
	private void paintAllies() {
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) return;
		if (!(getWorld() instanceof ServerWorld sw)) return;
		Box box = getBoundingBox().expand(COOP_RADIUS);
		List<LivingEntity> nearby = sw.getEntitiesByClass(LivingEntity.class, box,
				e -> e != this && HostileEnvironment.isAlly(e));
		for (LivingEntity ally : nearby) {
			if (ally instanceof HostileEntity h) {
				LivingEntity cur = h.getTarget();
				if (cur == null || !cur.isAlive()) {
					h.setTarget(target);
				}
			}
		}
	}

	public float getRingSpin() { return ringSpin; }
	public Fire getFirePhase() { return fire; }

	private void advanceFire(LivingEntity target, float dt) {
		fireTimer -= dt;
		if (fireTimer > 0) return;
		Vec3d from = getPos().add(0, getHeight() * 0.5, 0);
		Vec3d dir = aimAt(from, target);
		switch (fire) {
			case SCAN -> {
				emitSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.35f, 1.6f);
				fire = Fire.MG_BURST;
				mgLeft = 6;
				fireTimer = 0.15f;
			}
			case MG_BURST -> {
				if (!drawEnergy(MG_COST)) {
					fireTimer = 0.3f;
					return;
				}
				WeaponCore.FireConfig cfg = shot(from.add(gunOffset(mgLeft % 2 == 0)), dir,
						9000f, 7f, DamageClass.KINETIC, 0.9f);
				cfg.colorR = 255; cfg.colorG = 40; cfg.colorB = 40;
				cfg.visualScale = 0.55f;
				fire(cfg);
				emitSound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 0.25f, 1.9f);
				mgLeft--;
				if (mgLeft <= 0) {
					fire = Fire.ROCKET_SALVO;
					fireTimer = 0.35f;
				} else {
					fireTimer = 0.12f;
				}
			}
			case ROCKET_SALVO -> {
				if (!drawEnergy(ROCKET_COST * 4)) {
					fire = Fire.RECHARGE;
					fireTimer = 1.2f;
					return;
				}
				for (int i = 0; i < 4; i++) {
					launchRocket(from, dir, target, i);
				}
				emitSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.85f, 0.85f);
				fire = Fire.RECHARGE;
				fireTimer = 2.8f;
			}
			case RECHARGE -> {
				fire = dashCooldown <= 0 ? Fire.DASH : Fire.SCAN;
				fireTimer = 0.2f;
			}
			case DASH -> {
				// Handled in combat goal via requestDash; phase just marks the window.
				fire = Fire.SCAN;
				fireTimer = 0.4f;
			}
		}
	}

	private Vec3d gunOffset(boolean left) {
		Vec3d fwd = getRotationVec(1f);
		Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(fwd);
		return right.multiply(left ? -0.55 : 0.55).add(fwd.multiply(0.4));
	}

	private void launchRocket(Vec3d from, Vec3d dir, LivingEntity target, int index) {
		double ang = index * (Math.PI * 0.5) + orbitPhase;
		Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(dir);
		Vec3d up = com.terminaldetector.drmd.flight.ShipAttitude.levelUpOf(dir);
		Vec3d offset = right.multiply(Math.cos(ang) * 0.7).add(up.multiply(Math.sin(ang) * 0.7));
		WeaponCore.FireConfig cfg = shot(from.add(offset), dir, 2200f, 26f, DamageClass.EXPLOSIVE, 5.5f);
		cfg.splashDamage = 30f;
		cfg.splashRadius = 120f;
		cfg.homing = true;
		cfg.turnRate = 70f;
		cfg.homeTarget = target;
		cfg.meshKind = ProjectileEntity.MESH_ROCKET;
		cfg.visualScale = 1.0f;
		cfg.colorR = 180; cfg.colorG = 40; cfg.colorB = 255;
		fire(cfg);
	}

	private void emitSound(net.minecraft.sound.SoundEvent event, float volume, float pitch) {
		getWorld().playSound(null, getX(), getY(), getZ(), event, SoundCategory.HOSTILE, volume, pitch);
	}

	/** Sharp lateral/closing dash — the annoying “blink” of the Oblivion drone. */
	void tryDash(Vec3d desire) {
		if (dashCooldown > 0) return;
		Vec3d impulse = desire.normalize().multiply(1.35);
		setVelocity(getVelocity().multiply(0.2).add(impulse));
		velocityModified = true;
		dashCooldown = 28;
		if (getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.SONIC_BOOM, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
		}
		emitSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.35f, 1.8f);
		if (fire == Fire.DASH) {
			fire = Fire.SCAN;
			fireTimer = 0.25f;
		}
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
				fire = Fire.SCAN;
			}
		}
	}

	private static class SeekerCombatGoal extends Goal {
		private final OblivionSeekerEntity seeker;

		SeekerCombatGoal(OblivionSeekerEntity seeker) {
			this.seeker = seeker;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity t = seeker.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean shouldContinue() { return canStart(); }

		@Override
		public void tick() {
			LivingEntity target = seeker.getTarget();
			if (target == null) return;
			float dt = 1f / 20f;
			Vec3d pos = seeker.getPos();
			Vec3d tpos = target.getPos().add(0, target.getHeight() * 0.55, 0);
			Vec3d toTarget = tpos.subtract(pos);
			double dist = toTarget.length();
			if (dist < 1e-3) return;
			Vec3d radial = toTarget.multiply(1.0 / dist);

			// Faster, jittery orbit than Scanner — reads as predatory.
			seeker.orbitPhase += 1.55 * dt;
			Vec3d right = com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(radial);
			Vec3d up = com.terminaldetector.drmd.flight.ShipAttitude.levelUpOf(radial);
			Vec3d tangent = right.multiply(Math.cos(seeker.orbitPhase) * 1.15)
					.add(up.multiply(Math.sin(seeker.orbitPhase * 1.3) * 0.55));
			double standoffError = MathHelper.clamp((dist - STANDOFF) / STANDOFF, -1.2, 1.2);
			Vec3d desire = tangent.multiply(0.95).add(radial.multiply(standoffError * 1.7));
			if (desire.lengthSquared() < 1e-6) desire = radial;
			desire = desire.normalize();

			double speed = 0.62;
			seeker.setVelocity(seeker.getVelocity().multiply(0.68).add(desire.multiply(speed * 0.32)));
			seeker.velocityModified = true;

			FlightAttitude.steer(seeker, radial, 11f, dt);
			float bank = FlightAttitude.bankTarget(radial, desire, 48f);
			seeker.setFlightRoll(FlightAttitude.approachAngle(seeker.getFlightRoll(), bank, 7f, dt));

			// Dash when too close, too far, or during DASH phase.
			if (seeker.fire == Fire.DASH || dist < 9 || dist > 34) {
				Vec3d dashDir = dist < 9 ? desire.subtract(radial).normalize() : radial;
				if (dashDir.lengthSquared() < 1e-6) dashDir = desire;
				seeker.tryDash(dashDir);
			}

			if (dist < 56 && seeker.canSee(target)) {
				seeker.advanceFire(target, dt);
			}
		}
	}

	private static class SeekerPatrolGoal extends Goal {
		private final OblivionSeekerEntity seeker;
		private Vec3d waypoint;
		private int repathTimer;

		SeekerPatrolGoal(OblivionSeekerEntity seeker) {
			this.seeker = seeker;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return seeker.getTarget() == null;
		}

		@Override
		public void tick() {
			float dt = 1f / 20f;
			if (waypoint == null || --repathTimer <= 0 || seeker.getPos().squaredDistanceTo(waypoint) < 4) {
				waypoint = seeker.getPos().add(
						(seeker.getRandom().nextDouble() - 0.5) * 36,
						(seeker.getRandom().nextDouble() - 0.5) * 14,
						(seeker.getRandom().nextDouble() - 0.5) * 36);
				repathTimer = 70;
			}
			Vec3d dir = waypoint.subtract(seeker.getPos());
			if (dir.lengthSquared() < 1e-6) return;
			dir = dir.normalize();
			seeker.setVelocity(seeker.getVelocity().multiply(0.82).add(dir.multiply(0.07)));
			seeker.velocityModified = true;
			FlightAttitude.steer(seeker, dir, 6f, dt);
			seeker.setFlightRoll(FlightAttitude.approachAngle(seeker.getFlightRoll(), 0f, 4f, dt));
		}
	}
}

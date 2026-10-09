package com.terminaldetector.drmd.physics;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.gravity.TransientGravityFields;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative physical-target manipulator shared by the three gravity weapons.
 *
 * <p>This is intentionally a bounded gameplay layer over the real D6 bodies, not a second rigid
 * body engine. It owns selection budgets, hold springs, rail alignment and launch policy. A
 * {@link PhysicsTarget} receives impulses/torque in physical units; light vanilla entities use one
 * adapter here, so weapon code never grows a separate {@code addVelocity} branch per gun.</p>
 */
public final class GravyPhysics {
	public enum Mode {
		SWARM(8, 32, 4.4, 28, 7, 2.4, 26, 0, 0),
		RAIL(1, 384, 3.6, 54, 11, 3.4, 42, 0, 0),
		SHIFT(1, 96, 4.0, 36, 9, 2.8, 25, 10, 20 * 8);

		private final int maxTargets;
		private final double maxMass;
		private final double holdDistance;
		private final double stiffness;
		private final double damping;
		private final double maxDeltaVelocity;
		private final double launchSpeed;
		private final double fieldRadius;
		private final int fieldTicks;

		Mode(int maxTargets, double maxMass, double holdDistance, double stiffness, double damping,
			 double maxDeltaVelocity, double launchSpeed, double fieldRadius, int fieldTicks) {
			this.maxTargets = maxTargets;
			this.maxMass = maxMass;
			this.holdDistance = holdDistance;
			this.stiffness = stiffness;
			this.damping = damping;
			this.maxDeltaVelocity = maxDeltaVelocity;
			this.launchSpeed = launchSpeed;
			this.fieldRadius = fieldRadius;
			this.fieldTicks = fieldTicks;
		}

		public int maxTargets() { return maxTargets; }
		public double maxMass() { return maxMass; }
		public double holdDistance() { return holdDistance; }
		public double launchSpeed() { return launchSpeed; }

		public boolean accepts(int currentCount, double currentMass, double nextMass) {
			return currentCount < maxTargets && Double.isFinite(nextMass) && nextMass > 0
					&& currentMass + nextMass <= maxMass + 1.0e-9;
		}
	}

	public record Held(UUID targetId, double mass) {}

	public record Grab(Mode mode, List<Held> targets) {
		public Grab {
			targets = List.copyOf(targets);
		}

		public double totalMass() {
			return targets.stream().mapToDouble(Held::mass).sum();
		}
	}

	private static final Map<UUID, Grab> GRABS = new ConcurrentHashMap<>();
	private static final double MAX_TETHER_DISTANCE = 34;

	private GravyPhysics() {}

	public static boolean isHolding(ServerPlayerEntity player) {
		return player != null && GRABS.containsKey(player.getUuid());
	}

	public static Optional<Mode> activeMode(ServerPlayerEntity player) {
		Grab grab = player == null ? null : GRABS.get(player.getUuid());
		return grab == null ? Optional.empty() : Optional.of(grab.mode());
	}

	public static int heldCount(ServerPlayerEntity player) {
		Grab grab = player == null ? null : GRABS.get(player.getUuid());
		return grab == null ? 0 : grab.targets().size();
	}

	public static double heldMass(ServerPlayerEntity player) {
		Grab grab = player == null ? null : GRABS.get(player.getUuid());
		return grab == null ? 0 : grab.totalMass();
	}

	public static void release(ServerPlayerEntity player) {
		if (player == null) return;
		GRABS.remove(player.getUuid());
		DescentPlayerData.get(player).setGravyGrabbing(false);
	}

	public static void clear() {
		GRABS.clear();
	}

	/** Vanilla entities admitted by the one light-body adapter. Players remain opt-out. */
	public static boolean canGrab(Entity target) {
		return target != null && target.isAlive() && !(target instanceof PlayerEntity)
				&& (target instanceof PhysicsTarget || target instanceof LivingEntity || target instanceof ItemEntity);
	}

	public static double massOf(Entity target) {
		if (target instanceof PhysicsTarget physics) return clampMass(physics.physicsMass());
		if (target instanceof ItemEntity item) {
			return clampMass(.2 + Math.min(64, item.getStack().getCount()) * .035);
		}
		return clampMass(Math.max(.8, target.getWidth() * target.getHeight() * 2.2));
	}

	/** Begin a bounded capture from nearest-first candidates. */
	public static int tryGrab(ServerPlayerEntity player, Mode mode, List<? extends Entity> candidates) {
		if (player == null || mode == null || candidates == null || candidates.isEmpty()) return 0;
		List<Held> accepted = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		double totalMass = 0;
		for (Entity target : candidates) {
			if (!canGrab(target) || target.getWorld() != player.getWorld() || !seen.add(target.getUuid())) continue;
			double mass = massOf(target);
			if (!mode.accepts(accepted.size(), totalMass, mass)) continue;
			accepted.add(new Held(target.getUuid(), mass));
			totalMass += mass;
			if (accepted.size() >= mode.maxTargets()) break;
		}
		if (accepted.isEmpty()) return 0;
		GRABS.put(player.getUuid(), new Grab(mode, accepted));
		DescentPlayerData.get(player).setGravyGrabbing(true);
		return accepted.size();
	}

	/** Legacy single-target entry point kept for old commands/addons. */
	public static boolean tryGrab(ServerPlayerEntity player, Entity target, float ignoredMass) {
		if (target == null) return false;
		return tryGrab(player, Mode.RAIL, List.of(target)) == 1;
	}

	/** Spring-hold targets in a 6DoF-local formation in front of the player's eyes. */
	public static void tick(ServerPlayerEntity player) {
		Grab grab = GRABS.get(player.getUuid());
		if (grab == null) return;
		ServerWorld world = player.getServerWorld();
		Vec3d aim = safeAim(player);
		List<Held> active = new ArrayList<>(grab.targets().size());
		for (Held held : grab.targets()) {
			Entity target = world.getEntity(held.targetId());
			if (!canGrab(target) || target.squaredDistanceTo(player) > MAX_TETHER_DISTANCE * MAX_TETHER_DISTANCE)
				continue;
			active.add(held);
		}
		if (active.isEmpty()) {
			release(player);
			return;
		}
		if (active.size() != grab.targets().size()) {
			grab = new Grab(grab.mode(), active);
			GRABS.put(player.getUuid(), grab);
		}

		for (int index = 0; index < active.size(); index++) {
			Held held = active.get(index);
			Entity target = world.getEntity(held.targetId());
			if (target == null) continue;
			Vec3d formation = formationOffset(grab.mode(), index, active.size(), aim);
			Vec3d holdPoint = player.getEyePos().add(aim.multiply(grab.mode().holdDistance())).add(formation);
			Vec3d centre = centreOf(target);
			Vec3d impulse = springImpulse(grab.mode(), holdPoint.subtract(centre), velocityOf(target), held.mass());
			applyImpulse(target, centre, impulse, held.mass());
			if (grab.mode() == Mode.RAIL && target instanceof PhysicsTarget physics) {
				alignLongAxis(physics, aim, held.mass());
			}
			if ((player.age & 1) == 0) drawTether(world, grab.mode(), player.getEyePos(), centre);
		}
	}

	/** Release the current formation as one volley. Returns the number of bodies actually launched. */
	public static int fling(ServerPlayerEntity player) {
		Grab grab = GRABS.remove(player.getUuid());
		DescentPlayerData.get(player).setGravyGrabbing(false);
		if (grab == null) return 0;
		ServerWorld world = player.getServerWorld();
		Vec3d aim = safeAim(player);
		int launched = 0;
		for (int index = 0; index < grab.targets().size(); index++) {
			Held held = grab.targets().get(index);
			Entity target = world.getEntity(held.targetId());
			if (!canGrab(target)) continue;
			Vec3d spread = formationOffset(grab.mode(), index, grab.targets().size(), aim).multiply(.12);
			Vec3d direction = aim.add(spread).normalize();
			Vec3d desiredVelocity = direction.multiply(grab.mode().launchSpeed());
			Vec3d impulse = desiredVelocity.subtract(velocityOf(target)).multiply(held.mass());
			impulse = clampVector(impulse, held.mass() * grab.mode().launchSpeed() * 1.5);
			applyImpulse(target, centreOf(target), impulse, held.mass());
			if (grab.mode() == Mode.SHIFT) {
				if (target instanceof PhysicsTarget physics) physics.enablePhysicsGravity();
				TransientGravityFields.attach(world, target, direction, grab.mode().fieldRadius,
						1.35f, grab.mode().fieldTicks, "Gravity throw");
			}
			world.spawnParticles(grab.mode() == Mode.SHIFT ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.ELECTRIC_SPARK,
					target.getX(), target.getBodyY(.5), target.getZ(), 12, .25, .25, .25, .08);
			launched++;
		}
		return launched;
	}

	/** Legacy power argument is deliberately ignored: launch tuning now belongs to the mode profile. */
	public static void fling(ServerPlayerEntity player, float ignoredPower) {
		fling(player);
	}

	/** Pure spring calculation used by gameplay and unit tests. Velocities are blocks per second. */
	public static Vec3d springImpulse(Mode mode, Vec3d delta, Vec3d velocity, double mass) {
		if (mode == null || !finite(delta) || !finite(velocity)) return Vec3d.ZERO;
		mass = clampMass(mass);
		Vec3d acceleration = delta.multiply(mode.stiffness).subtract(velocity.multiply(mode.damping));
		return clampVector(acceleration.multiply(mass * .05), mass * mode.maxDeltaVelocity);
	}

	/** Stable ring in the plane perpendicular to aim; no world-Y assumption, so it survives roll. */
	public static Vec3d formationOffset(Mode mode, int index, int count, Vec3d aim) {
		if (mode != Mode.SWARM || count <= 1 || !finite(aim) || aim.lengthSquared() < 1.0e-10)
			return Vec3d.ZERO;
		Vec3d forward = aim.normalize();
		Vec3d reference = Math.abs(forward.y) < .9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
		Vec3d right = forward.crossProduct(reference).normalize();
		Vec3d up = right.crossProduct(forward).normalize();
		double angle = index * Math.PI * (3 - Math.sqrt(5));
		double radius = .62 + .18 * (index / 6);
		return right.multiply(Math.cos(angle) * radius).add(up.multiply(Math.sin(angle) * radius));
	}

	private static void alignLongAxis(PhysicsTarget physics, Vec3d aim, double mass) {
		physics.physicsLongAxis().ifPresent(rawAxis -> {
			if (!finite(rawAxis) || rawAxis.lengthSquared() < 1.0e-10) return;
			Vec3d axis = rawAxis.normalize();
			if (axis.dotProduct(aim) < 0) axis = axis.negate();
			Vec3d error = axis.crossProduct(aim);
			Vec3d angular = physics.physicsAngularVelocity();
			Vec3d torque = error.multiply(mass * 18).subtract(angular.multiply(mass * 4));
			physics.applyPhysicsTorque(clampVector(torque, mass * 28));
		});
	}

	private static void applyImpulse(Entity target, Vec3d point, Vec3d impulse, double mass) {
		if (!finite(impulse) || impulse.lengthSquared() < 1.0e-12) return;
		if (target instanceof PhysicsTarget physics) {
			physics.applyPhysicsImpulse(point, impulse);
			return;
		}
		// Vanilla velocity is blocks/tick; PhysicsTarget velocity is blocks/second.
		Vec3d tickDelta = impulse.multiply(1.0 / clampMass(mass) / 20.0);
		target.addVelocity(tickDelta.x, tickDelta.y, tickDelta.z);
		target.velocityModified = true;
		target.fallDistance = 0;
	}

	private static Vec3d velocityOf(Entity target) {
		return target instanceof PhysicsTarget physics
				? physics.physicsVelocity() : target.getVelocity().multiply(20);
	}

	private static Vec3d centreOf(Entity target) {
		return target instanceof PhysicsTarget ? target.getPos() : target.getBoundingBox().getCenter();
	}

	private static Vec3d safeAim(ServerPlayerEntity player) {
		Vec3d aim = WeaponCore.aimDir(player);
		return finite(aim) && aim.lengthSquared() > 1.0e-10 ? aim.normalize() : new Vec3d(0, 0, 1);
	}

	private static void drawTether(ServerWorld world, Mode mode, Vec3d from, Vec3d to) {
		for (int point = 1; point <= 4; point++) {
			Vec3d p = from.lerp(to, point / 5.0);
			world.spawnParticles(mode == Mode.SWARM ? ParticleTypes.END_ROD
						: mode == Mode.RAIL ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.REVERSE_PORTAL,
					p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}

	private static double clampMass(double mass) {
		return Double.isFinite(mass) ? Math.max(.2, Math.min(512, mass)) : 1;
	}

	private static Vec3d clampVector(Vec3d value, double maxLength) {
		if (!finite(value) || !Double.isFinite(maxLength) || maxLength <= 0) return Vec3d.ZERO;
		double squared = value.lengthSquared();
		return squared > maxLength * maxLength ? value.normalize().multiply(maxLength) : value;
	}

	private static boolean finite(Vec3d value) {
		return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
	}
}

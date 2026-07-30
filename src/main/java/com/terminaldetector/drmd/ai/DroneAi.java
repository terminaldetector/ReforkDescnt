package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Combat motion: ORBIT → RUN → BREAK with 6DoF attitude (yaw/pitch/roll from velocity).
 * Port of GMod drone combat — ~10 roles share this motion; no special per-mob port needed.
 */
public final class DroneAi {
	public enum Phase { ORBIT, RUN, BREAK }

	public static class CombatGoal extends Goal {
		private final DroneEntity drone;

		public CombatGoal(DroneEntity drone) {
			this.drone = drone;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return drone.getTarget() != null && drone.getTarget().isAlive();
		}

		@Override
		public void tick() {
			LivingEntity target = drone.getTarget();
			if (target == null) return;
			float dt = 1f / 20f;
			drone.addPhaseTimer(dt);

			Vec3d pos = drone.getPos();
			Vec3d tpos = target.getPos().add(0, target.getHeight() * 0.5, 0);
			Vec3d toTarget = tpos.subtract(pos);
			double dist = toTarget.length();
			double preferred = DescentMod.su(drone.getRole().speed > 500 ? 500 : 700);

			Phase phase = drone.getPhase();
			if (phase == Phase.ORBIT && drone.getPhaseTimer() > 3f) drone.setPhase(Phase.RUN);
			else if (phase == Phase.RUN && drone.getPhaseTimer() > 1.5f) drone.setPhase(Phase.BREAK);
			else if (phase == Phase.BREAK && drone.getPhaseTimer() > 1.2f) drone.setPhase(Phase.ORBIT);

			Vec3d desire;
			switch (drone.getPhase()) {
				case ORBIT -> {
					// Orbit in the plane facing the target (full 3D, not world-up locked)
					Vec3d radial = toTarget.lengthSquared() > 1e-6 ? toTarget.normalize() : new Vec3d(0, 0, 1);
					Vec3d upHint = Math.abs(radial.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
					Vec3d tangent = radial.crossProduct(upHint);
					if (tangent.lengthSquared() < 1e-4) tangent = new Vec3d(1, 0, 0);
					tangent = tangent.normalize();
					double radialErr = MathHelper.clamp((dist - preferred) / preferred, -1, 1);
					desire = tangent.multiply(2.2).add(radial.multiply(-radialErr));
				}
				case RUN -> desire = toTarget.normalize().add(0, drone.getRandom().nextBoolean() ? 0.4 : -0.4, 0);
				case BREAK -> {
					Vec3d away = pos.subtract(tpos).normalize();
					desire = away.add(0, 0.6, 0).add(drone.getRotationVector().multiply(0.5));
				}
				default -> desire = toTarget.normalize();
			}

			Vec3d sep = Vec3d.ZERO;
			for (DroneEntity other : drone.getWorld().getEntitiesByClass(DroneEntity.class,
					drone.getBoundingBox().expand(DescentMod.su(90)), d -> d != drone)) {
				Vec3d away = pos.subtract(other.getPos());
				double d = away.length();
				if (d > 1e-3 && d < DescentMod.su(90)) {
					sep = sep.add(away.normalize().multiply(2.8 / d));
				}
			}
			desire = desire.add(sep);
			if (desire.lengthSquared() < 1e-6) desire = toTarget;
			desire = desire.normalize();

			double maxSpd = DescentMod.su(drone.getRole().speed);
			Vec3d vel = desire.multiply(maxSpd / 20.0);
			drone.setVelocity(vel);
			drone.velocityModified = true;
			alignAttitude(drone, vel, desire, dt);

			if (dist < DescentMod.su(2000)) {
				drone.tryFireAt(target);
			}
		}

		/** Velocity-aligned yaw/pitch + bank roll — 6DoF feel without per-mob special cases. */
		private static void alignAttitude(DroneEntity drone, Vec3d vel, Vec3d desire, float dt) {
			if (vel.lengthSquared() < 1e-6) return;
			Vec3d dir = vel.normalize();
			float yaw = (float) (MathHelper.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
			float pitch = (float) (MathHelper.atan2(-dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI));
			drone.setYaw(yaw);
			drone.setPitch(pitch);
			drone.bodyYaw = yaw;

			// Bank into turn: lateral desire vs current right vector
			Vec3d forward = dir;
			Vec3d worldUp = new Vec3d(0, 1, 0);
			Vec3d right = forward.crossProduct(worldUp);
			if (right.lengthSquared() < 1e-4) right = new Vec3d(1, 0, 0);
			right = right.normalize();
			float bank = (float) MathHelper.clamp(desire.dotProduct(right) * 55.0, -55.0, 55.0);
			float roll = MathHelper.lerp(1f - (float) Math.exp(-6f * dt), drone.getFlightRoll(), bank);
			drone.setFlightRoll(roll);
		}
	}
}

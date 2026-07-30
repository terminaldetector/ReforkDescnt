package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Combat motion phases: ORBIT → RUN (dive/climb) → BREAK (+ barrel/split-s/immelmann).
 * Boids weights from d6_ai.lua: sep 2.8 / coh 0.8 / align 1.0 / target 2.2.
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
					Vec3d tangent = toTarget.crossProduct(new Vec3d(0, 1, 0));
					if (tangent.lengthSquared() < 1e-4) tangent = new Vec3d(1, 0, 0);
					tangent = tangent.normalize();
					double radial = MathHelper.clamp((dist - preferred) / preferred, -1, 1);
					desire = tangent.multiply(2.2).add(toTarget.normalize().multiply(-radial));
				}
				case RUN -> desire = toTarget.normalize().add(0, drone.getRandom().nextBoolean() ? 0.4 : -0.4, 0);
				case BREAK -> {
					Vec3d away = pos.subtract(tpos).normalize();
					desire = away.add(0, 0.6, 0).add(drone.getRotationVector().multiply(0.5));
				}
				default -> desire = toTarget.normalize();
			}

			// Simple separation from nearby drones
			Vec3d sep = Vec3d.ZERO;
			for (DroneEntity other : drone.getWorld().getEntitiesByClass(DroneEntity.class,
					drone.getBoundingBox().expand(DescentMod.su(90)), d -> d != drone)) {
				Vec3d away = pos.subtract(other.getPos());
				double d = away.length();
				if (d > 1e-3 && d < DescentMod.su(90)) {
					sep = sep.add(away.normalize().multiply(2.8 / d));
				}
			}
			desire = desire.add(sep).normalize();

			double maxSpd = DescentMod.su(drone.getRole().speed);
			Vec3d vel = desire.multiply(maxSpd / 20.0);
			drone.setVelocity(vel);
			drone.velocityModified = true;
			drone.getLookControl().lookAt(target, 30f, 30f);

			if (dist < DescentMod.su(2000)) {
				drone.tryFireAt(target);
			}
		}
	}
}

package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Reynolds boids for Descent drones — port of {@code D6_Swarm_AI_Core} in d6_ai.lua.
 *
 * <p>Separation / cohesion / alignment among flock-mates, plus a pull toward the current
 * combat target. Heavy / elite / mine-style roles stay out of the cloud (same as GMod
 * {@code BOIDS_EXCLUDED}).
 */
public final class DroneSwarmAi {
	private static final float SWARM_RADIUS = 400f;
	private static final float SEPARATION_DIST = 90f;
	private static final float WEIGHT_SEPARATION = 2.8f;
	private static final float WEIGHT_COHESION = 0.8f;
	private static final float WEIGHT_ALIGNMENT = 1.0f;
	private static final float WEIGHT_TARGET = 2.2f;

	private DroneSwarmAi() {}

	public static boolean participates(AiRole role) {
		if (role == null) return false;
		return switch (role) {
			case HEAVY, HEAVY_ELITE, GRAV -> false;
			default -> true;
		};
	}

	/**
	 * Unitless desire vector to blend into combat motion. Zero when alone / excluded.
	 */
	public static Vec3d compute(DroneEntity drone, LivingEntity target) {
		if (!participates(drone.getRole())) return Vec3d.ZERO;
		double radius = DescentMod.su(SWARM_RADIUS);
		double sepDist = DescentMod.su(SEPARATION_DIST);
		Vec3d myPos = drone.getPos();

		Vec3d separation = Vec3d.ZERO;
		Vec3d center = Vec3d.ZERO;
		Vec3d avgVel = Vec3d.ZERO;
		int count = 0;

		for (DroneEntity other : drone.getWorld().getEntitiesByClass(DroneEntity.class,
				drone.getBoundingBox().expand(radius),
				d -> d != drone && d.isAlive() && participates(d.getRole()))) {
			Vec3d nPos = other.getPos();
			double d = myPos.distanceTo(nPos);
			if (d < 1e-4 || d > radius) continue;
			if (d < sepDist) {
				separation = separation.add(myPos.subtract(nPos).normalize().multiply(1.0 / d));
			}
			center = center.add(nPos);
			avgVel = avgVel.add(other.getVelocity());
			count++;
		}

		Vec3d cohesion = Vec3d.ZERO;
		Vec3d alignment = Vec3d.ZERO;
		if (count > 0) {
			center = center.multiply(1.0 / count);
			Vec3d toCenter = center.subtract(myPos);
			if (toCenter.lengthSquared() > 1e-6) cohesion = toCenter.normalize();
			Vec3d avg = avgVel.multiply(1.0 / count);
			if (avg.lengthSquared() > 1e-6) alignment = avg.normalize();
		}

		Vec3d targetDir = Vec3d.ZERO;
		if (target != null && target.isAlive()) {
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.4, 0).subtract(myPos);
			if (to.lengthSquared() > 1e-6) {
				targetDir = to.normalize();
				// MG pressure: don't sit in their face — GMod mg &lt; 400 retreat bias.
				if (drone.getRole() == AiRole.MG && to.length() < DescentMod.su(400)) {
					targetDir = targetDir.multiply(-0.5);
				}
			}
		}

		Vec3d finalVel = separation.multiply(WEIGHT_SEPARATION)
				.add(cohesion.multiply(WEIGHT_COHESION))
				.add(alignment.multiply(WEIGHT_ALIGNMENT))
				.add(targetDir.multiply(WEIGHT_TARGET));
		if (finalVel.lengthSquared() < 1e-6) return Vec3d.ZERO;
		return finalVel.normalize();
	}
}

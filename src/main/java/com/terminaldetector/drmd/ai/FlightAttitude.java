package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Shared 6DoF turning for every flying DRMD mob — same rules the player's Pyro follows.
 *
 * <p>Two things matter and both used to be done ad hoc per mob:
 *
 * <ul>
 *   <li><b>Bank reference.</b> The roll a mob leans into a turn is measured against the pole-safe
 *       zero-roll frame ({@link ShipAttitude#levelRightOf}), never {@code forward × worldUp}. The
 *       latter collapses when a drone climbs or dives vertically, which made it snap its roll.
 *   <li><b>Angle wrapping.</b> Heading and bank are approached along the shortest arc, so a mob
 *       crossing the ±180° seam turns the short way instead of spinning all the way round.
 * </ul>
 */
public final class FlightAttitude {
	private FlightAttitude() {}

	/** Minecraft yaw for a heading, in degrees. */
	public static float yawOf(Vec3d dir) {
		return (float) Math.toDegrees(MathHelper.atan2(-dir.x, dir.z));
	}

	/** Minecraft pitch for a heading, in degrees (positive = nose down). */
	public static float pitchOf(Vec3d dir) {
		double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		return (float) Math.toDegrees(MathHelper.atan2(-dir.y, horizontal));
	}

	/**
	 * Bank a mob should hold for the lateral component of what it wants to do.
	 *
	 * @param heading   current facing (need not be normalised)
	 * @param desire    where the mob is steering
	 * @param maxBank   degrees of lean at full lateral demand
	 */
	public static float bankTarget(Vec3d heading, Vec3d desire, float maxBank) {
		if (heading.lengthSquared() < 1e-8 || desire.lengthSquared() < 1e-8) return 0f;
		Vec3d fwd = heading.normalize();
		Vec3d right = ShipAttitude.levelRightOf(fwd);
		double lateral = desire.normalize().dotProduct(right);
		return (float) MathHelper.clamp(lateral * maxBank, -maxBank, maxBank);
	}

	/** Frame-rate independent approach along the shortest arc. */
	public static float approachAngle(float current, float target, float rate, float dt) {
		float delta = MathHelper.wrapDegrees(target - current);
		float k = 1f - (float) Math.exp(-rate * dt);
		return MathHelper.wrapDegrees(current + delta * k);
	}

	/**
	 * Point a mob along a heading, easing yaw and pitch instead of snapping.
	 *
	 * <p>Body yaw follows head yaw so the rendered hull actually turns about its own axis rather
	 * than sliding sideways with a fixed facing.
	 */
	public static void steer(LivingEntity mob, Vec3d heading, float turnRate, float dt) {
		if (heading.lengthSquared() < 1e-8) return;
		Vec3d dir = heading.normalize();
		float yaw = approachAngle(mob.getYaw(), yawOf(dir), turnRate, dt);
		float pitch = approachAngle(mob.getPitch(), pitchOf(dir), turnRate, dt);
		mob.setYaw(yaw);
		mob.setPitch(pitch);
		mob.setHeadYaw(yaw);
		mob.bodyYaw = yaw;
	}

	/** Same, for non-living entities (swarm anchors, hulls) that only carry yaw/pitch. */
	public static void steerEntity(Entity entity, Vec3d heading, float turnRate, float dt) {
		if (heading.lengthSquared() < 1e-8) return;
		Vec3d dir = heading.normalize();
		entity.setYaw(approachAngle(entity.getYaw(), yawOf(dir), turnRate, dt));
		entity.setPitch(approachAngle(entity.getPitch(), pitchOf(dir), turnRate, dt));
	}
}

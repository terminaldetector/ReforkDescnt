package com.terminaldetector.drmd.world.gravity;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Safe reorientation onto a local-gravity surface.
 *
 * <p>Vanilla AABBs stay world-axis-aligned. When "down" flips, leaving the entity where it
 * stood wedges the standing box into the mount face — players get forced into a ~1-block crawl
 * pose and mobs suffocate. This helper finds the surface along the new down, places feet on it,
 * then nudges along local up until the standing hitbox is clear.
 */
public final class GravityMount {
	private static final double FIND_RANGE = 4.5;
	private static final double FOOT_EPS = 0.02;
	private static final double NUDGE = 0.2;
	private static final int MAX_NUDGES = 16;

	private GravityMount() {}

	/**
	 * Place {@code entity} standing on the surface along {@code up}'s opposite.
	 * @return true if a surface was found and the entity was repositioned into free space
	 */
	public static boolean safeMount(LivingEntity entity, Vec3d up) {
		if (up.lengthSquared() < 1e-6) return false;
		up = up.normalize();
		World world = entity.getWorld();
		Vec3d down = up.negate();

		Vec3d probeFrom = entity.getPos().add(up.multiply(Math.max(0.35, entity.getHeight() * 0.45)));
		BlockHitResult hit = raySurface(world, entity, probeFrom, down, FIND_RANGE);
		if (hit == null) {
			// Try from a few offsets so a body already clipped into a wall still finds the face.
			Vec3d side = Math.abs(up.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
			side = side.subtract(up.multiply(side.dotProduct(up)));
			if (side.lengthSquared() > 1e-6) {
				side = side.normalize().multiply(0.4);
				hit = raySurface(world, entity, probeFrom.add(side), down, FIND_RANGE);
				if (hit == null) hit = raySurface(world, entity, probeFrom.subtract(side), down, FIND_RANGE);
			}
		}
		if (hit == null) return false;

		Vec3d target = hit.getPos().add(up.multiply(FOOT_EPS));
		entity.setPose(EntityPose.STANDING);
		entity.setSwimming(false);
		entity.setSneaking(false);

		for (int i = 0; i < MAX_NUDGES; i++) {
			entity.requestTeleport(target.x, target.y, target.z);
			entity.setPosition(target.x, target.y, target.z);
			Box box = standingBoxAt(entity, target, up);
			if (world.isSpaceEmpty(entity, box.contract(1.0E-6))) {
				finish(entity);
				return true;
			}
			target = target.add(up.multiply(NUDGE));
		}

		// Last resort: keep the furthest nudge (most free) even if still tight — better than wall mush.
		entity.requestTeleport(target.x, target.y, target.z);
		entity.setPosition(target.x, target.y, target.z);
		finish(entity);
		return hasStandingClearance(entity, up);
	}

	/**
	 * True when a standing body at the entity fits with no block overlap.
	 *
	 * <p>Asked along the entity's own up, not the world's. This is the test that decides whether
	 * vanilla is allowed to fold a player into the crawl pose, and measuring it up the world Y while
	 * the player is stood on a wall asks whether there is 1.8 blocks of headroom above someone who is
	 * lying sideways. In a corridor there is not, so the answer was no, so the crawl was allowed —
	 * and once crawling the body is shorter still, so the answer stayed no. That is why it never
	 * recovered: the pilot was stuck a block tall for as long as the torch held them.
	 */
	public static boolean hasStandingClearance(LivingEntity entity) {
		return hasStandingClearance(entity, localUp(entity));
	}

	public static boolean hasStandingClearance(LivingEntity entity, Vec3d up) {
		return entity.getWorld().isSpaceEmpty(entity,
				standingBoxAt(entity, entity.getPos(), up).contract(1.0E-6));
	}

	/** The up this entity is actually standing against — its mount, or world up if it has none. */
	private static Vec3d localUp(LivingEntity entity) {
		Vec3d up = com.terminaldetector.drmd.world.LocalOrientation.getUp(entity.getUuid());
		return up.lengthSquared() < 1e-6 ? new Vec3d(0, 1, 0) : up.normalize();
	}

	private static void finish(LivingEntity entity) {
		entity.fallDistance = 0f;
		entity.setVelocity(Vec3d.ZERO);
		entity.velocityModified = true;
		entity.setOnGround(true);
		entity.setPose(EntityPose.STANDING);
		entity.setSwimming(false);
	}

	/**
	 * The volume a standing body occupies with its feet at {@code feet} and its head up {@code up}.
	 *
	 * <p>Minecraft boxes are world-axis-aligned whichever way the body points, so this is the AABB of
	 * the standing capsule rather than the capsule: the segment from feet to head, padded by the body
	 * radius on the axes the body is <em>not</em> lying along. The padding on axis i is
	 * {@code hw · √(1 − up_i²)} — the width of a disc of radius {@code hw} normal to {@code up}, seen
	 * down that axis.
	 *
	 * <p>On a level floor that is exactly the vanilla box and nothing changes, which is the rule the
	 * whole feature is built on: up = (0,1,0) gives zero padding on Y and {@code hw} on X and Z.
	 */
	private static Box standingBoxAt(LivingEntity entity, Vec3d feet, Vec3d up) {
		return standingBox(feet, up, entity.getWidth() / 2.0,
				entity.getDimensions(EntityPose.STANDING).height());
	}

	/** The geometry on its own, so it can be checked without a world or an entity. */
	public static Box standingBox(Vec3d feet, Vec3d up, double halfWidth, double height) {
		Vec3d u = up.lengthSquared() < 1e-6 ? new Vec3d(0, 1, 0) : up.normalize();
		Vec3d head = feet.add(u.multiply(height));
		double px = halfWidth * Math.sqrt(Math.max(0, 1 - u.x * u.x));
		double py = halfWidth * Math.sqrt(Math.max(0, 1 - u.y * u.y));
		double pz = halfWidth * Math.sqrt(Math.max(0, 1 - u.z * u.z));
		return new Box(
				Math.min(feet.x, head.x) - px, Math.min(feet.y, head.y) - py, Math.min(feet.z, head.z) - pz,
				Math.max(feet.x, head.x) + px, Math.max(feet.y, head.y) + py, Math.max(feet.z, head.z) + pz);
	}

	private static BlockHitResult raySurface(World world, LivingEntity entity, Vec3d from, Vec3d down, double range) {
		var hit = world.raycast(new RaycastContext(
				from, from.add(down.multiply(range)),
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				entity));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}
}

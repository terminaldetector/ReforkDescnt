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
			Box box = standingBoxAt(entity, target);
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
		return world.isSpaceEmpty(entity, entity.getBoundingBox().contract(1.0E-6));
	}

	/** True when a standing AABB at the entity fits with no block overlap. */
	public static boolean hasStandingClearance(LivingEntity entity) {
		return entity.getWorld().isSpaceEmpty(entity, standingBoxAt(entity, entity.getPos()).contract(1.0E-6));
	}

	private static void finish(LivingEntity entity) {
		entity.fallDistance = 0f;
		entity.setVelocity(Vec3d.ZERO);
		entity.velocityModified = true;
		entity.setOnGround(true);
		entity.setPose(EntityPose.STANDING);
		entity.setSwimming(false);
	}

	private static Box standingBoxAt(LivingEntity entity, Vec3d feet) {
		float w = entity.getWidth();
		float h = entity.getDimensions(EntityPose.STANDING).height();
		double hw = w / 2.0;
		return new Box(feet.x - hw, feet.y, feet.z - hw, feet.x + hw, feet.y + h, feet.z + hw);
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

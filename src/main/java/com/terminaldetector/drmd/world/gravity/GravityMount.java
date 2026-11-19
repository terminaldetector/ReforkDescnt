package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Clearance for local gravity frame changes and the physical box shared with movement. */
public final class GravityMount {
    private static final double EPS = 1e-6;
    private GravityMount() {}

    /** Plan without moving the player, using the current position or a checked local pivot. */
    static Optional<Vec3d> framePosition(PlayerEntity player, Vec3d oldUp, Vec3d newUp) {
        var world = player.getWorld();
        var access = WorldAccess.of(world);
        var loaded = new HashMap<D6ChunkPos, Boolean>();
        var feet = player.getPos();
        var dimensions = player.getDimensions(player.getPose());
        double radius = dimensions.width() * .5;
        var nextBox = standingBox(feet, newUp, radius, dimensions.height());
        if (clear(player, nextBox, access, loaded)) return Optional.of(feet);

        var currentBox = player.getBoundingBox();
        // Rotating about the feet can add up to one body radius on the support side.
        // Check the entire old body's step and the new body's destination before publishing either.
        double lift = radius + .04;
        for (int i = 1; i <= 6; i++) {
            var step = oldUp.multiply(lift * i / 6);
            if (!clear(player, currentBox.stretch(step), access, loaded)) break;
            if (clear(player, nextBox.offset(step), access, loaded)) return Optional.of(feet.add(step));
        }
        // On a ceiling, turning about the feet can put the entire upright body above the
        // support. Turn around the occupied body's centre to keep it on the same side.
        var pivot = oldUp.subtract(newUp).multiply(dimensions.height() * .5);
        var turned = nextBox.offset(pivot);
        if (clear(player, currentBox.union(turned), access, loaded)) return Optional.of(feet.add(pivot));
        return Optional.empty();
    }

    private static boolean clear(PlayerEntity player, Box box, WorldAccess access, Map<D6ChunkPos, Boolean> loaded) {
        var world = player.getWorld();
        return box.minY >= world.getBottomY() && box.maxY <= world.getTopY() && world.getWorldBorder().contains(box)
            && loadedSpace(world, box, access, loaded) && world.isSpaceEmpty(player, box.contract(EPS));
    }

    static boolean loadedSpace(World world, Box box) {
        return loadedSpace(world, box, WorldAccess.of(world), new HashMap<>());
    }

    private static boolean loadedSpace(World world, Box box, WorldAccess access, Map<D6ChunkPos, Boolean> loaded) {
        var halo = box.expand(1);
        int x0=MathHelper.floor(halo.minX)>>4, x1=MathHelper.floor(Math.nextDown(halo.maxX))>>4;
        int y0=MathHelper.floor(halo.minY)>>4, y1=MathHelper.floor(Math.nextDown(halo.maxY))>>4;
        int z0=MathHelper.floor(halo.minZ)>>4, z1=MathHelper.floor(Math.nextDown(halo.maxZ))>>4;
        if ((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>64) return false;
        for(int x=x0;x<=x1;x++) for(int y=y0;y<=y1;y++) for(int z=z0;z<=z1;z++){
            var cube=new D6ChunkPos(x,y,z);
            if(CubicAvailability.withinHeight(world,cube)&&!loaded.computeIfAbsent(cube,access::isVolumeAvailable))return false;
        }
        return true;
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
        var box = standingBoxAt(entity, entity.getPos(), up);
		return loadedSpace(entity.getWorld(), box) && entity.getWorld().isSpaceEmpty(entity, box.contract(EPS));
	}

	/** The up this entity is actually standing against — its mount, or world up if it has none. */
	private static Vec3d localUp(LivingEntity entity) {
		Vec3d up = entity instanceof net.minecraft.entity.player.PlayerEntity player && FootGravitySystem.isActive(player)
                ? FootGravitySystem.getUp(player) : com.terminaldetector.drmd.world.LocalOrientation.getUp(entity.getUuid());
		return up.lengthSquared() < 1e-6 ? new Vec3d(0, 1, 0) : up.normalize();
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

}

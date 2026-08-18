package com.terminaldetector.drmd.world.portal.mirror;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Reflects a velocity off a mirror plane, and derives that plane's outward normal from a block's
 * stored FACING.
 */
public final class MirrorReflection {
	private MirrorReflection() {}

	/**
	 * v' = v - 2(v·n)n. Matches ImmPtl's own {@code Mirror.mirroredVec(vec, normal)} —
	 * {@code vec.add(normal.scale(vec.dot(normal) * -2))} is the same formula, read directly from
	 * {@code qouteall.imm_ptl.core.portal.Mirror} in the uploaded source — so a laser bouncing off a
	 * DRMD mirror block and ImmPtl's own live reflection agree on what "reflect" means.
	 */
	public static Vec3d reflect(Vec3d velocity, Vec3d normal) {
		Vec3d n = normal.normalize();
		return velocity.subtract(n.multiply(2 * velocity.dotProduct(n)));
	}

	/**
	 * Outward face normal from a block's stored FACING.
	 *
	 * <p>Deliberately not {@code facing.getOpposite()}: a laser has to be travelling toward the face
	 * to hit it at all, so the reflection normal points the same way FACING already does. This is the
	 * opposite derivation from a gravity block's "down," which points back toward its mount surface —
	 * copying that idiom here would silently reflect every bounce through the wrong side of the block.
	 */
	public static Vec3d normalFor(Direction facing) {
		return Vec3d.of(facing.getVector());
	}
}

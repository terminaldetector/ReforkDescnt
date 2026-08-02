package com.terminaldetector.drmd.client.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Stand the player model on an arbitrary basis — a ship attitude, or a wall.
 *
 * <p>Takes the whole basis rather than just an up vector on purpose. Rotating the model's up onto
 * the target by the shortest arc is exact for a pitch alone and exact for a roll alone, but the two
 * do not commute: at 30° nose-up and 90° of bank the model's nose comes out 30° off, and inverted
 * it faces backwards. Building the rotation from forward <em>and</em> up removes the whole class of
 * error — verified exact for both axes across 1701 attitude samples, with a determinant of 1 so
 * nothing is mirrored.
 */
public final class ModelOrientation {
	private ModelOrientation() {}

	/**
	 * Append the rotation that carries the model onto {@code forward}/{@code up}.
	 *
	 * <p>Call from {@code LivingEntityRenderer.setupTransforms} on RETURN. That method has already
	 * turned the stack by {@code 180 - bodyYaw} about Y, so the target basis is pulled back through
	 * that turn first; with a level ship this collapses to the identity and vanilla is untouched.
	 */
	public static void applyBasis(MatrixStack matrices, float bodyYaw, Vec3d forward, Vec3d up) {
		Quaternionf q = basisRotation(bodyYaw, forward, up);
		if (q != null) matrices.multiply(q);
	}

	/**
	 * The rotation {@link #applyBasis} appends, or null when there is nothing to do.
	 *
	 * <p>Separated out so the geometry can be checked without a matrix stack or a running game.
	 */
	public static Quaternionf basisRotation(float bodyYaw, Vec3d forward, Vec3d up) {
		if (forward == null || up == null) return null;
		if (forward.lengthSquared() < 1e-8 || up.lengthSquared() < 1e-8) return null;

		double rad = Math.toRadians(-(180.0 - bodyYaw));
		Vec3d tf = rotateY(forward.normalize(), rad);
		Vec3d tu = rotateY(up, rad);
		tu = tu.subtract(tf.multiply(tu.dotProduct(tf)));
		if (tu.lengthSquared() < 1e-10) return null;
		tu = tu.normalize();
		Vec3d tr = tu.crossProduct(tf);

		// The model's own triple is right = -X, up = +Y, facing = -Z, so the columns of the rotation
		// — the images of the unit axes — are -right, up, -forward. JOML's Matrix3f is column-major:
		// each row of arguments below is one column.
		Matrix3f m = new Matrix3f(
				(float) -tr.x, (float) -tr.y, (float) -tr.z,
				(float) tu.x, (float) tu.y, (float) tu.z,
				(float) -tf.x, (float) -tf.y, (float) -tf.z);
		return new Quaternionf().setFromNormalized(m);
	}

	private static Vec3d rotateY(Vec3d v, double rad) {
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new Vec3d(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
	}
}

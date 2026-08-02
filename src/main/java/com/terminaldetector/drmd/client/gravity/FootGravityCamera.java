package com.terminaldetector.drmd.client.gravity;

import com.terminaldetector.drmd.client.DescentClientState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Stabilizes the view so local gravity UP reads as screen-up —
 * soft lerp, no ship attitude freelook.
 */
public final class FootGravityCamera {
	private static Vec3d visualUp = new Vec3d(0, 1, 0);

	private FootGravityCamera() {}

	public static void tickClient() {
		Vec3d target = DescentClientState.footGravity
				? new Vec3d(DescentClientState.localUx, DescentClientState.localUy, DescentClientState.localUz)
				: new Vec3d(0, 1, 0);
		if (target.lengthSquared() < 1e-6) target = new Vec3d(0, 1, 0);
		target = target.normalize();
		double blend = DescentClientState.footGravity ? 0.22 : 0.28;
		visualUp = visualUp.add(target.subtract(visualUp).multiply(blend));
		if (visualUp.lengthSquared() < 1e-8) visualUp = target;
		else visualUp = visualUp.normalize();
	}

	public static void apply(MatrixStack matrices) {
		if (DescentClientState.enabled) return;
		Vec3d up = visualUp;
		if (isNearWorldUp(up) && !DescentClientState.footGravity) return;

		Vec3d worldUp = new Vec3d(0, 1, 0);
		double dot = MathHelper.clamp(worldUp.dotProduct(up), -1.0, 1.0);
		Vec3d axis = worldUp.crossProduct(up);
		if (axis.lengthSquared() < 1e-10) {
			if (dot < 0) {
				// Ceiling: 180° about view-forward proxy (X)
				matrices.multiply(new Quaternionf().rotationXYZ((float) Math.PI, 0, 0));
			}
			return;
		}
		axis = axis.normalize();
		float angle = (float) Math.acos(dot);
		matrices.multiply(new Quaternionf().fromAxisAngleRad(
				new Vector3f((float) axis.x, (float) axis.y, (float) axis.z), angle));
	}

	public static boolean isNearWorldUp(Vec3d up) {
		return up.squaredDistanceTo(0, 1, 0) < 0.0025;
	}

	public static void reset() {
		visualUp = new Vec3d(0, 1, 0);
	}
}

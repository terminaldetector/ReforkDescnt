package com.terminaldetector.drmd.client.gravity;

import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Mouse look in the surface's frame while a gravity torch owns your "down".
 *
 * <p>Vanilla look is yaw about world Y and pitch about the world horizon. Stood on a wall that is
 * wrong in the worst possible way: with local up horizontal, projecting a world yaw onto the
 * surface collapses the whole circle of headings onto a single axis, so the pilot can only ever
 * face two directions and turning does nothing in between. Here yaw turns about local up and pitch
 * about local right, which on a floor reduces to exactly the vanilla behaviour — the wall case is
 * the general one, not a special case bolted on.
 */
public final class FootLook {
	/** Vanilla's degrees-per-count, matched so sensitivity feels identical on and off a wall. */
	private static final double SENSITIVITY = 0.15;
	/** Keeping local pitch off ±90° keeps the camera roll defined. */
	private static final double PITCH_LIMIT = 89.0;

	private FootLook() {}

	public static void applyMouse(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
		Vec3d up = FootGravityCamera.visualUp();
		if (up.lengthSquared() < 1e-6) up = new Vec3d(0, 1, 0);
		up = up.normalize();

		Vec3d f = player.getRotationVec(1f);
		if (f.lengthSquared() < 1e-8) return;
		f = f.normalize();

		// Yaw about local up. Minecraft yaw increases toward the pilot's right, which is a negative
		// rotation about up under the right-hand rule, hence the sign.
		f = ShipAttitude.rotate(f, up, -cursorDeltaX * SENSITIVITY);

		Vec3d right = up.crossProduct(f);
		if (right.lengthSquared() > 1e-10) {
			right = right.normalize();
			// Clamp the delta rather than the result, so shoving the mouse into the stop does not
			// keep accumulating rotation that snaps back the moment you ease off.
			double pitchNow = -Math.toDegrees(Math.asin(MathHelper.clamp(f.dotProduct(up), -1, 1)));
			double want = cursorDeltaY * SENSITIVITY;
			double applied = MathHelper.clamp(pitchNow + want, -PITCH_LIMIT, PITCH_LIMIT) - pitchNow;
			if (Math.abs(applied) > 1e-9) {
				f = ShipAttitude.rotate(f, right, applied);
			}
		}

		f = f.normalize();
		player.setYaw((float) Math.toDegrees(Math.atan2(-f.x, f.z)));
		player.setPitch((float) -Math.toDegrees(Math.asin(MathHelper.clamp(f.y, -1, 1))));
	}
}

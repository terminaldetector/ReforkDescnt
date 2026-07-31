package com.terminaldetector.drmd.client.gravity;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Wall walking, seen from the inside — the Prey trick.
 *
 * <p>Stepping onto a gravity torch's surface should read as the world rolling under you, not as
 * you being tipped over. So the local gravity up is rendered as screen-up: the view gets a
 * screen-space roll equal to the bank of that up vector against wherever the camera is pointed,
 * the exact same decomposition the ship uses for 6DoF.
 *
 * <p>The previous version built a world-space axis-angle rotation and multiplied it into a matrix
 * stack that was already in view space. The axis was therefore interpreted in the wrong basis and
 * the horizon tipped in a direction that depended on where you happened to be looking. It also
 * blended the up vector with a component lerp, which passes through the origin on a ceiling flip.
 */
public final class FootGravityCamera {
	/** Rotation rate of the visual up toward the field's up, per client tick. */
	private static final double BLEND_IN = 0.22;
	/** Slightly faster on the way out so leaving a field feels like release, not drift. */
	private static final double BLEND_OUT = 0.28;

	private static Vec3d visualUp = new Vec3d(0, 1, 0);

	private FootGravityCamera() {}

	/**
	 * Smoothed local up.
	 *
	 * <p>Both the camera roll and the mouse-look frame read this, so the surface you are being
	 * rotated onto is the same one your controls already answer to — no half-tick where the view has
	 * turned but the input has not.
	 */
	public static Vec3d visualUp() {
		return visualUp;
	}

	public static void tickClient() {
		Vec3d target = DescentClientState.footGravity
				? new Vec3d(DescentClientState.localUx, DescentClientState.localUy, DescentClientState.localUz)
				: new Vec3d(0, 1, 0);
		if (target.lengthSquared() < 1e-6) target = new Vec3d(0, 1, 0);
		double blend = DescentClientState.footGravity ? BLEND_IN : BLEND_OUT;
		visualUp = ShipAttitude.slerp(visualUp, target, blend);
	}

	/** True once the view is upright again and there is nothing left to correct. */
	public static boolean settled() {
		return !DescentClientState.footGravity && isNearWorldUp(visualUp);
	}

	/**
	 * Screen-space roll, in degrees, that puts local gravity up at the top of the screen.
	 *
	 * <p>Undefined when you look straight along your own up axis, which is why the look frame keeps
	 * local pitch off ±90°.
	 */
	public static float viewRoll(float tickDelta) {
		if (DescentClientState.enabled) return 0f;
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc != null ? mc.player : null;
		if (player == null || settled()) return 0f;
		float bank = ShipAttitude.bankDegrees(player.getRotationVec(tickDelta), visualUp);
		// F5 front view mirrors the scene, so the bank has to be mirrored with it.
		boolean front = mc.options != null && mc.options.getPerspective().isFrontView();
		return front ? -bank : bank;
	}

	public static void apply(MatrixStack matrices, float tickDelta) {
		float roll = viewRoll(tickDelta);
		if (Math.abs(roll) > 0.05f) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
		}
	}

	public static boolean isNearWorldUp(Vec3d up) {
		return up.squaredDistanceTo(0, 1, 0) < 0.0025;
	}

	public static void reset() {
		visualUp = new Vec3d(0, 1, 0);
	}
}

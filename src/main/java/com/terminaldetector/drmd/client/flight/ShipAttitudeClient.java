package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Client-owned Descent attitude — port of d6_client.lua CreateMove (D6_Cam_CM).
 *
 * Two-layer angular filter (author notes in GMod):
 *   TurnVel  — smooths mouse (≈ ft*8)
 *   AngVel   — body inertia (FA on ≈ ft*5, FA off ≈ ft*2)
 * Roll uses inertial RollVel around local Forward; bank soft-clamped ±180.
 */
public final class ShipAttitudeClient {
	private static final ShipAttitude ATT = new ShipAttitude();
	private static final float ROLL_SPEED = 175f;

	private static boolean primed;
	private static float turnPitch;
	private static float turnYaw;
	private static float angPitch;
	private static float angYaw;
	private static float rollVel;

	private ShipAttitudeClient() {}

	public static ShipAttitude get() {
		return ATT;
	}

	public static boolean isPrimed() {
		return primed;
	}

	public static void resetFromPlayer(ClientPlayerEntity player) {
		ATT.fromLook(player.getRotationVec(1f));
		primed = true;
		turnPitch = turnYaw = angPitch = angYaw = 0;
		rollVel = 0;
		DescentCamera.clear();
		applyToPlayer(player);
	}

	public static void clear() {
		primed = false;
		turnPitch = turnYaw = angPitch = angYaw = 0;
		rollVel = 0;
		DescentCamera.clear();
	}

	/**
	 * Mouse deltas once per render frame (Mouse.updateMouse → changeLookDirection).
	 * FrameTime-scaled Lerp rates match GMod CreateMove.
	 */
	public static void applyMouse(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
		if (!primed) resetFromPlayer(player);
		float dt = frameDt();
		float targetYaw = (float) (-cursorDeltaX * 0.15);
		float targetPitch = (float) (-cursorDeltaY * 0.15);

		float turnK = MathHelper.clamp(8f * dt, 0f, 1f);
		turnYaw = MathHelper.lerp(turnK, turnYaw, targetYaw);
		turnPitch = MathHelper.lerp(turnK, turnPitch, targetPitch);

		float angDamp = DescentClientState.flightAssist ? 5f : 2f;
		float angK = MathHelper.clamp(angDamp * dt, 0f, 1f);
		angYaw = MathHelper.lerp(angK, angYaw, turnYaw);
		angPitch = MathHelper.lerp(angK, angPitch, turnPitch);

		// Local-axis yaw then pitch: no world-up reference anywhere in the input path, so the nose
		// crosses straight up / straight down without gimbal lock, exactly like Descent.
		ATT.yawLocal(angYaw);
		ATT.pitchLocal(angPitch);
		applyToPlayer(player);
	}

	/** Every client tick while flying so roll inertia decays without held keys. */
	public static void tickRoll(ClientPlayerEntity player, float rollInput, float dt) {
		if (!primed) return;
		float target = rollInput * ROLL_SPEED;
		rollVel = MathHelper.lerp(MathHelper.clamp(5f * dt, 0f, 1f), rollVel, target);
		if (Math.abs(rollVel) > 0.01f) {
			// Unbounded: a Pyro can keep barrel-rolling in one direction forever.
			ATT.rollLocal(rollVel * dt);
		}
		applyToPlayer(player);
	}

	public static void decayRollVel(float t) {
		rollVel = MathHelper.lerp(t, rollVel, 0f);
	}

	public static void zeroRollVel() {
		rollVel = 0;
	}

	public static void level() {
		DescentCamera.beginLevel();
	}

	public static void applyToPlayer(ClientPlayerEntity player) {
		player.setYaw(ATT.yawDegrees());
		player.setPitch(clampPitch(ATT.pitchDegrees()));
		player.prevYaw = player.getYaw();
		player.prevPitch = player.getPitch();
		DescentClientState.roll = ATT.bankDegrees();
		DescentClientState.pitch = ATT.pitchDegrees();
		DescentClientState.attFx = (float) ATT.fx;
		DescentClientState.attFy = (float) ATT.fy;
		DescentClientState.attFz = (float) ATT.fz;
		DescentClientState.attUx = (float) ATT.ux;
		DescentClientState.attUy = (float) ATT.uy;
		DescentClientState.attUz = (float) ATT.uz;
		DescentClientState.attitudeValid = true;
	}

	/** Entity pitch is a network-encoded byte angle; the camera keeps the unclamped value. */
	private static float clampPitch(float p) {
		return MathHelper.clamp(p, -90f, 90f);
	}

	/** GMod FrameTime analogue from MC last frame duration (1.0 = one game tick). */
	private static float frameDt() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) return 1f / 60f;
		float ticks = mc.getRenderTickCounter().getLastFrameDuration();
		return MathHelper.clamp(ticks / 20f, 1f / 300f, 0.1f);
	}
}

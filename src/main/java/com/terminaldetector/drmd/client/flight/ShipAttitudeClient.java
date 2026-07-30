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
		// Snap render history only on prime — continuous snap caused F5 model hang.
		player.prevYaw = player.getYaw();
		player.prevPitch = player.getPitch();
		player.prevHeadYaw = player.headYaw;
		player.prevBodyYaw = player.bodyYaw;
	}

	public static void clear() {
		primed = false;
		turnPitch = turnYaw = angPitch = angYaw = 0;
		rollVel = 0;
		DescentCamera.clear();
	}

	/**
	 * Mouse deltas once per render frame (Mouse.updateMouse → changeLookDirection).
	 *
	 * <p>Accumulates raw degrees then drains with FrameTime lag (GMod TurnVel/AngVel feel)
	 * so total look rotation is preserved — the old “lerp toward delta then apply lerped”
	 * path ate most of the mouse and made first-person 360° feel broken vs F5 rear view.
	 */
	public static void applyMouse(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
		if (!primed) resetFromPlayer(player);
		float dt = frameDt();
		// Accumulate impulses (degrees this frame) — preserved total rotation.
		turnYaw += (float) (-cursorDeltaX * 0.15);
		turnPitch += (float) (-cursorDeltaY * 0.15);

		// Snappy Descent feel: drain most of the buffer each frame (FA keeps a little hull lag).
		float turnRate = DescentClientState.flightAssist ? 22f : 40f;
		float turnK = MathHelper.clamp(turnRate * dt, 0f, 1f);
		float stepYaw = turnYaw * turnK;
		float stepPitch = turnPitch * turnK;
		turnYaw -= stepYaw;
		turnPitch -= stepPitch;

		// Light body lag only — do not re-filter into a sticky angVel that keeps spinning.
		float angDamp = DescentClientState.flightAssist ? 18f : 36f;
		float angK = MathHelper.clamp(angDamp * dt, 0f, 1f);
		angYaw = MathHelper.lerp(angK, angYaw, stepYaw);
		angPitch = MathHelper.lerp(angK, angPitch, stepPitch);
		// Decay leftover when mouse stops so the ship does not keep drifting.
		if (Math.abs(stepYaw) < 1e-4f && Math.abs(stepPitch) < 1e-4f) {
			angYaw *= (1f - angK);
			angPitch *= (1f - angK);
		}

		// Local-axis yaw then pitch: no world-up reference — nose crosses poles without gimbal lock.
		// pitchLocal is around pilot's RIGHT (up×forward); negate so mouse-up raises the nose.
		ATT.yawLocal(angYaw);
		ATT.pitchLocal(-angPitch);
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
		float yaw = ATT.yawDegrees();
		float pitch = clampPitch(ATT.pitchDegrees());
		player.setYaw(yaw);
		player.setPitch(pitch);
		// Drive body with the ship so F5 does not lag behind the camera.
		// Do NOT snap prevYaw/prevPitch every mouse frame — that kills interpolation
		// and makes the model look frozen / hitching while the camera turns.
		player.headYaw = yaw;
		player.bodyYaw = yaw;
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

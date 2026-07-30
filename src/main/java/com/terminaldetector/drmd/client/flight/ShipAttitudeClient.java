package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Client-owned Descent attitude — mouse pitch/yaw rotate local ship axes (GMod-like).
 * Port of d6_client.lua Ang:RotateAroundAxis(Right/Up/Forward) with light turn smoothing.
 */
public final class ShipAttitudeClient {
	private static final ShipAttitude ATT = new ShipAttitude();
	private static boolean primed;
	private static float turnPitch;
	private static float turnYaw;
	private static float rollVel;

	private ShipAttitudeClient() {}

	public static ShipAttitude get() {
		return ATT;
	}

	public static void resetFromPlayer(ClientPlayerEntity player) {
		ATT.fromLook(player.getRotationVec(1f));
		primed = true;
		turnPitch = 0;
		turnYaw = 0;
		rollVel = 0;
		applyToPlayer(player);
	}

	public static void clear() {
		primed = false;
		turnPitch = 0;
		turnYaw = 0;
		rollVel = 0;
	}

	/** Apply raw mouse cursor deltas (same units as changeLookDirection). */
	public static void applyMouse(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
		if (!primed) resetFromPlayer(player);
		// Vanilla sensitivity already baked into deltas by Mouse.updateMouse
		float targetYaw = (float) (-cursorDeltaX * 0.15);
		float targetPitch = (float) (-cursorDeltaY * 0.15);
		// Soft lerp toward mouse intent (d6_client TurnVel)
		turnYaw = MathHelper.lerp(0.55f, turnYaw, targetYaw);
		turnPitch = MathHelper.lerp(0.55f, turnPitch, targetPitch);
		ATT.yawLocal(turnYaw);
		ATT.pitchLocal(turnPitch);
		applyToPlayer(player);
	}

	public static void applyRollInput(float rollInput, float dt) {
		if (!primed) return;
		// Inertial roll like GMod RollVel
		float target = rollInput * 220f;
		rollVel = MathHelper.lerp(Math.min(1f, 5f * dt), rollVel, target);
		if (Math.abs(rollVel) > 0.01f) {
			ATT.rollLocal(rollVel * dt);
		}
	}

	public static void level() {
		ATT.levelRoll();
		rollVel = 0;
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

	private static float clampPitch(float p) {
		return MathHelper.clamp(p, -90f, 90f);
	}
}

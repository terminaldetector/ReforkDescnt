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
 * Roll uses inertial RollVel around local Forward; integrated every render frame
 * so barrel rolls stay smooth at any refresh rate (not stepped at 20 Hz).
 */
public final class ShipAttitudeClient {
	private static final ShipAttitude ATT = new ShipAttitude();
	/** Default roll rate; the options screen overrides it. */
	private static final float ROLL_SPEED = 175f;

	private static boolean primed;
    private static java.lang.ref.WeakReference<ClientPlayerEntity> owner=new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<net.minecraft.client.world.ClientWorld> ownerWorld=new java.lang.ref.WeakReference<>(null);
    private static boolean owns(ClientPlayerEntity player){return player!=null&&owner.get()==player&&ownerWorld.get()==player.getWorld();}
    private static void bindOwner(ClientPlayerEntity player){owner=new java.lang.ref.WeakReference<>(player);ownerWorld=new java.lang.ref.WeakReference<>((net.minecraft.client.world.ClientWorld)player.getWorld());}
	public static int portalEpoch;
	private static float turnPitch;
	private static float turnYaw;
	private static float angPitch;
	private static float angYaw;
	private static float rollVel;
	/** Q/E stick, sampled on the client tick; consumed on render frames. */
	private static float rollInput;
	private static long lastIntegrateNs;

	private ShipAttitudeClient() {}

	public static ShipAttitude get() {
		return ATT;
	}

	public static boolean isPrimed() {
        var client=MinecraftClient.getInstance();return primed&&client!=null&&owns(client.player);
	}

	/** Instantaneous roll rate °/s — HUD / minimap can freeze heavy work while barrel-rolling. */
	public static float rollSpeed() {
		return rollVel;
	}

	public static void resetFromPlayer(ClientPlayerEntity player) {
        bindOwner(player);
		ATT.fromLook(player.getRotationVec(1f));
		primed = true;
		turnPitch = turnYaw = angPitch = angYaw = 0;
		rollVel = 0;
		rollInput = 0;
		lastIntegrateNs = 0;
		DescentCamera.clear();
		applyToPlayer(player);
	}

	public static void acceptPortalFrame(ClientPlayerEntity player, net.minecraft.util.math.Vec3d forward,
			net.minecraft.util.math.Vec3d up) {
        bindOwner(player);
		ATT.setForwardUp(forward, up);
		primed = true;
		DescentCamera.clear();
		applyToPlayer(player);
	}

	public static void clear() {
        owner.clear();ownerWorld.clear();
		primed = false;
		turnPitch = turnYaw = angPitch = angYaw = 0;
		rollVel = 0;
		rollInput = 0;
		lastIntegrateNs = 0;
		DescentCamera.clear();
	}

	/** Sampled once per client tick from Q/E — does not advance the basis. */
	public static void setRollInput(float input) {
		rollInput = MathHelper.clamp(input, -1f, 1f);
	}

	/**
	 * Mouse deltas once per render frame (Mouse.updateMouse → changeLookDirection).
	 * FrameTime-scaled Lerp rates match GMod CreateMove.
	 */
	public static void applyMouse(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
		if (!primed||!owns(player)) resetFromPlayer(player);
		float dt = frameDt();
		double gain = 0.15 * com.terminaldetector.drmd.client.config.DescentConfig.lookGain;
		float targetYaw = (float) (-cursorDeltaX * gain);
		float targetPitch = (float) (-cursorDeltaY * gain);

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

	/**
	 * Advance roll (and soft level) on the render clock.
	 *
	 * <p>Must not run on the 20 Hz client tick — that is what made barrel rolls staircase. Real
	 * frame dt from {@link System#nanoTime()} keeps the rate honest even if Camera.update is called
	 * more than once in a frame (second call sees ~0 dt).
	 */
	public static void integrateRender(ClientPlayerEntity player) {
        if(player==null)return;
        if(!primed||!owns(player))resetFromPlayer(player);
		long now = System.nanoTime();
		float dt;
		if (lastIntegrateNs == 0L) {
			dt = frameDt();
		} else {
			dt = (now - lastIntegrateNs) * 1e-9f;
		}
		lastIntegrateNs = now;
		dt = MathHelper.clamp(dt, 0f, 0.1f);
		if (dt < 1e-5f) return;

		float rate = com.terminaldetector.drmd.client.config.DescentConfig.rollRate > 0
				? com.terminaldetector.drmd.client.config.DescentConfig.rollRate
				: ROLL_SPEED;
		float target = rollInput * rate;
		rollVel = MathHelper.lerp(MathHelper.clamp(5f * dt, 0f, 1f), rollVel, target);
		if (Math.abs(rollVel) > 0.01f) {
			ATT.rollLocal(rollVel * dt);
		}

		if (DescentCamera.isLeveling()) {
			ATT.rollLocal(-ATT.bankDegrees() * MathHelper.clamp(12f * dt, 0f, 1f));
			decayRollVel(MathHelper.clamp(12f * dt, 0f, 1f));
			if (Math.abs(ATT.bankDegrees()) < 0.35f) {
				ATT.levelRoll();
				zeroRollVel();
				DescentCamera.finishLevel();
			}
		}

		applyToPlayer(player);
	}

	/** @deprecated Roll advances in {@link #integrateRender}; kept for call-site clarity. */
	@Deprecated
	public static void tickRoll(ClientPlayerEntity player, float rollInput, float dt) {
		setRollInput(rollInput);
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
        if(!owns(player)){resetFromPlayer(player);return;}
		float yaw = ATT.yawDegrees();
		player.setYaw(yaw);
		player.setPitch(clampPitch(ATT.pitchDegrees()));

		// Carry the previous yaw across the ±180 seam instead of pinning it to the new one.
		player.prevYaw = yaw - MathHelper.wrapDegrees(yaw - player.prevYaw);
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

	/** GMod FrameTime analogue from MC last frame duration (seconds). */
	private static float frameDt() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) return 1f / 60f;
		float ticks = mc.getRenderTickCounter().getLastFrameDuration();
		return MathHelper.clamp(ticks / 20f, 1f / 300f, 0.1f);
	}
}

package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Descent CalcView port (d6_client.lua D6_Cam_CV) adapted to Minecraft Camera.
 *
 * GMod applies full Angle (incl. roll) + CamLag / reactor micro-motion / engine vib /
 * ship-relative third person. MC Camera has no roll — bank stays on the view matrix;
 * this class owns position lag and third-person placement relative to ship axes.
 */
public final class DescentCamera {
	/** Source CamLag pullback scaled for MC blocks (intent: afterburner “mass”). */
	private static final double LAG_MAX = 0.38;
	private static final double DASH_KICK = 0.55;
	private static final double MICRO_MAX = 0.07;
	private static final double VIB_BASE = 0.012;
	private static final double VIB_AR = 0.028;
	/** Third person: GMod Forward*-100 + Up*22 → feel-matched MC distances. */
	private static final double TP_BACK = 5.5;
	private static final double TP_UP = 1.25;

	private static Vec3d camLag = Vec3d.ZERO;
	private static Vec3d dashKick = Vec3d.ZERO;
	private static float hudRoll;
	private static boolean leveling;
	private static long lastDashPulseMs;
	/** F5 front view mirrors the camera, so the bank has to be mirrored with it. */
	private static boolean mirroredView;

	private DescentCamera() {}

	public static void clear() {
		camLag = Vec3d.ZERO;
		dashKick = Vec3d.ZERO;
		hudRoll = 0;
		leveling = false;
	}

	/** Pulse opposite-dash camera kick (client dash front-edge). */
	public static void pulseDash(Vec3d worldDir) {
		if (worldDir == null || worldDir.lengthSquared() < 1e-8) return;
		long now = System.currentTimeMillis();
		if (now - lastDashPulseMs < 200) return;
		lastDashPulseMs = now;
		dashKick = worldDir.normalize().multiply(-DASH_KICK);
	}

	public static void beginLevel() {
		leveling = true;
	}

	public static boolean isLeveling() {
		return leveling;
	}

	/**
	 * Screen-space bank the world is rendered with, in degrees.
	 *
	 * <p>Exact — never smoothed. Together with the camera's yaw/pitch it has to reconstruct the ship
	 * basis bit for bit; any lag here would decouple the horizon from the hull, and near the poles
	 * (where yaw swings fast) it would read as the camera spinning on its own.
	 */
	public static float viewRoll() {
		if (!DescentClientState.enabled || !DescentClientState.attitudeValid) return 0f;
		float bank = ShipAttitudeClient.get().bankDegrees();
		return mirroredView ? -bank : bank;
	}

	/** Smoothed, wrap-safe bank for HUD gauges only. */
	public static float hudRoll() {
		return hudRoll;
	}

	/**
	 * Per-client-tick: CamLag / kick decay / soft roll level (CreateMove-adjacent).
	 */
	public static void tick(ClientPlayerEntity player, float dt) {
		if (!DescentClientState.enabled || !DescentClientState.attitudeValid) {
			clear();
			return;
		}
		ShipAttitude att = ShipAttitudeClient.get();
		Vec3d forward = att.forward();
		Vec3d vel = player.getVelocity();

		Vec3d lagTarget = Vec3d.ZERO;
		if (DescentClientState.alwaysRun && com.terminaldetector.drmd.client.config.DescentConfig.cameraShake) {
			double fwdSpd = vel.dotProduct(forward) * 20.0; // blocks/sec feel
			double pull = MathHelper.clamp(fwdSpd / 12.0, 0.0, 1.0) * LAG_MAX;
			lagTarget = forward.multiply(-pull);
		}
		lagTarget = lagTarget.add(dashKick);
		double lerpT = MathHelper.clamp(10.0 * dt, 0.0, 1.0);
		camLag = lerpVec(camLag, lagTarget, lerpT);
		dashKick = lerpVec(dashKick, Vec3d.ZERO, MathHelper.clamp(6.0 * dt, 0.0, 1.0));

		// Soft level (GMod double-tap R → Ang.r Lerp); X starts this
		if (leveling) {
			att.rollLocal(-att.bankDegrees() * MathHelper.clamp(12f * dt, 0f, 1f));
			ShipAttitudeClient.decayRollVel(MathHelper.clamp(12f * dt, 0f, 1f));
			if (Math.abs(att.bankDegrees()) < 0.35f) {
				att.levelRoll();
				ShipAttitudeClient.zeroRollVel();
				leveling = false;
			}
			ShipAttitudeClient.applyToPlayer(player);
		}

		// Lerp along the shortest arc so a barrel roll past ±180 does not whip the gauge around.
		float delta = MathHelper.wrapDegrees(att.bankDegrees() - hudRoll);
		hudRoll = MathHelper.wrapDegrees(hudRoll + delta * MathHelper.clamp(14f * dt, 0f, 1f));
	}

	/**
	 * Apply to Minecraft Camera after vanilla {@code Camera.update}.
	 * First person: eye + CamLag + micro + vib. Third person: ship-relative chase with clip.
	 */
	public static void apply(Camera camera, CameraAccess access, boolean thirdPerson, boolean inverseView, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		mirroredView = false;
		if (player == null || !DescentClientState.enabled || !DescentClientState.attitudeValid) return;

		ShipAttitude att = ShipAttitudeClient.get();
		Vec3d forward = att.forward();
		Vec3d up = att.up();
		Vec3d right = att.right();

		// Drive yaw/pitch from full ship attitude. No clamp: pitchDegrees() is an asin, already in
		// [-90, 90], and the residual bank from viewRoll() restores the remaining 360° of freedom.
		mirroredView = inverseView;
		if (inverseView) {
			access.drmd$setRotation(att.yawDegrees() + 180f, -att.pitchDegrees());
		} else {
			access.drmd$setRotation(att.yawDegrees(), att.pitchDegrees());
		}

		Vec3d eye = player.getLerpedPos(tickDelta).add(0, player.getStandingEyeHeight(), 0);
		double t = (player.getWorld().getTime() + tickDelta) + player.getId() * 0.37;

		Vec3d origin = eye.add(camLag);

		double spd = com.terminaldetector.drmd.client.config.DescentConfig.cameraShake ? player.getVelocity().length() * 20.0 : 0.0;
		double mmAmp = MathHelper.clamp(spd / 22.0, 0.0, 1.0) * MICRO_MAX;
		if (mmAmp > 0.004) {
			origin = origin
					.add(right.multiply(Math.sin(t * 8.3) * mmAmp))
					.add(up.multiply(Math.sin(t * 11.7) * mmAmp * 0.55));
		}

		boolean ar = DescentClientState.alwaysRun;
		double vibAmp = (ar ? VIB_AR : VIB_BASE) * MathHelper.clamp(spd / 14.0, 0.0, 1.0);
		if (vibAmp > 0.001) {
			origin = origin.add(
					right.multiply(Math.sin(t * 61) * vibAmp)
							.add(up.multiply(Math.sin(t * 67) * vibAmp))
							.add(forward.multiply(Math.sin(t * 53) * vibAmp)));
		}

		if (thirdPerson || inverseView) {
			// F5 front view sits ahead of the nose instead of behind the thruster.
			double along = inverseView ? TP_BACK : -TP_BACK;
			Vec3d back = forward.multiply(along).add(up.multiply(TP_UP));
			Vec3d want = origin.add(back);
			var hit = player.getWorld().raycast(new RaycastContext(
					origin, want,
					RaycastContext.ShapeType.VISUAL,
					RaycastContext.FluidHandling.NONE,
					player));
			if (hit.getType() != HitResult.Type.MISS) {
				Vec3d hp = hit.getPos();
				Vec3d n = new Vec3d(hit.getSide().getOffsetX(), hit.getSide().getOffsetY(), hit.getSide().getOffsetZ());
				want = hp.add(n.multiply(0.2));
			}
			access.drmd$setPos(want);
		} else {
			access.drmd$setPos(origin);
		}
	}

	/** FOV delta degrees while afterburning (Descent menu had FOV 60–130). */
	public static float fovBoost() {
		if (!DescentClientState.enabled || !com.terminaldetector.drmd.client.config.DescentConfig.cameraShake) return 0;
		float boost = 0;
		if (DescentClientState.alwaysRun) boost += 6f;
		// speed is blocks/second; the hull tops out near 28, so scale across that whole range
		// instead of pinning the stretch on at walking pace.
		boost += MathHelper.clamp(DescentClientState.speed / 28f, 0f, 1f) * 4f;
		return boost;
	}

	private static Vec3d lerpVec(Vec3d a, Vec3d b, double t) {
		return new Vec3d(
				MathHelper.lerp(t, a.x, b.x),
				MathHelper.lerp(t, a.y, b.y),
				MathHelper.lerp(t, a.z, b.z));
	}

	/** Narrow accessor surface used by CameraMixin (avoids leaking invokers elsewhere). */
	public interface CameraAccess {
		void drmd$setPos(Vec3d pos);
		void drmd$setRotation(float yaw, float pitch);
	}
}

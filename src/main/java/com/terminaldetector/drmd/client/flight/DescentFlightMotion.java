package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.flight.FlightSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Hull motion on the pilot's own client while 6DoF is on.
 *
 * <h2>Why the client simulates</h2>
 *
 * The GMod original runs one movement model that both sides execute against a shared input command,
 * so it does not matter which end you think of as authoritative. Minecraft has no such command: a
 * player's position is decided entirely by its own client, which walks the entity and reports where
 * it ended up, and the server only sanity-checks the claim. A design where the server integrates
 * and the client waits to be told its velocity therefore costs a full round trip on every input —
 * on a remote server the ship visibly sits still after you press thrust, and even locally it lags a
 * tick behind the stick.
 *
 * <p>So the client runs the same integrator, from the same inputs, and treats the server's value as
 * an authority to converge on rather than a command to obey. Thrust answers immediately; anything
 * the client could not know about — an energy cut, a collision the server resolved differently —
 * arrives as a correction that is blended out over a few ticks instead of a snap.
 *
 * <p>This also replaces {@code travel} outright. Vanilla's would apply air drag and gravity on top
 * of the flight model, and in creative it rewrites Y as {@code previousY * 0.6} every tick — each
 * of which quietly dismantles a 6DoF velocity vector.
 */
public final class DescentFlightMotion {
	/** Speed kept after scraping a surface; the rest is spent on the hull. */
	private static final double SCRAPE_KEEP = 0.86;
	/**
	 * Share of the gap to the server's velocity closed per tick.
	 *
	 * <p>Both ends run the same model on the same inputs, so in the ordinary case there is nothing
	 * to close and this does nothing. It exists for the cases the client cannot predict, and 0.25
	 * settles those inside ~10 ticks without the correction itself being visible as a jerk.
	 */
	private static final double CORRECTION = 0.25;

	/** Predicted hull velocity in blocks per SECOND — the flight model's own domain. */
	private static Vec3d predicted = Vec3d.ZERO;
	private static boolean primed;

	private DescentFlightMotion() {}

	/** True when this entity is the local pilot and the mod owns its movement this tick. */
	public static boolean drives(PlayerEntity player) {
		if (!DescentClientState.enabled) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player != player) return false;
		// A spectator has no collision and its own free flight; a passenger is the vehicle's problem.
		return !player.isSpectator() && !player.hasVehicle();
	}

	/** Latest authoritative velocity, in blocks/tick as it travels on the wire. */
	public static void onServerVelocity(float x, float y, float z) {
		Vec3d server = new Vec3d(x, y, z).multiply(DescentMod.TICKS_PER_SECOND);
		if (!primed) {
			predicted = server;
			primed = true;
			return;
		}
		predicted = predicted.add(server.subtract(predicted).multiply(CORRECTION));
	}

	public static void clear() {
		predicted = Vec3d.ZERO;
		primed = false;
	}

	public static void travel(PlayerEntity player) {
		// Creative flight cannot co-drive the hull. `allowFlying` is left alone, so switching 6DoF
		// off with H hands normal creative flight straight back for building.
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
		}

		predicted = integrate(predicted);

		Vec3d perTick = predicted.multiply(1.0 / DescentMod.TICKS_PER_SECOND);
		player.setVelocity(perTick);
		player.move(MovementType.SELF, perTick);

		// Entity.move already zeroes whichever axes the collision sweep blocked, which is the wall
		// stop; this only bleeds the speed that survived along the surface so a graze costs
		// something. The prediction is pulled back in step so it does not keep pushing into stone.
		if (player.horizontalCollision || player.verticalCollision) {
			Vec3d after = player.getVelocity().multiply(SCRAPE_KEEP);
			player.setVelocity(after);
			predicted = after.multiply(DescentMod.TICKS_PER_SECOND);
		}

		player.fallDistance = 0f;
	}

	/**
	 * One tick of the flight model, in blocks per second.
	 *
	 * <p>Deliberately the same shape as {@link FlightSystem#tick}: thrust along the ship basis,
	 * quadratic drag, Source-rate inertia, soft cap. It is a prediction of that, not a second
	 * opinion — anything it gets wrong is corrected by the authority within a few ticks.
	 */
	private static Vec3d integrate(Vec3d vel) {
		float dt = 1f / (float) DescentMod.TICKS_PER_SECOND;
		if (!DescentClientState.attitudeValid || !ShipAttitudeClient.isPrimed()) return vel;

		var att = ShipAttitudeClient.get();
		Vec3d look = att.forward();
		Vec3d up = att.up();
		Vec3d right = att.right();

		DescentKeybinds.sampleThrust();
		float fwd = DescentKeybinds.inputForward();
		float strafe = DescentKeybinds.inputStrafe();
		float vert = DescentKeybinds.inputVertical();
		boolean afterburner = DescentClientState.alwaysRun;
		int abTier = com.terminaldetector.drmd.flight.AfterburnerTiers.clamp(DescentClientState.afterburnerTier);
		// Descent afterburner cruise: hold R with idle stick still drives nose-forward.
		if (afterburner && Math.abs(fwd) < 0.01f && Math.abs(strafe) < 0.01f && Math.abs(vert) < 0.01f) {
			fwd = 1f;
		}
		boolean thrusting = Math.abs(fwd) > 0.01f || Math.abs(strafe) > 0.01f || Math.abs(vert) > 0.01f;

		float engAlloc = DescentClientState.allocEngines;
		float accelMult = afterburner
				? com.terminaldetector.drmd.flight.AfterburnerTiers.accelMult(abTier, engAlloc) : 1f;
		float speedMult = afterburner
				? com.terminaldetector.drmd.flight.AfterburnerTiers.speedMult(abTier, engAlloc) : 1f;

		double accel = DescentMod.su(DescentClientState.accel) * accelMult;
		Vec3d wish = look.multiply(fwd)
				.add(right.multiply(strafe * FlightSystem.STRAFE_MULT))
				.add(up.multiply(vert * FlightSystem.VERT_MULT));
		if (wish.lengthSquared() > 1e-6) {
			vel = vel.add(wish.normalize().multiply(accel * dt));
		}

		double speed = vel.length();
		if (speed > 1e-4) {
			double qDrag = DescentClientState.drag * speed * speed * DescentMod.UNIT_SCALE * dt;
			vel = vel.multiply(Math.max(0, 1.0 - qDrag / speed));
		}
		if (DescentClientState.flightAssist && !thrusting) {
			vel = vel.multiply(Math.max(0, 1.0 - FlightSystem.BRAKE_MULT * 60 * dt));
		}
		vel = vel.multiply(Math.pow(FlightSystem.INERTIA, dt * 60.0));

		double maxSpd = DescentMod.su(DescentClientState.maxSpeed) * speedMult;
		if (vel.length() > maxSpd) vel = vel.normalize().multiply(maxSpd);
		return vel;
	}
}

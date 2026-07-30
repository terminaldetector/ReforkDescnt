package com.terminaldetector.drmd.flight;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereBand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 6DOF flight integrator — port of d6_core.lua CFG + spool + FA + idle gravity + dash + grapple.
 * Source units are scaled via DescentMod.UNIT_SCALE for Minecraft playability.
 */
public final class FlightSystem {
	public static final float INERTIA = 0.968f;
	public static final float BRAKE_MULT = 0.020f;
	public static final float SPOOL_UP = 15f;
	public static final float SPOOL_DOWN = 4f;
	public static final float STRAFE_MULT = 0.80f; // GMod CFG.strafeMult
	public static final float VERT_MULT = 0.80f;   // GMod CFG.vertMult — Space/Ctrl ship-up
	public static final float WALL_BOUNCE = 0.5f;
	public static final float IDLE_GRAV_SEC = 45f;
	public static final float IDLE_GRAV_RAMP = 5f;
	public static final float MICRO_GRAV = 20f;
	/** @deprecated use {@link FlightSpeeds#DASH_IMPULSE} (blocks/tick). */
	@Deprecated
	public static final float DASH_VEL = 3200f;
	public static final float DASH_DUR = 0.18f;
	public static final float DASH_CD = 1.8f;
	public static final float HOOK_DIST = 2500f;
	public static final float HOOK_PULL = 3800f;
	public static final float ROLL_ACCEL = 220f;
	public static final float ROLL_DRAG = 4.5f;

	/** Client-side input snapshot synced each tick while flying. */
	public static final class InputState {
		public float forward, strafe, vertical, roll;
		public boolean dash, hook, alwaysRunToggle;
	}

	private static final java.util.Map<java.util.UUID, InputState> INPUTS = new java.util.concurrent.ConcurrentHashMap<>();

	private FlightSystem() {}

	public static InputState input(PlayerEntity player) {
		return INPUTS.computeIfAbsent(player.getUuid(), id -> new InputState());
	}

	public static void clearInput(java.util.UUID id) {
		INPUTS.remove(id);
	}

	public static void tick(ServerPlayerEntity player, DescentPlayerData data) {
		// Creative double-tap fly must stay OFF or PlayerEntity.travel kills thrusters.
		FlightMotion.suppressCreativeFly(player);

		float dt = 1f / 20f;
		InputState in = input(player);

		if (data.getDashCooldown() > 0) data.setDashCooldown(data.getDashCooldown() - dt);
		if (data.getDashTimer() > 0) data.setDashTimer(data.getDashTimer() - dt);

		boolean hasInput = Math.abs(in.forward) > 0.01f || Math.abs(in.strafe) > 0.01f
				|| Math.abs(in.vertical) > 0.01f || Math.abs(in.roll) > 0.01f
				|| in.dash || player.handSwinging;

		if (hasInput) {
			data.setIdleTimer(0f);
			data.setGravityFactor(0f);
		} else {
			data.setIdleTimer(data.getIdleTimer() + dt);
			if (data.getIdleTimer() >= IDLE_GRAV_SEC) {
				float t = Math.min(1f, (data.getIdleTimer() - IDLE_GRAV_SEC) / IDLE_GRAV_RAMP);
				data.setGravityFactor(t);
			}
		}

		// Always-Run energy drain
		if (data.isAlwaysRun()) {
			if (!EnergySystem.tryConsume(data, "engines", EnergySystem.ALWAYS_RUN_COST_PER_SEC * dt)) {
				data.setAlwaysRun(false);
			}
		}

		// Roll rate (client applies local roll into synced ship attitude)
		float rollVel = data.getRollVel() + in.roll * ROLL_ACCEL * dt;
		rollVel *= Math.pow(MathHelper.clamp(1f - ROLL_DRAG * dt, 0f, 1f), 1);
		data.setRollVel(rollVel);
		if (!data.hasShipAttitude()) {
			float roll = data.getRoll() + rollVel * dt;
			while (roll > 180f) roll -= 360f;
			while (roll < -180f) roll += 360f;
			data.setRoll(roll);
		}

		// Descent ship basis: W = thrust along nose, not flattened world-forward.
		Vec3d look = data.shipForward(player);
		Vec3d rolledUp = data.shipUp(player);
		// right = up × forward (RH) — matches Descent strafe; forward×up was inverted.
		Vec3d rolledRight = rolledUp.crossProduct(look);
		if (rolledRight.lengthSquared() < 1e-8) {
			rolledRight = new Vec3d(1, 0, 0);
			rolledUp = look.crossProduct(rolledRight).normalize();
			rolledRight = rolledUp.crossProduct(look).normalize();
		} else {
			rolledRight = rolledRight.normalize();
		}

		boolean thrusting = Math.abs(in.forward) > 0.01f || Math.abs(in.strafe) > 0.01f || Math.abs(in.vertical) > 0.01f;
		if (thrusting && player.getServer().getTicks() % 4 == 0) {
			com.terminaldetector.drmd.world.smoke.SmokeSystem.emit(
					player.getPos().subtract(look.multiply(0.8)),
					com.terminaldetector.drmd.world.smoke.SmokeSystem.Source.ENGINE,
					0.4f, 0.25f, 18);
		}
		float spool = data.getThrustSpool();
		if (thrusting) spool = Math.min(1f, spool + SPOOL_UP * dt);
		else spool = Math.max(0f, spool - SPOOL_DOWN * dt);
		data.setThrustSpool(spool);

		boolean afterburn = data.isAlwaysRun();
		// Atmospheric bands: mild feel only — never blow past elytra envelope (no-clip risk).
		boolean endVacuum = player.getWorld().getRegistryKey() == net.minecraft.world.World.END;
		AtmosphereBand band = AtmosphereBand.at(player.getWorld(), player.getY());
		float atmos = MathHelper.clamp(band.thrustScale, 0.85f, 1.2f);
		if (endVacuum) {
			data.setGravityFactor(0f);
		}

		// Per-axis thrust like Descent — caps in FlightSpeeds (~1.2× elytra, форсаж 4×).
		double a = FlightSpeeds.accelBlocksPerSec2(afterburn) * atmos * spool * dt;
		Vec3d vel = data.getFlightVelocity()
				.add(look.multiply(in.forward * a))
				.add(rolledRight.multiply(in.strafe * STRAFE_MULT * a))
				.add(rolledUp.multiply(in.vertical * VERT_MULT * a));

		// Thruster mode ignores station torches / generators — free 6DoF must not get
		// reoriented to wall UP (that kills spherical look and feels like "flight fell off").
		boolean onPyro = player.getVehicle() instanceof com.terminaldetector.drmd.entity.PyroShipEntity;
		com.terminaldetector.drmd.world.gravity.FootGravitySystem.clear(player);
		com.terminaldetector.drmd.world.LocalOrientation.setUp(player, new Vec3d(0, 1, 0));
		// World-down sink only while idle (GMod). While thrusting — pure free flight, no pull.
		Vec3d gravDir = new Vec3d(0, -1, 0);
		double g = 0.0;
		if (!endVacuum && !thrusting) {
			g = DescentMod.su(MICRO_GRAV) * (onPyro ? 0.25 : 1.0)
					+ DescentMod.su(data.getGravity()) * data.getGravityFactor() * (onPyro ? 0.0 : 1.0);
		}
		if (g > 0) vel = vel.add(gravDir.multiply(g * dt));

		// Dash — along current thrust wish, else straight out the nose (Descent afterburner kick).
		if (in.dash && data.getDashCooldown() <= 0 && EnergySystem.tryConsume(data, "engines", EnergySystem.DASH_COST)) {
			Vec3d dashDir = look;
			if (Math.abs(in.forward) > 0.01f || Math.abs(in.strafe) > 0.01f || Math.abs(in.vertical) > 0.01f) {
				dashDir = look.multiply(in.forward)
						.add(rolledRight.multiply(in.strafe * STRAFE_MULT))
						.add(rolledUp.multiply(in.vertical * VERT_MULT));
				if (dashDir.lengthSquared() > 1e-8) dashDir = dashDir.normalize();
				else dashDir = look;
			}
			vel = vel.add(dashDir.multiply(FlightSpeeds.DASH_IMPULSE));
			data.setDashCooldown(DASH_CD);
			data.setDashTimer(DASH_DUR);
			in.dash = false;
		}

		// Grapple / hook
		if (in.hook && !data.isHookActive()) {
			Vec3d eye = player.getEyePos();
			var hit = player.getWorld().raycast(new net.minecraft.world.RaycastContext(
					eye, eye.add(look.multiply(DescentMod.su(HOOK_DIST))),
					net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
					net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
			if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
				data.setHookActive(true);
				data.setHookPos(hit.getPos());
			}
			in.hook = false;
		} else if (!in.hook && data.isHookActive()) {
			// release when key up handled via packet toggle; keep pull while active
		}

		if (data.isHookActive()) {
			Vec3d to = data.getHookPos().subtract(player.getPos());
			double dist = to.length();
			double maxDist = DescentMod.su(HOOK_DIST);
			if (dist < DescentMod.su(55) || dist > maxDist * 1.2) {
				data.setHookActive(false);
			} else {
				vel = vel.add(to.normalize().multiply(DescentMod.su(HOOK_PULL) * dt));
			}
		}

		// Drag / Flight Assist — scaled by atmosphere air density
		double speed = vel.length();
		double dragK = data.getDrag() * band.airDrag;
		if (data.isFlightAssist()) {
			// Quadratic drag + linear brake when no input
			if (speed > 1e-4) {
				double qDrag = dragK * speed * speed * DescentMod.UNIT_SCALE * dt;
				vel = vel.multiply(Math.max(0, 1.0 - qDrag / speed));
			}
			if (!thrusting) {
				vel = vel.multiply(Math.max(0, 1.0 - BRAKE_MULT * 60 * dt));
			}
		} else {
			if (speed > 1e-4) {
				double qDrag = dragK * speed * speed * DescentMod.UNIT_SCALE * dt;
				vel = vel.multiply(Math.max(0, 1.0 - qDrag / speed));
			}
		}

		// Inertia retention (Source tick-rate based)
		double inertiaKeep = Math.pow(INERTIA, dt * 60.0);
		vel = vel.multiply(inertiaKeep);

		// Soft speed cap — cruise ≈ 1.2× elytra, форсаж = 4× elytra; spool makes it dynamic.
		double maxSpd = FlightSpeeds.maxBlocksPerTick(afterburn, spool) * atmos;
		if (vel.length() > maxSpd) vel = vel.normalize().multiply(maxSpd);

		// Soft world-column walls — stop silent void flight past min_y / topY
		vel = applyColumnSoftWall(player, vel);

		// Pre-move collision damping from last tick flags (takeoff-safe).
		vel = applyCollisionResponse(player, vel, thrusting);

		data.setFlightVelocity(vel);
		player.setNoGravity(true);
		player.noClip = false;
		player.setVelocity(vel);
		player.velocityModified = true;
		player.fallDistance = 0f;

		// HUD sync every other tick — every-tick sync was lagging attitude / flight feel.
		if (player.age % 2 == 0) {
			ModNetworking.syncPlayer(player, data);
		}
	}

	/** Full thruster arming — use everywhere instead of bare setEnabled(true). */
	public static void enable(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		// Hard exclusive switch: leave pedestrian / foot-gravity completely.
		com.terminaldetector.drmd.world.gravity.FootGravitySystem.clear(player.getUuid());
		com.terminaldetector.drmd.world.LocalOrientation.setUp(player, new Vec3d(0, 1, 0));
		data.setEnabled(true);
		data.ensureInit();
		data.setFlightVelocity(Vec3d.ZERO); // drop any legacy tunnel-speed vector
		data.setIdleTimer(0f);
		data.setGravityFactor(0f);
		data.setHookActive(false);
		rescueIntoColumn(player);
		FlightMotion.suppressCreativeFly(player);
		player.noClip = false;
		player.fallDistance = 0f;
		player.velocityModified = true;
		ModNetworking.syncPlayer(player, data);
		player.sendMessage(net.minecraft.text.Text.literal(
				"§b6DoF §aON §7— creative-fly off · WASD+Space/Ctrl · Q/E roll · H = пеший"), true);
	}

	/**
	 * Hard repair: force thrusters ON, clear foot gravity / hook / idle sink,
	 * re-sync so the client re-primes spherical attitude.
	 */
	public static void repair(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		com.terminaldetector.drmd.world.gravity.FootGravitySystem.clear(player.getUuid());
		com.terminaldetector.drmd.world.LocalOrientation.setUp(player, new Vec3d(0, 1, 0));
		data.setEnabled(true);
		data.ensureInit();
		data.setHookActive(false);
		data.setIdleTimer(0f);
		data.setGravityFactor(0f);
		data.setDashCooldown(0f);
		if (!data.hasShipAttitude()) {
			data.levelShipAttitude(player);
		}
		rescueIntoColumn(player);
		FlightMotion.suppressCreativeFly(player);
		player.fallDistance = 0f;
		player.velocityModified = true;
		ModNetworking.syncPlayer(player, data);
		player.sendMessage(net.minecraft.text.Text.literal(
				"§b6DoF §arepaired §7— creative-fly OFF, thrusters ON, WASD+Space/Ctrl, Q/E roll"), false);
	}

	public static void disable(ServerPlayerEntity player, DescentPlayerData data) {
		data.setEnabled(false);
		data.clearShipAttitude();
		data.setRoll(0);
		data.setRollVel(0);
		data.setFlightVelocity(Vec3d.ZERO);
		data.setHookActive(false);
		data.setThrustSpool(0f);
		data.setIdleTimer(0f);
		data.setGravityFactor(0f);
		player.setVelocity(Vec3d.ZERO);
		// Pedestrian default; foot-gravity may reclaim if standing in a field.
		player.setNoGravity(false);
		// Creative: allow vanilla fly again after leaving 6DoF (double-tap Space).
		if (player.getAbilities().creativeMode) {
			player.getAbilities().allowFlying = true;
			player.getAbilities().flying = false;
			player.sendAbilitiesUpdate();
		}
		ModNetworking.syncPlayer(player, data);
		player.sendMessage(net.minecraft.text.Text.literal(
				"§eРежим: ПЕШИЙ §7· Caps Lock / H — снова полёт"), true);
	}

	/** Pedestrian ↔ flight swap (server authority). */
	public static void toggle(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		if (data.isEnabled()) {
			disable(player, data);
			com.terminaldetector.drmd.world.gravity.FootGravitySystem.adoptAt(player, player.getPos());
			com.terminaldetector.drmd.world.gravity.FootGravitySystem.tick(player);
		} else {
			enable(player);
		}
	}

	public static void tryDash(ServerPlayerEntity player) {
		input(player).dash = true;
	}

	public static void toggleHook(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		if (data.isHookActive()) {
			data.setHookActive(false);
		} else {
			input(player).hook = true;
		}
	}

	/** Pull a player who already escaped the buildable column back inside (keep momentum). */
	private static void rescueIntoColumn(ServerPlayerEntity player) {
		int bot = player.getWorld().getBottomY();
		int top = bot + player.getWorld().getHeight() - 1;
		double y = player.getY();
		if (y >= bot + 4 && y <= top - 4) return;
		double clampedY = MathHelper.clamp(y, bot + 16.0, top - 16.0);
		player.requestTeleport(player.getX(), clampedY, player.getZ());
		DescentPlayerData data = DescentPlayerData.get(player);
		Vec3d vel = data.getFlightVelocity();
		double vy = y < bot + 4 ? Math.max(0.4, Math.abs(vel.y) * WALL_BOUNCE)
				: -Math.max(0.4, Math.abs(vel.y) * WALL_BOUNCE);
		Vec3d bounced = new Vec3d(vel.x * 0.85, vy, vel.z * 0.85);
		data.setFlightVelocity(bounced);
		player.setVelocity(bounced);
	}

	/**
	 * Axis-aware wall response. Floor contact must not cancel Space/ship-up takeoff.
	 */
	private static Vec3d applyCollisionResponse(ServerPlayerEntity player, Vec3d vel, boolean thrusting) {
		if (player.horizontalCollision && (vel.x * vel.x + vel.z * vel.z) > 0.0025) {
			vel = new Vec3d(vel.x * -WALL_BOUNCE, vel.y, vel.z * -WALL_BOUNCE);
		}
		if (!player.verticalCollision) return vel;
		if (player.isOnGround()) {
			// Resting / scraping floor: kill only downward sink; keep upward thrust.
			if (vel.y < 0) {
				vel = new Vec3d(vel.x, thrusting ? 0 : vel.y * -WALL_BOUNCE * 0.25, vel.z);
			}
			return vel;
		}
		// Ceiling / overhang while climbing
		if (vel.y > 0.02) {
			vel = new Vec3d(vel.x, -Math.abs(vel.y) * WALL_BOUNCE, vel.z);
		}
		return vel;
	}

	/**
	 * Bounce / clamp inside the real dimension column so 6DoF never drifts to Y≈−3800 void.
	 */
	private static Vec3d applyColumnSoftWall(ServerPlayerEntity player, Vec3d vel) {
		int bot = player.getWorld().getBottomY();
		int top = bot + player.getWorld().getHeight() - 1;
		double margin = 4.0;
		double lo = bot + margin;
		double hi = top - margin;
		double y = player.getY();
		if (y >= lo && y <= hi) {
			// Soft push near edges before punching through
			if (y < lo + 8.0 && vel.y < 0) {
				double t = (lo + 8.0 - y) / 8.0;
				return new Vec3d(vel.x, vel.y * (1.0 - 0.85 * t), vel.z);
			}
			if (y > hi - 8.0 && vel.y > 0) {
				double t = (y - (hi - 8.0)) / 8.0;
				return new Vec3d(vel.x, vel.y * (1.0 - 0.85 * t), vel.z);
			}
			return vel;
		}
		double clampedY = MathHelper.clamp(y, lo + 0.5, hi - 0.5);
		player.requestTeleport(player.getX(), clampedY, player.getZ());
		double vy;
		if (y < lo) vy = Math.max(Math.abs(vel.y) * WALL_BOUNCE, 0.35);
		else vy = -Math.max(Math.abs(vel.y) * WALL_BOUNCE, 0.35);
		return new Vec3d(vel.x * 0.85, vy, vel.z * 0.85);
	}
}

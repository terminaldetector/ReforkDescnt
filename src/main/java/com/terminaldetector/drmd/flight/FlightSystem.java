package com.terminaldetector.drmd.flight;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.atmosphere.AtmosphereBand;
import com.terminaldetector.drmd.world.gravity.GravityFields;
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
	public static final float STRAFE_MULT = 0.72f;
	public static final float VERT_MULT = 0.65f;
	public static final float WALL_BOUNCE = 0.5f;
	public static final float IDLE_GRAV_SEC = 45f;
	public static final float IDLE_GRAV_RAMP = 5f;
	public static final float MICRO_GRAV = 20f;
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
		/** Descent afterburner — held (not toggled). Drives cruise when stick is idle. */
		public boolean afterburner;
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
		// Spectators own their own movement entirely — hijacking travel there strands them.
		if (player.isSpectator()) {
			if (player.hasNoGravity()) player.setNoGravity(false);
			return;
		}
		float dt = 1f / 20f;
		InputState in = input(player);

		// Creative double-tap space re-arms flying; that path shreds 6DoF (Y*0.6 + flySpeed outside
		// travel). Keep allowFlying so H-off restores creative build flight. Client also clears
		// flying at tickMovement HEAD (ClientPlayerEntityMixin).
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
			player.sendAbilitiesUpdate();
		}

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

		// Descent afterburner cruise: hold R → burn energy (tier cost), boost, idle nose thrust.
		int abTier = data.getAfterburnerTier();
		boolean afterburner = in.afterburner;
		if (afterburner) {
			if (!EnergySystem.tryConsume(data, "engines", AfterburnerTiers.costPerSec(abTier) * dt)) {
				afterburner = false;
				in.afterburner = false;
			}
		}
		data.setAlwaysRun(afterburner);
		if (afterburner && Math.abs(in.forward) < 0.01f
				&& Math.abs(in.strafe) < 0.01f && Math.abs(in.vertical) < 0.01f) {
			// Corridor cruise — afterburner alone pushes forward like original Descent.
			in.forward = 1f;
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

		Vec3d look = data.shipForward(player);
		Vec3d rolledUp = data.shipUp(player);
		Vec3d rolledRight = look.crossProduct(rolledUp);
		if (rolledRight.lengthSquared() < 1e-8) {
			rolledRight = new Vec3d(1, 0, 0);
			rolledUp = rolledRight.crossProduct(look).normalize();
		} else {
			rolledRight = rolledRight.normalize();
		}

		boolean thrusting = Math.abs(in.forward) > 0.01f || Math.abs(in.strafe) > 0.01f || Math.abs(in.vertical) > 0.01f;
		if (thrusting && player.getServer().getTicks() % 4 == 0) {
			com.terminaldetector.drmd.world.smoke.SmokeSystem.emit(
					player.getPos().subtract(look.multiply(0.8)),
					com.terminaldetector.drmd.world.smoke.SmokeSystem.Source.ENGINE,
					afterburner ? 0.7f : 0.4f, afterburner ? 0.4f : 0.25f, afterburner ? 28 : 18);
		}
		float spool = data.getThrustSpool();
		if (thrusting) spool = Math.min(1f, spool + SPOOL_UP * dt);
		else spool = Math.max(0f, spool - SPOOL_DOWN * dt);
		data.setThrustSpool(spool);

		float engAlloc = data.getAllocEngines();
		float accelMult = afterburner ? AfterburnerTiers.accelMult(abTier, engAlloc) : 1f;
		float speedMult = afterburner ? AfterburnerTiers.speedMult(abTier, engAlloc) : 1f;

		// Atmospheric bands: thin air / near-space / End vacuum → less drag, more thrust
		var level = com.terminaldetector.drmd.world.level.WorldLevels.at(player.getY());
		boolean endVacuum = player.getWorld().getRegistryKey() == net.minecraft.world.World.END
				|| level == com.terminaldetector.drmd.world.level.WorldLevels.Level.END
				|| level == com.terminaldetector.drmd.world.level.WorldLevels.Level.ORBITAL;
		AtmosphereBand band = AtmosphereBand.at(player.getWorld(), player.getY());
		accelMult *= band.thrustScale;
		speedMult *= MathHelper.lerp(1f - band.airDrag, 1f, 1.15f);
		boolean psychedelicVoid = com.terminaldetector.drmd.world.DescentWorldState
				.get(player.getServerWorld()).isPsychedelic();
		if (endVacuum || psychedelicVoid) {
			// No idle gravity sink in End / orbital / psychedelic void — thrusters only
			data.setGravityFactor(0f);
		}

		double accel = DescentMod.su(data.getAccel()) * accelMult * spool;
		Vec3d wish = look.multiply(in.forward)
				.add(rolledRight.multiply(in.strafe * STRAFE_MULT))
				.add(rolledUp.multiply(in.vertical * VERT_MULT));
		if (wish.lengthSquared() > 1e-6) wish = wish.normalize().multiply(accel * dt);

		Vec3d vel = data.getFlightVelocity().add(wish);

		// Micro gravity + idle gravity — NEVER from station torches while piloting Pyro GX
		boolean onPyro = player.getVehicle() instanceof com.terminaldetector.drmd.entity.PyroShipEntity;
		GravityFields.Sample field = null;
		Vec3d gravDir;
		double g = endVacuum ? 0.0
				: DescentMod.su(MICRO_GRAV) + DescentMod.su(data.getGravity()) * data.getGravityFactor();
		if (!onPyro && !endVacuum) {
			field = GravityFields.sample(player.getWorld(), player.getPos());
		}
		if (field != null) {
			gravDir = field.downDir();
			g += DescentMod.su(data.getGravity()) * field.strength() * 0.55;
			com.terminaldetector.drmd.world.LocalOrientation.setUp(player.getUuid(), field.upDir());
		} else {
			gravDir = onPyro
					? new Vec3d(0, -1, 0) // ship ignores local station UP
					: com.terminaldetector.drmd.world.LocalOrientation.gravityDir(player.getUuid());
			if (onPyro) {
				// Keep micro-g along world down only; no torch reorientation
				g = endVacuum ? 0.0 : DescentMod.su(MICRO_GRAV) * 0.25;
			}
		}
		if (g > 0) vel = vel.add(gravDir.multiply(g * dt));

		// Dash
		if (in.dash && data.getDashCooldown() <= 0 && EnergySystem.tryConsume(data, "engines", EnergySystem.DASH_COST)) {
			Vec3d dashDir = wish.lengthSquared() > 1e-6 ? wish.normalize() : look;
			vel = vel.add(dashDir.multiply(DescentMod.su(DASH_VEL)));
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

		// Soft speed cap
		double maxSpd = DescentMod.su(data.getMaxSpeed()) * speedMult;
		if (vel.length() > maxSpd) vel = vel.normalize().multiply(maxSpd);

		data.setFlightVelocity(vel);

		// Apply to player — disable vanilla gravity while 6DOF on.
		//
		// The integrator above runs in blocks per SECOND (Source velocities are per-second), so the
		// hand-off to Minecraft has to divide by the tick rate. Feeding blocks/s straight into
		// setVelocity is what made the hull cap out at 27 blocks/tick — 550 m/s, outrunning chunk
		// loading and every collision sweep in the game.
		player.setNoGravity(true);
		player.setVelocity(vel.multiply(1.0 / DescentMod.TICKS_PER_SECOND));
		player.velocityModified = true;
		player.fallDistance = 0f;

		// Simple wall bounce
		if (player.horizontalCollision || player.verticalCollision) {
			Vec3d bounced = data.getFlightVelocity().multiply(-WALL_BOUNCE);
			if (player.horizontalCollision) {
				bounced = new Vec3d(bounced.x, data.getFlightVelocity().y * WALL_BOUNCE, bounced.z);
			}
			if (player.verticalCollision) {
				bounced = new Vec3d(data.getFlightVelocity().x * WALL_BOUNCE, -Math.abs(data.getFlightVelocity().y) * WALL_BOUNCE, data.getFlightVelocity().z * WALL_BOUNCE);
			}
			data.setFlightVelocity(data.getFlightVelocity().multiply(WALL_BOUNCE).add(bounced).multiply(0.5));
		}

		// Half-rate sync while flying — every-tick SyncPayload was a hitch source during rolls.
		if ((player.age & 1) == 0) {
			ModNetworking.syncPlayer(player, data);
		}
	}

	public static void disable(ServerPlayerEntity player, DescentPlayerData data) {
		data.setEnabled(false);
		data.clearShipAttitude();
		data.setRoll(0);
		data.setRollVel(0);
		data.setFlightVelocity(Vec3d.ZERO);
		data.setHookActive(false);
		// Foot gravity may immediately reclaim noGravity; default to vanilla until then
		if (!com.terminaldetector.drmd.world.gravity.FootGravitySystem.isActive(player.getUuid())) {
			player.setNoGravity(false);
		}
		// Restore creative fly permission so H-off can build again.
		if (player.getAbilities().creativeMode && !player.getAbilities().allowFlying) {
			player.getAbilities().allowFlying = true;
			player.sendAbilitiesUpdate();
		}
		ModNetworking.syncPlayer(player, data);
	}

	public static void toggle(ServerPlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		if (data.isEnabled()) {
			disable(player, data);
			// Re-enter foot gravity if standing in a torch/generator field
			com.terminaldetector.drmd.world.gravity.FootGravitySystem.adoptAt(player, player.getPos());
			com.terminaldetector.drmd.world.gravity.FootGravitySystem.tick(player);
		} else {
			enable(player, data);
		}
	}

	/** Force 6DoF on and sync — join path / /d6 / void ending. */
	public static void enable(ServerPlayerEntity player, DescentPlayerData data) {
		com.terminaldetector.drmd.world.gravity.FootGravitySystem.clear(player.getUuid());
		data.setEnabled(true);
		data.ensureInit();
		boolean dirty = false;
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
			dirty = true;
		}
		// Hard-block creative double-tap while armed — client mixin mirrors this each tick.
		if (player.getAbilities().allowFlying) {
			player.getAbilities().allowFlying = false;
			dirty = true;
		}
		if (dirty) player.sendAbilitiesUpdate();
		player.setNoGravity(true);
		ModNetworking.syncPlayer(player, data);
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
}

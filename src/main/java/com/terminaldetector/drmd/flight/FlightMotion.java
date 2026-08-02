package com.terminaldetector.drmd.flight;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Shared 6DoF move apply — used from {@code PlayerEntity.travel} (not LivingEntity)
 * so creative {@code abilities.flying} post-processing cannot wipe thruster velocity.
 *
 * <p>Movement is collision-substepped so high speed cannot tunnel (no-clip) through blocks.
 */
public final class FlightMotion {
	private FlightMotion() {}

	/** @return true if 6DoF owned this travel tick (caller should cancel vanilla travel). */
	public static boolean applyTravel(PlayerEntity player) {
		DescentPlayerData data = DescentPlayerData.get(player);
		if (!data.isEnabled()) return false;
		if (player.getVehicle() instanceof PyroShipEntity) return false;

		suppressCreativeFly(player);
		FootGravitySystem.clear(player);
		player.noClip = false;

		Vec3d vel = data.getFlightVelocity();
		if (player.getWorld().isClient && vel.lengthSquared() < 1e-12) {
			vel = player.getVelocity();
		}

		// Hard safety cap — never move more than afterburn envelope in one tick.
		double hardCap = FlightSpeeds.AFTERBURN_MAX_BPS / 20.0;
		if (vel.lengthSquared() > hardCap * hardCap) {
			vel = vel.normalize().multiply(hardCap);
		}

		player.setNoGravity(true);
		Vec3d before = player.getPos();
		moveWithCollision(player, vel);
		Vec3d actual = player.getPos().subtract(before);

		// Kill velocity into walls so the next tick does not keep pushing through.
		Vec3d clipped = clipAgainstCollision(vel, actual, player);
		data.setFlightVelocity(clipped);
		player.setVelocity(clipped);
		player.fallDistance = 0f;
		player.velocityDirty = true;
		return true;
	}

	/**
	 * Split large deltas into short steps so {@link PlayerEntity#move} cannot skip 1-block walls.
	 */
	public static void moveWithCollision(PlayerEntity player, Vec3d vel) {
		double len = vel.length();
		if (len < 1e-8) {
			player.setVelocity(Vec3d.ZERO);
			return;
		}
		int steps = MathHelper.clamp(
				(int) Math.ceil(len / FlightSpeeds.COLLISION_STEP),
				1,
				FlightSpeeds.COLLISION_MAX_STEPS);
		Vec3d step = vel.multiply(1.0 / steps);
		for (int i = 0; i < steps; i++) {
			player.noClip = false;
			player.move(MovementType.SELF, step);
			if (player.horizontalCollision && player.verticalCollision
					&& step.horizontalLengthSquared() > 1e-6 && Math.abs(step.y) > 1e-6) {
				// Wedged — stop remaining substeps
				break;
			}
		}
	}

	/** Zero / scale axes that were blocked by collision this tick. */
	public static Vec3d clipAgainstCollision(Vec3d intended, Vec3d actual, PlayerEntity player) {
		double ix = intended.x, iy = intended.y, iz = intended.z;
		double ax = actual.x, ay = actual.y, az = actual.z;
		double vx = ix, vy = iy, vz = iz;

		if (Math.abs(ix) > 1e-5 && Math.abs(ax) < Math.abs(ix) * 0.08) vx = 0;
		else if (Math.abs(ix) > 1e-5) vx = ix * MathHelper.clamp(ax / ix, 0.0, 1.0);

		if (Math.abs(iy) > 1e-5 && Math.abs(ay) < Math.abs(iy) * 0.08) vy = 0;
		else if (Math.abs(iy) > 1e-5) vy = iy * MathHelper.clamp(ay / iy, 0.0, 1.0);

		if (Math.abs(iz) > 1e-5 && Math.abs(az) < Math.abs(iz) * 0.08) vz = 0;
		else if (Math.abs(iz) > 1e-5) vz = iz * MathHelper.clamp(az / iz, 0.0, 1.0);

		if (player.horizontalCollision && vx * vx + vz * vz > 1e-6) {
			// Soft bounce instead of sticking forever
			vx *= -FlightSystem.WALL_BOUNCE * 0.35;
			vz *= -FlightSystem.WALL_BOUNCE * 0.35;
		}
		if (player.verticalCollision && Math.abs(vy) > 1e-6 && !player.isOnGround()) {
			vy *= -FlightSystem.WALL_BOUNCE * 0.35;
		}
		return new Vec3d(vx, vy, vz);
	}

	/**
	 * Creative double-tap Space sets {@code abilities.flying}. That path rewrites velocity
	 * after {@code LivingEntity.travel} and freezes / kills 6DoF. Force it off while thrusters on.
	 */
	public static void suppressCreativeFly(PlayerEntity player) {
		var ab = player.getAbilities();
		if (ab.flying) {
			ab.flying = false;
			if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
				sp.sendAbilitiesUpdate();
			}
		}
		player.noClip = false;
		player.setNoGravity(true);
	}

	/** Lightweight client prediction so WASD responds before the next server sync. */
	public static void clientPredict(PlayerEntity player, float forward, float strafe, float vertical,
									 Vec3d forwardDir, Vec3d upDir) {
		DescentPlayerData data = DescentPlayerData.get(player);
		if (!data.isEnabled() || player.getWorld() == null || !player.getWorld().isClient) return;
		suppressCreativeFly(player);

		float dt = 1f / 20f;
		Vec3d look = forwardDir.lengthSquared() > 1e-8 ? forwardDir.normalize() : player.getRotationVec(1f);
		Vec3d up = upDir.lengthSquared() > 1e-8 ? upDir.normalize() : new Vec3d(0, 1, 0);
		up = up.subtract(look.multiply(up.dotProduct(look)));
		if (up.lengthSquared() < 1e-8) up = new Vec3d(0, 1, 0);
		else up = up.normalize();
		Vec3d right = up.crossProduct(look);
		if (right.lengthSquared() < 1e-8) right = new Vec3d(1, 0, 0);
		else right = right.normalize();

		boolean thrusting = Math.abs(forward) > 0.01f || Math.abs(strafe) > 0.01f || Math.abs(vertical) > 0.01f;
		float spool = data.getThrustSpool();
		if (thrusting) spool = Math.min(1f, spool + FlightSystem.SPOOL_UP * dt);
		else spool = Math.max(0f, spool - FlightSystem.SPOOL_DOWN * dt);
		data.setThrustSpool(spool);

		boolean ar = data.isAlwaysRun();
		double a = FlightSpeeds.accelBlocksPerSec2(ar) * spool * dt;
		Vec3d vel = data.getFlightVelocity()
				.add(look.multiply(forward * a))
				.add(right.multiply(strafe * FlightSystem.STRAFE_MULT * a))
				.add(up.multiply(vertical * FlightSystem.VERT_MULT * a));
		double inertiaKeep = Math.pow(FlightSystem.INERTIA, dt * 60.0);
		vel = vel.multiply(inertiaKeep);
		double maxTick = FlightSpeeds.maxBlocksPerTick(ar, spool);
		if (vel.length() > maxTick) vel = vel.normalize().multiply(maxTick);

		data.setFlightVelocity(vel);
		player.setVelocity(vel);
		player.velocityDirty = true;
	}
}

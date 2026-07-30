package com.terminaldetector.drmd.flight;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Shared 6DoF move apply — used from {@code PlayerEntity.travel} (not LivingEntity)
 * so creative {@code abilities.flying} post-processing cannot wipe thruster velocity.
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

		Vec3d vel = data.getFlightVelocity();
		if (player.getWorld().isClient && vel.lengthSquared() < 1e-12) {
			vel = player.getVelocity();
		}
		player.setNoGravity(true);
		player.setVelocity(vel);
		player.move(MovementType.SELF, vel);
		player.fallDistance = 0f;
		player.velocityDirty = true;
		return true;
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
		// up × forward = pilot's right (same as server FlightSystem)
		Vec3d right = up.crossProduct(look);
		if (right.lengthSquared() < 1e-8) right = new Vec3d(1, 0, 0);
		else right = right.normalize();

		boolean thrusting = Math.abs(forward) > 0.01f || Math.abs(strafe) > 0.01f || Math.abs(vertical) > 0.01f;
		float spool = data.getThrustSpool();
		if (thrusting) spool = Math.min(1f, spool + FlightSystem.SPOOL_UP * dt);
		else spool = Math.max(0f, spool - FlightSystem.SPOOL_DOWN * dt);
		data.setThrustSpool(spool);

		// Descent: W adds full accel along nose — never renormalize with strafe/vertical.
		double a = DescentMod.su(data.getAccel()) * spool * dt;
		Vec3d vel = data.getFlightVelocity()
				.add(look.multiply(forward * a))
				.add(right.multiply(strafe * FlightSystem.STRAFE_MULT * a))
				.add(up.multiply(vertical * FlightSystem.VERT_MULT * a));
		double inertiaKeep = Math.pow(FlightSystem.INERTIA, dt * 60.0);
		vel = vel.multiply(inertiaKeep);
		double maxSpd = DescentMod.su(data.getMaxSpeed());
		if (vel.length() > maxSpd) vel = vel.normalize().multiply(maxSpd);

		data.setFlightVelocity(vel);
		player.setVelocity(vel);
		player.velocityDirty = true;
	}
}

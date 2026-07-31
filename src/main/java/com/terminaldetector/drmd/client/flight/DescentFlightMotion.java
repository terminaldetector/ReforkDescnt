package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.client.DescentClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Hull motion on the pilot's own client while 6DoF is on.
 *
 * <p>A player's position is client-authoritative: the server integrates the flight model but the
 * client is what actually walks the entity through the world and reports back. Left to itself the
 * server's {@code setVelocity} is invisible to the pilot — a player is never a listener on its own
 * entity tracker — so the ship velocity arrives on {@code SyncPayload} instead and gets spent here.
 *
 * <p>This replaces {@code travel} outright rather than adding to it. Vanilla's travel would apply
 * air drag, gravity and, in creative, rewrite Y as {@code previousY * 0.6} every tick — each of
 * which quietly dismantles a 6DoF velocity vector.
 */
public final class DescentFlightMotion {
	/** Speed kept after scraping a surface; the rest is spent on the hull. */
	private static final double SCRAPE_KEEP = 0.86;

	private DescentFlightMotion() {}

	/** True when this entity is the local pilot and the mod owns its movement this tick. */
	public static boolean drives(PlayerEntity player) {
		if (!DescentClientState.enabled) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player != player) return false;
		// A spectator has no collision and its own free flight; a passenger is the vehicle's problem.
		return !player.isSpectator() && !player.hasVehicle();
	}

	public static void travel(PlayerEntity player) {
		// Creative flight cannot co-drive the hull. `allowFlying` is left alone, so switching 6DoF
		// off with H hands normal creative flight straight back for building.
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
		}

		Vec3d vel = player.getVelocity();
		player.move(MovementType.SELF, vel);

		// Entity.move already zeroes whichever axes the collision sweep blocked, which is the wall
		// stop; this only bleeds the speed that survived along the surface so a graze costs something.
		if (player.horizontalCollision || player.verticalCollision) {
			player.setVelocity(player.getVelocity().multiply(SCRAPE_KEEP));
		}

		player.fallDistance = 0f;
	}
}

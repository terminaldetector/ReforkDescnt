package com.terminaldetector.drmd.physics;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Havok-like subset for the gravity gun — grab / hold spring / throw.
 * Not a full rigid-body solver: enough for Descent gravy feel in Minecraft.
 *
 * <p>Status: scaffold. Wire from {@code DescentWeaponItem.fireGravy} next.</p>
 */
public final class GravyPhysics {
	private static final Map<UUID, Grab> GRABS = new ConcurrentHashMap<>();

	public record Grab(UUID targetId, float mass, Vec3d holdOffset) {}

	private GravyPhysics() {}

	public static boolean isHolding(ServerPlayerEntity player) {
		return GRABS.containsKey(player.getUuid());
	}

	public static void release(ServerPlayerEntity player) {
		GRABS.remove(player.getUuid());
	}

	/** Begin grab if look-ray hits a living / prop entity within range. */
	public static boolean tryGrab(ServerPlayerEntity player, Entity target, float mass) {
		if (target == null || !target.isAlive()) return false;
		GRABS.put(player.getUuid(), new Grab(target.getUuid(), Math.max(0.2f, mass), new Vec3d(0, 0, 2.5)));
		return true;
	}

	/** Spring-hold toward a point in front of the player's eyes (6DoF-friendly). */
	public static void tick(ServerPlayerEntity player) {
		Grab grab = GRABS.get(player.getUuid());
		if (grab == null) return;
		World world = player.getWorld();
		Entity target = null;
		for (Entity e : ((net.minecraft.server.world.ServerWorld) world).iterateEntities()) {
			if (e.getUuid().equals(grab.targetId())) {
				target = e;
				break;
			}
		}
		if (target == null || !target.isAlive()) {
			release(player);
			return;
		}
		Vec3d hold = player.getEyePos().add(player.getRotationVec(1f).multiply(3.2));
		Vec3d delta = hold.subtract(target.getPos());
		// Soft spring + damp (Havok-lite)
		double k = 0.35 / grab.mass();
		double damp = 0.82;
		Vec3d vel = target.getVelocity().multiply(damp).add(delta.multiply(k));
		target.setVelocity(vel);
		target.velocityModified = true;
		target.fallDistance = 0;
	}

	public static void fling(ServerPlayerEntity player, float power) {
		Grab grab = GRABS.get(player.getUuid());
		if (grab == null) return;
		for (Entity e : ((net.minecraft.server.world.ServerWorld) player.getWorld()).iterateEntities()) {
			if (!e.getUuid().equals(grab.targetId())) continue;
			Vec3d impulse = player.getRotationVec(1f).multiply(power / grab.mass());
			e.addVelocity(impulse.x, impulse.y, impulse.z);
			e.velocityModified = true;
			break;
		}
		release(player);
	}
}

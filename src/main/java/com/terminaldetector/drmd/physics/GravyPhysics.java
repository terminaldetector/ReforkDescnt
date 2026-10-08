package com.terminaldetector.drmd.physics;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.terminaldetector.drmd.weapon.core.WeaponCore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Havok-like subset for the gravity gun — grab / hold spring / throw.
 * Not a full rigid-body solver: enough for Descent gravy feel in Minecraft.
 *
 * <p>Called by the gravity weapon; block assemblies receive physical impulses in SI tick units.</p>
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
		float resolvedMass = target instanceof PhysicsTarget physics
				? (float) physics.physicsMass() : mass;
		GRABS.put(player.getUuid(), new Grab(target.getUuid(), Math.max(0.2f, resolvedMass), new Vec3d(0, 0, 2.5)));
		return true;
	}

	/** Spring-hold toward a point in front of the player's eyes (6DoF-friendly). */
	public static void tick(ServerPlayerEntity player) {
		Grab grab = GRABS.get(player.getUuid());
		if (grab == null) return;
		World world = player.getWorld();
		Entity target = ((net.minecraft.server.world.ServerWorld) world).getEntity(grab.targetId());
		if (target == null || !target.isAlive()) {
			release(player);
			return;
		}
		Vec3d hold = player.getEyePos().add(WeaponCore.aimDir(player).multiply(3.2));
		Vec3d delta = hold.subtract(target.getPos());
		if (target instanceof PhysicsTarget physics) {
			Vec3d velocity = physics.physicsVelocity();
			// Fixed-strength spring: heavier targets accelerate less. Impulse includes dt once.
			Vec3d impulse = delta.multiply(80).subtract(velocity.multiply(8)).multiply(.05);
			physics.applyPhysicsImpulse(target.getPos(), impulse);
			return;
		}
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
		Entity e = player.getServerWorld().getEntity(grab.targetId());
		if (e instanceof PhysicsTarget physics) {
			Vec3d impulse = WeaponCore.aimDir(player).multiply(power * 20);
			physics.applyPhysicsImpulse(e.getPos(), impulse);
		} else if (e != null) {
			Vec3d impulse = WeaponCore.aimDir(player).multiply(power / grab.mass());
			e.addVelocity(impulse.x, impulse.y, impulse.z);
			e.velocityModified = true;
		}
		release(player);
	}
}

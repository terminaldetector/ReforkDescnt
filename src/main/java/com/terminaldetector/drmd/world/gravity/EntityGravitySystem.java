package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.entity.PyroShipEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local gravity for non-player living entities (mobs).
 *
 * <p>Players use {@link FootGravitySystem}. Mobs get the same field sample, a safe mount when
 * "down" changes, {@code noGravity}, and a soft pull/stick toward the surface — never a shove
 * into the wall that causes suffocation.
 */
public final class EntityGravitySystem {
	private static final double GRAVITY = 0.06;
	private static final double STICK = 1.6;
	/** Remount when field up turns more than ~45°. */
	private static final double REMOUNT_DOT = 0.70;

	private record MobState(Vec3d fieldUp, boolean mounted) {}

	private static final Map<UUID, MobState> STATE = new ConcurrentHashMap<>();

	private EntityGravitySystem() {}

	public static void clear() {
		STATE.clear();
	}

	public static void tick(MinecraftServer server) {
		Set<UUID> active = new HashSet<>();
		for (ServerWorld world : server.getWorlds()) {
			for (GravityFields.Field field : GravityFields.all()) {
				if (field.worldKey() != null && field.worldKey() != world.getRegistryKey()) continue;
				BlockPos o = field.origin();
				double r = field.radius() + 1.0;
				Box box = new Box(o).expand(r);
				for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
					if (!active.add(e.getUuid())) continue;
					tickEntity(e);
				}
			}
		}
		STATE.keySet().removeIf(id -> {
			if (active.contains(id)) return false;
			for (ServerWorld world : server.getWorlds()) {
				Entity e = world.getEntity(id);
				if (e instanceof LivingEntity le && !(le instanceof PlayerEntity)) {
					le.setNoGravity(false);
					break;
				}
			}
			return true;
		});
	}

	private static void tickEntity(LivingEntity entity) {
		if (entity instanceof PlayerEntity) return; // FootGravitySystem
		if (entity.getVehicle() instanceof PyroShipEntity) return;
		if (entity.hasVehicle()) return;

		GravityFields.Sample sample = GravityFields.sample(entity.getWorld(), entity.getPos());
		if (sample == null || sample.strength() < 0.05f) {
			MobState prev = STATE.remove(entity.getUuid());
			if (prev != null) entity.setNoGravity(false);
			return;
		}

		Vec3d up = sample.upDir().normalize();
		MobState prev = STATE.get(entity.getUuid());
		boolean remount = prev == null || prev.fieldUp().dotProduct(up) < REMOUNT_DOT;
		if (remount) {
			GravityMount.safeMount(entity, up);
		}
		STATE.put(entity.getUuid(), new MobState(up, true));

		entity.setNoGravity(true);
		entity.fallDistance = 0f;
		applyMobPhysics(entity, up);
	}

	private static void applyMobPhysics(LivingEntity entity, Vec3d up) {
		Vec3d down = up.negate();
		boolean grounded = probe(entity, up);

		Vec3d vel = entity.getVelocity();
		// Kill into-up residue; keep tangent motion
		vel = vel.subtract(up.multiply(vel.dotProduct(up)));
		if (!grounded) {
			vel = vel.add(down.multiply(GRAVITY));
		} else {
			double into = vel.dotProduct(down);
			if (into > 0) vel = vel.subtract(down.multiply(into));
			snap(entity, up);
		}
		vel = vel.multiply(grounded ? 0.86 : 0.96);
		entity.setVelocity(vel);
		entity.move(MovementType.SELF, vel);
		entity.velocityModified = true;
		entity.setOnGround(grounded);
		entity.fallDistance = 0f;
	}

	private static boolean probe(LivingEntity entity, Vec3d up) {
		Vec3d from = entity.getPos().add(up.multiply(0.08));
		var hit = entity.getWorld().raycast(new RaycastContext(
				from, from.add(up.negate().multiply(STICK)),
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				entity));
		return hit.getType() == HitResult.Type.BLOCK
				&& hit.getPos().distanceTo(from) < STICK;
	}

	private static void snap(LivingEntity entity, Vec3d up) {
		Vec3d from = entity.getPos().add(up.multiply(0.08));
		var hit = entity.getWorld().raycast(new RaycastContext(
				from, from.add(up.negate().multiply(STICK)),
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				entity));
		if (hit.getType() != HitResult.Type.BLOCK) return;
		double along = hit.getPos().subtract(entity.getPos()).dotProduct(up);
		if (Math.abs(along) < 0.05) return;
		double step = Math.max(-0.3, Math.min(0.3, along * 0.4));
		entity.move(MovementType.SELF, up.multiply(step));
	}
}

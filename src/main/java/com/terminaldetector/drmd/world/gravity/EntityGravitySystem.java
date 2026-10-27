package com.terminaldetector.drmd.world.gravity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.WeakReference;

/**
 * Local gravity for non-player living entities (mobs).
 *
 * <p>Players use {@link FootGravitySystem}. Mobs get the same field sample, a safe mount when
 * "down" changes, a runtime gravity override, and a soft pull/stick toward the surface — never a shove
 * into the wall that causes suffocation.
 */
public final class EntityGravitySystem {
	private static final double GRAVITY = 0.06;
	private static final double STICK = 1.6;
	/** Remount when field up turns more than ~45°. */
	private static final double REMOUNT_DOT = 0.70;

	private static final class MobState {
		final WeakReference<LivingEntity> actor;
		final WeakReference<net.minecraft.world.World> world;
		Vec3d fieldUp;
		MobState(LivingEntity entity,Vec3d up) {
			actor=new WeakReference<>(entity);world=new WeakReference<>(entity.getWorld());fieldUp=up;
		}
		boolean owns(Entity entity){return actor.get()==entity && world.get()==entity.getWorld();}
	}

	private static final Map<UUID, MobState> STATE = new ConcurrentHashMap<>();

	private EntityGravitySystem() {}

	public static void clear() {
		STATE.clear();
	}

	/** Suppress only the standard scalar acceleration, never the entity's saved NoGravity flag. */
	public static boolean suppressesVanillaGravity(Entity entity) {
		if(entity.getWorld().isClient || !(entity instanceof LivingEntity living)
				|| living instanceof PlayerEntity || !living.isAlive() || living.hasVehicle())return false;
		MobState state=STATE.get(entity.getUuid());
		return state!=null && state.owns(entity);
	}

	public static void tick(MinecraftServer server) {
		Set<UUID> active = new HashSet<>();
		for (ServerWorld world : server.getWorlds()) {
			if(!GravityFields.hasFields(world.getRegistryKey()))continue;
			// Visit each loaded actor once. Overlapping emitters previously repeated world
			// entity-section searches, then sampled the full field catalogue for each actor.
			for(Entity candidate:world.iterateEntities()) {
				if(candidate instanceof LivingEntity e && !(e instanceof PlayerEntity) && e.isAlive()) {
					active.add(e.getUuid());tickEntity(e);
				}
			}
		}
		STATE.keySet().removeIf(id -> !active.contains(id));
	}

	private static void tickEntity(LivingEntity entity) {
		if (entity instanceof PlayerEntity) return; // FootGravitySystem
		if (entity.hasVehicle()) {STATE.remove(entity.getUuid());return;}

		GravityFields.Sample sample = GravityFields.sample(entity.getWorld(), entity.getPos());
		if (sample == null || sample.strength() < 0.05f) {
			STATE.remove(entity.getUuid());
			return;
		}

		Vec3d up = sample.upDir().normalize();
		MobState prev = STATE.get(entity.getUuid());
		if(prev!=null && !prev.owns(entity))prev=null;
		boolean remount = prev == null || prev.fieldUp.dotProduct(up) < REMOUNT_DOT;
		if (remount) {
			GravityMount.safeMount(entity, up);
		}
		if(prev==null)STATE.put(entity.getUuid(),new MobState(entity,up));
		else prev.fieldUp=up;

		entity.fallDistance = 0f;
		applyMobPhysics(entity, up,sample.strength());
	}

	private static void applyMobPhysics(LivingEntity entity, Vec3d up,float strength) {
		Vec3d down = up.negate();
		boolean grounded = probe(entity, up);

		Vec3d vel = entity.getVelocity();
		if (!grounded) {
			vel = vel.add(down.multiply(GRAVITY*strength));
		} else {
			double into = vel.dotProduct(down);
			if (into > 0) vel = vel.subtract(down.multiply(into));
			snap(entity, up);
		}
		vel = vel.multiply(grounded ? 0.86 : 0.96);
		entity.setVelocity(vel);
		// LivingEntity.travel integrates this velocity during the actor's own next tick.
		// Advancing again here moved actors twice and bypassed their normal tick lifecycle.
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

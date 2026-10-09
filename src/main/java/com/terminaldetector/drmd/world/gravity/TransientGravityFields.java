package com.terminaldetector.drmd.world.gravity;

import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, server-authoritative gravity fields created by weapons.
 *
 * <p>Permanent emitters own their entries in {@link GravityFields}. Weapon fields are different:
 * they expire, may follow a thrown physical body, and must disappear when the anchor unloads or the
 * server changes. Keeping that lifetime here prevents every gun from inventing another cleanup
 * path around the process-wide field catalogue.</p>
 */
public final class TransientGravityFields {
	public static final int MAX_ACTIVE_FIELDS = 64;
	private static final AtomicLong SEQUENCE = new AtomicLong();
	private static final Map<UUID, Lease> LEASES = new ConcurrentHashMap<>();

	private static final class Lease {
		final UUID fieldId;
		final RegistryKey<World> worldKey;
		final UUID anchorId;
		final Vec3d fixedPosition;
		final Vec3d downDir;
		final double radius;
		final float power;
		final String label;
		final long sequence;
		int remainingTicks;

		Lease(UUID fieldId, RegistryKey<World> worldKey, UUID anchorId, Vec3d fixedPosition,
			  Vec3d downDir, double radius, float power, int remainingTicks, String label) {
			this.fieldId = fieldId;
			this.worldKey = worldKey;
			this.anchorId = anchorId;
			this.fixedPosition = fixedPosition;
			this.downDir = downDir;
			this.radius = radius;
			this.power = power;
			this.remainingTicks = remainingTicks;
			this.label = label;
			this.sequence = SEQUENCE.incrementAndGet();
		}
	}

	private TransientGravityFields() {}

	/** Attach one field to an entity. Re-firing on the same body refreshes, rather than stacks, it. */
	public static UUID attach(ServerWorld world, Entity anchor, Vec3d downDir, double radius,
							  float power, int lifetimeTicks, String label) {
		if (world == null || anchor == null || anchor.isRemoved()) return null;
		UUID id = UUID.nameUUIDFromBytes(("drmd:gravity-projector:" + anchor.getUuid())
				.getBytes(StandardCharsets.UTF_8));
		return put(new Lease(id, world.getRegistryKey(), anchor.getUuid(), null,
				safeDirection(downDir), safeRadius(radius), safePower(power),
				safeLifetime(lifetimeTicks), safeLabel(label)), anchor.getPos());
	}

	/** Place a stationary field at a ray hit. */
	public static UUID place(ServerWorld world, Vec3d position, Vec3d downDir, double radius,
							 float power, int lifetimeTicks, String label) {
		if (world == null || position == null || !finite(position)) return null;
		UUID id = UUID.randomUUID();
		return put(new Lease(id, world.getRegistryKey(), null, position,
				safeDirection(downDir), safeRadius(radius), safePower(power),
				safeLifetime(lifetimeTicks), safeLabel(label)), position);
	}

	private static UUID put(Lease lease, Vec3d position) {
		if (!LEASES.containsKey(lease.fieldId) && LEASES.size() >= MAX_ACTIVE_FIELDS) {
			LEASES.values().stream().min(Comparator.comparingLong(value -> value.sequence))
					.ifPresent(oldest -> remove(oldest.fieldId));
		}
		LEASES.put(lease.fieldId, lease);
		publish(lease, position);
		return lease.fieldId;
	}

	/** Update moving anchors and expire old fields. Called once per server tick. */
	public static void tick(MinecraftServer server) {
		if (server == null || LEASES.isEmpty()) return;
		for (Lease lease : LEASES.values()) {
			if (--lease.remainingTicks <= 0) {
				remove(lease.fieldId);
				continue;
			}
			ServerWorld world = server.getWorld(lease.worldKey);
			if (world == null) {
				remove(lease.fieldId);
				continue;
			}
			Vec3d position = lease.fixedPosition;
			if (lease.anchorId != null) {
				Entity anchor = world.getEntity(lease.anchorId);
				if (anchor == null || anchor.isRemoved()) {
					remove(lease.fieldId);
					continue;
				}
				position = anchor.getPos();
			}
			publish(lease, position);
		}
	}

	private static void publish(Lease lease, Vec3d position) {
		GravityFields.put(new GravityFields.Field(lease.fieldId, lease.worldKey,
				BlockPos.ofFloored(position), lease.downDir, lease.radius, lease.power,
				FieldShape.SPHERE, lease.label));
	}

	public static void remove(UUID id) {
		if (id == null) return;
		LEASES.remove(id);
		GravityFields.remove(id);
	}

	public static void clear() {
		for (UUID id : LEASES.keySet()) GravityFields.remove(id);
		LEASES.clear();
	}

	public static int activeCount() {
		return LEASES.size();
	}

	public static int remainingTicks(UUID id) {
		Lease lease = LEASES.get(id);
		return lease == null ? 0 : lease.remainingTicks;
	}

	private static Vec3d safeDirection(Vec3d direction) {
		return direction != null && finite(direction) && direction.lengthSquared() > 1.0e-10
				? direction.normalize() : new Vec3d(0, -1, 0);
	}

	private static double safeRadius(double radius) {
		return Double.isFinite(radius) ? Math.max(2, Math.min(24, radius)) : 8;
	}

	private static float safePower(float power) {
		return Float.isFinite(power) ? Math.max(.1f, Math.min(2f, power)) : 1f;
	}

	private static int safeLifetime(int ticks) {
		return Math.max(1, Math.min(20 * 30, ticks));
	}

	private static String safeLabel(String label) {
		return label == null || label.isBlank() ? "Gravity projector" : label;
	}

	private static boolean finite(Vec3d value) {
		return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
	}
}

package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Reynolds boids for Descent drones — port of {@code D6_Swarm_AI_Core} in d6_ai.lua.
 *
 * <p>Separation / cohesion / alignment among flock-mates, plus a pull toward the current
 * combat target. Heavy / elite / mine-style roles stay out of the cloud (same as GMod
 * {@code BOIDS_EXCLUDED}).
 */
public final class DroneSwarmAi {
	private static final double SWARM_RADIUS = DescentMod.su(400);
	private static final double SEPARATION_DIST = DescentMod.su(90);
	public static final int MAX_BOID_NEIGHBOURS = 32;
	public static final int MAX_BOID_CANDIDATES = 96;
	private static final int MAX_SUPPORT_NEIGHBOURS = 48;
	private static final int MAX_SUPPORT_CANDIDATES = 128;
	private static final float WEIGHT_SEPARATION = 2.8f;
	private static final float WEIGHT_COHESION = 0.8f;
	private static final float WEIGHT_ALIGNMENT = 1.0f;
	private static final float WEIGHT_TARGET = 2.2f;
	private static final Map<ServerWorld, FrameSnapshot> SNAPSHOTS = new WeakHashMap<>();

	private record DroneSample(UUID id, Vec3d position, Vec3d velocity, AiRole role) {}
	private record FrameSnapshot(long tick, BoundedSpatialIndex<DroneSample> all,
			BoundedSpatialIndex<DroneSample> flock) {}
	public record NeighbourQueryStats(long snapshotTick, int indexedDrones, int neighbours,
			int examinedCandidates, int occupiedBuckets) {}

	private DroneSwarmAi() {}

	public static boolean participates(AiRole role) {
		if (role == null) return false;
		return switch (role) {
			case HEAVY, HEAVY_ELITE, GRAV -> false;
			default -> true;
		};
	}

	/** Drop world-keyed frame snapshots at both ends of an integrated/dedicated server session. */
	public static void clear() {
		synchronized (SNAPSHOTS) {
			SNAPSHOTS.clear();
		}
	}

	/**
	 * Unitless desire vector to blend into combat motion. Zero when alone / excluded.
	 */
	public static Vec3d compute(DroneEntity drone, LivingEntity target) {
		if (!participates(drone.getRole())) return Vec3d.ZERO;
		if (!(drone.getWorld() instanceof ServerWorld world)) return Vec3d.ZERO;
		Vec3d myPos = drone.getPos();

		Vec3d separation = Vec3d.ZERO;
		Vec3d center = Vec3d.ZERO;
		Vec3d avgVel = Vec3d.ZERO;
		int count = 0;

		FrameSnapshot snapshot = snapshot(world);
		for (BoundedSpatialIndex.Hit<DroneSample> hit : flockQuery(snapshot, drone).hits()) {
			DroneSample other = hit.value();
			Vec3d nPos = other.position();
			double d = Math.sqrt(hit.squaredDistance());
			if(d<1e-4) {
                var escape=OverlapSeparation.between(drone.getUuid(),other.id());
                separation=separation.add(escape.x(),escape.y(),escape.z());
                center=center.add(nPos); avgVel=avgVel.add(other.velocity()); count++;
                continue;
            }
            if(d>SWARM_RADIUS) continue;
			if (d < SEPARATION_DIST) {
				separation = separation.add(myPos.subtract(nPos).normalize().multiply(1.0 / d));
			}
			center = center.add(nPos);
			avgVel = avgVel.add(other.velocity());
			count++;
		}

		Vec3d cohesion = Vec3d.ZERO;
		Vec3d alignment = Vec3d.ZERO;
		if (count > 0) {
			center = center.multiply(1.0 / count);
			Vec3d toCenter = center.subtract(myPos);
			if (toCenter.lengthSquared() > 1e-6) cohesion = toCenter.normalize();
			Vec3d avg = avgVel.multiply(1.0 / count);
			if (avg.lengthSquared() > 1e-6) alignment = avg.normalize();
		}

		Vec3d targetDir = Vec3d.ZERO;
		if (target != null && target.isAlive()) {
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.4, 0).subtract(myPos);
			if (to.lengthSquared() > 1e-6) {
				targetDir = to.normalize();
				// MG pressure: don't sit in their face — GMod mg &lt; 400 retreat bias.
				if (drone.getRole() == AiRole.MG && to.length() < DescentMod.su(400)) {
					targetDir = targetDir.multiply(-0.5);
				}
			}
		}

		Vec3d finalVel = separation.multiply(WEIGHT_SEPARATION)
				.add(cohesion.multiply(WEIGHT_COHESION))
				.add(alignment.multiply(WEIGHT_ALIGNMENT))
				.add(targetDir.multiply(WEIGHT_TARGET));
		if (finalVel.lengthSquared() < 1e-6) return Vec3d.ZERO;
		return finalVel.normalize();
	}

	/**
	 * Bounded support lookup sharing the same once-per-world-tick snapshot as the boid pass.
	 * The current entity health is checked after the spatial sample is selected, so healing never
	 * relies on a stale health value from the beginning of the tick.
	 */
	public static DroneEntity mostDamagedAlly(DroneEntity drone, double radius) {
		if (!(drone.getWorld() instanceof ServerWorld world) || radius <= 0) return null;
		FrameSnapshot snapshot = snapshot(world);
		var query = snapshot.all.nearest(drone.getX(), drone.getY(), drone.getZ(), radius, MAX_SUPPORT_NEIGHBOURS,
				MAX_SUPPORT_CANDIDATES, querySeed(drone, snapshot.tick),
				sample -> !sample.id.equals(drone.getUuid()));
		DroneEntity worst = null;
		float worstRatio = 1f;
		for (BoundedSpatialIndex.Hit<DroneSample> hit : query.hits()) {
			Entity entity = world.getEntity(hit.value().id());
			if (!(entity instanceof DroneEntity ally) || !ally.isAlive()) continue;
			float ratio = ally.getHealth() / Math.max(1f, ally.getMaxHealth());
			if (ratio < worstRatio) {
				worstRatio = ratio;
				worst = ally;
			}
		}
		return worst;
	}

	/** Observable work bound for GameTests and performance diagnostics. */
	public static NeighbourQueryStats queryStats(DroneEntity drone) {
		if (!(drone.getWorld() instanceof ServerWorld world)) return new NeighbourQueryStats(-1, 0, 0, 0, 0);
		FrameSnapshot snapshot = snapshot(world);
		var query = flockQuery(snapshot, drone);
		return new NeighbourQueryStats(snapshot.tick, snapshot.all.size(), query.hits().size(),
				query.examinedCandidates(), query.occupiedBuckets());
	}

	private static BoundedSpatialIndex.Query<DroneSample> flockQuery(FrameSnapshot snapshot, DroneEntity drone) {
		return snapshot.flock.nearest(drone.getX(), drone.getY(), drone.getZ(), SWARM_RADIUS, MAX_BOID_NEIGHBOURS,
				MAX_BOID_CANDIDATES, querySeed(drone, snapshot.tick),
				sample -> !sample.id.equals(drone.getUuid()));
	}

	private static FrameSnapshot snapshot(ServerWorld world) {
		long tick = world.getTime();
		synchronized (SNAPSHOTS) {
			FrameSnapshot current = SNAPSHOTS.get(world);
			if (current != null && current.tick == tick) return current;
			BoundedSpatialIndex<DroneSample> all = new BoundedSpatialIndex<>(SWARM_RADIUS);
			BoundedSpatialIndex<DroneSample> flock = new BoundedSpatialIndex<>(SWARM_RADIUS);
			for (Entity entity : world.iterateEntities()) {
				if (!(entity instanceof DroneEntity drone) || !drone.isAlive()) continue;
				DroneSample sample = new DroneSample(drone.getUuid(), drone.getPos(),
						drone.getVelocity(), drone.getRole());
				all.add(sample, sample.position().x, sample.position().y, sample.position().z);
				if (participates(sample.role())) {
					flock.add(sample, sample.position().x, sample.position().y, sample.position().z);
				}
			}
			FrameSnapshot rebuilt = new FrameSnapshot(tick, all, flock);
			SNAPSHOTS.put(world, rebuilt);
			return rebuilt;
		}
	}

	private static long querySeed(DroneEntity drone, long tick) {
		UUID id = drone.getUuid();
		return id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23)
				^ tick * 0x9E3779B97F4A7C15L;
	}
}

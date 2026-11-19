package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
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
    private static final Map<ServerWorld,LoadedRoster> LOADED=new WeakHashMap<>();
    private static boolean registered;
    private static final class LoadedRoster {
        final LinkedHashMap<UUID,WeakReference<DroneEntity>> entries=new LinkedHashMap<>();
        long builds,samples,worldScans,pruned,unloads;boolean seeded;
    }
    public record IndexStats(long snapshotTick,int residentSlots,int lastLiveSamples,long snapshotBuilds,long indexedSamples,long fullWorldScans,long pruned,long unloaded){}

	private record DroneSample(UUID id, Vec3d position, Vec3d velocity, AiRole role,double halfWidth) {}
	private record FrameSnapshot(long tick, BoundedSpatialIndex<DroneSample> all,
			BoundedSpatialIndex<DroneSample> flock,Map<UUID,BoundedSpatialIndex.Query<DroneSample>> queries) {}
	public record NeighbourQueryStats(long snapshotTick, int indexedDrones, int neighbours,
			int examinedCandidates, int occupiedBuckets) {}
    public record CoordinationQuery(java.util.List<UUID> ids,int examinedCandidates){}

	private DroneSwarmAi() {}

    /** One loaded-actor lifecycle index, not a second world or a sensing scheduler.
     * Entity movement/target/physics remain owned by their normal ticks. */
    public static void register(){
        if(registered)return;registered=true;
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof DroneEntity drone)synchronized(SNAPSHOTS){
            LOADED.computeIfAbsent(world,w->new LoadedRoster()).entries.put(drone.getUuid(),new WeakReference<>(drone));
        }});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof DroneEntity drone)synchronized(SNAPSHOTS){
            var roster=LOADED.get(world);var entry=roster==null?null:roster.entries.get(drone.getUuid());
            if(entry!=null&&entry.get()==drone){roster.entries.remove(drone.getUuid());roster.unloads++;}
        }});
        ServerWorldEvents.UNLOAD.register((server,world)->{synchronized(SNAPSHOTS){LOADED.remove(world);SNAPSHOTS.remove(world);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->clear());
    }
    /** Read-only profiler sample; asking for counters never builds an index or runs a neighbour query. */
    public static IndexStats indexStats(ServerWorld world){synchronized(SNAPSHOTS){var r=LOADED.get(world);var f=SNAPSHOTS.get(world);
        return r==null?new IndexStats(-1,0,0,0,0,0,0,0):new IndexStats(f==null?-1:f.tick,r.entries.size(),f==null?0:f.all.size(),r.builds,r.samples,r.worldScans,r.pruned,r.unloads);
    }}

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
            LOADED.clear();
		}
        DroneSquadCoordinator.clear();
	}

    /** Existing frame index, bounded independently from flock steering; actual allegiance is checked. */
    public static CoordinationQuery coordinationNeighbours(DroneEntity scout){
        if(!(scout.getWorld() instanceof ServerWorld world))return new CoordinationQuery(java.util.List.of(),0);
        var frame=snapshot(world);
        var query=frame.all.nearest(scout.getX(),scout.getY(),scout.getZ(),32,DroneSquadCoordinator.MAX_ALLIES,
            DroneSquadCoordinator.MAX_CANDIDATES,querySeed(scout,frame.tick),sample->{
                var entity=world.getEntity(sample.id());return entity instanceof DroneEntity ally&&DroneSquadCoordinator.allies(scout,ally);
            });
        return new CoordinationQuery(query.hits().stream().map(hit->hit.value().id()).toList(),query.examinedCandidates());
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
		if (!(drone.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread()||!Double.isFinite(radius)||radius<=0||radius>32) return null;
		FrameSnapshot snapshot = snapshot(world);
		var query = snapshot.all.nearest(drone.getX(), drone.getY(), drone.getZ(), radius, MAX_SUPPORT_NEIGHBOURS,
				MAX_SUPPORT_CANDIDATES, querySeed(drone, snapshot.tick),
				sample -> !sample.id.equals(drone.getUuid()));
		DroneEntity worst = null;
		float worstRatio = 1f;
		for (BoundedSpatialIndex.Hit<DroneSample> hit : query.hits()) {
			Entity entity = world.getEntity(hit.value().id());
			if (!(entity instanceof DroneEntity ally) || !validSupportAlly(drone,ally,radius)) continue;
			float ratio = ally.getHealth() / Math.max(1f, ally.getMaxHealth());
			if (ratio < worstRatio) {
				worstRatio = ratio;
				worst = ally;
			}
		}
		return worst;
	}
    /** Current identity/allegiance and full loaded volumes, including after a portal or actor removal. */
    static boolean validSupportAlly(DroneEntity drone,DroneEntity ally,double radius){
        if(!(drone.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()||!Double.isFinite(radius)||radius<=0||radius>32
            ||!DroneSquadCoordinator.allies(drone,ally)||world.getEntity(drone.getUuid())!=drone||world.getEntity(ally.getUuid())!=ally
            ||drone.squaredDistanceTo(ally)>radius*radius)return false;
        return supportVolumeLoaded(world,drone)&&supportVolumeLoaded(world,ally);
    }
    private static boolean supportVolumeLoaded(ServerWorld world,DroneEntity actor){
        var b=actor.getBoundingBox().expand(1);if(!Double.isFinite(b.minX+b.minY+b.minZ+b.maxX+b.maxY+b.maxZ)
            ||actor.getBoundingBox().minY<world.getBottomY()||actor.getBoundingBox().maxY>world.getTopY()||!world.getWorldBorder().contains(actor.getBoundingBox())||!world.shouldTickEntity(actor.getBlockPos()))return false;
        int x0=net.minecraft.util.math.MathHelper.floor(b.minX)>>4,x1=net.minecraft.util.math.MathHelper.floor(b.maxX)>>4,y0=net.minecraft.util.math.MathHelper.floor(b.minY)>>4,y1=net.minecraft.util.math.MathHelper.floor(b.maxY)>>4,z0=net.minecraft.util.math.MathHelper.floor(b.minZ)>>4,z1=net.minecraft.util.math.MathHelper.floor(b.maxZ)>>4;
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>64)return false;
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++){var cube=new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z);if(com.terminaldetector.drmd.world.access.CubicAvailability.withinHeight(world,cube)&&!access.isVolumeAvailable(cube))return false;}return true;
    }

    /** Short-range repulsion shares the exact bounded flock query used by combat steering. */
    static Vec3d closeSeparation(DroneEntity drone){
        if(!participates(drone.getRole())||!(drone.getWorld() instanceof ServerWorld world))return Vec3d.ZERO;
        Vec3d result=Vec3d.ZERO;var pos=drone.getPos();
        for(var hit:flockQuery(snapshot(world),drone).hits()){
            var other=hit.value();var delta=pos.subtract(other.position());double distance=delta.length(),clearance=(drone.getWidth()*.5+other.halfWidth())*1.1;
            if(distance>=clearance||clearance<=0)continue;
            if(distance<1e-5){var escape=OverlapSeparation.between(drone.getUuid(),other.id());delta=new Vec3d(escape.x(),escape.y(),escape.z());}
            else delta=delta.multiply(1/distance);
            result=result.add(delta.multiply(1-distance/clearance));
        }
        return result.lengthSquared()>1?result.normalize():result;
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
		return snapshot.queries.computeIfAbsent(drone.getUuid(),id->snapshot.flock.nearest(drone.getX(), drone.getY(), drone.getZ(), SWARM_RADIUS, MAX_BOID_NEIGHBOURS,
				MAX_BOID_CANDIDATES, querySeed(drone, snapshot.tick),
				sample -> !sample.id.equals(drone.getUuid())));
	}

	private static FrameSnapshot snapshot(ServerWorld world) {
		long tick = world.getTime();
		synchronized (SNAPSHOTS) {
			FrameSnapshot current = SNAPSHOTS.get(world);
			if (current != null && current.tick == tick) return current;
			BoundedSpatialIndex<DroneSample> all = new BoundedSpatialIndex<>(SWARM_RADIUS);
			BoundedSpatialIndex<DroneSample> flock = new BoundedSpatialIndex<>(SWARM_RADIUS);
            var roster=LOADED.computeIfAbsent(world,w->new LoadedRoster());
            // One seed also covers startup clear() after already loaded actors.
            // Unregistered standalone embeddings retain the original per-tick
            // scan rather than silently publishing an empty neighbourhood.
            if(!roster.seeded||!registered){if(!registered)roster.entries.clear();roster.seeded=true;roster.worldScans++;
                for(Entity entity:world.iterateEntities())if(entity instanceof DroneEntity drone){var old=roster.entries.get(drone.getUuid());
                    if(old==null||old.get()==null||old.get().isRemoved())roster.entries.put(drone.getUuid(),new WeakReference<>(drone));}
            }
            for(var iterator=roster.entries.entrySet().iterator();iterator.hasNext();){var e=iterator.next();var drone=e.getValue().get();
                if(drone==null||drone.isRemoved()||drone.getWorld()!=world||!drone.getUuid().equals(e.getKey())){iterator.remove();roster.pruned++;continue;}
                if(!drone.isAlive())continue;
				DroneSample sample = new DroneSample(drone.getUuid(), drone.getPos(),
						drone.getVelocity(), drone.getRole(),drone.getWidth()*.5);
				all.add(sample, sample.position().x, sample.position().y, sample.position().z);
				if (participates(sample.role())) {
					flock.add(sample, sample.position().x, sample.position().y, sample.position().z);
				}
			}
			FrameSnapshot rebuilt = new FrameSnapshot(tick, all, flock,new java.util.HashMap<>());
            roster.builds++;roster.samples+=all.size();
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

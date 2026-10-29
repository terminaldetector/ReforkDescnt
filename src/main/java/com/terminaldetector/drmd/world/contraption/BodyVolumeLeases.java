package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.world.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import java.lang.reflect.*;
import java.util.*;

/** Server-owned leases for the whole moving hull and its empty interior. Requests are
 * asynchronous and bounded per tick. Missing data still pauses the existing D6 simulation.
 * No per-block rigid bodies, player teleport anchors or force-loaded save flags. */
public final class BodyVolumeLeases {
    private record Owner(UUID body,long column){}
    private static final ChunkTicketType<UUID> TICKET=ChunkTicketType.create("drmd_body_volume",Comparator.<UUID>naturalOrder());
    private static final int REQUESTS_PER_TICK=16;
    private static final class Lease {
        final Map<Long,BodyVolumePlan.Column> columns=new HashMap<>();
        BodyBounds planned;long revision=-1;
        List<BodyVolumePlan.Column> wanted=List.of();
    }
    private static final class State {final Map<UUID,Lease> leases=new HashMap<>();int cursor;}
    // Values contain only numeric plans/UUIDs. They do not retain the weak world key.
    private static final Map<ServerWorld,State> WORLDS=new WeakHashMap<>();
    private static boolean nativeFailed;
    private static Method nativeLevel,tracker,view,update,remove;
    private BodyVolumeLeases(){}
    public static void register() {
        ServerTickEvents.START_WORLD_TICK.register(BodyVolumeLeases::tick);
        ServerWorldEvents.UNLOAD.register((server,world)->close(world));
    }
    public static int heldColumns(ServerWorld world,UUID body) {
        var state=WORLDS.get(world);var lease=state==null?null:state.leases.get(body);return lease==null?0:lease.columns.size();
    }
    public static List<BodyVolumePlan.Column> heldPlan(ServerWorld world,UUID body) {
        var state=WORLDS.get(world);var lease=state==null?null:state.leases.get(body);return lease==null?List.of():List.copyOf(lease.columns.values());
    }
    private static void tick(ServerWorld world) {
        var bodies=BodySpatialIndex.loaded(world);var active=new ArrayList<BlockBodyEntity>();
        for(var body:bodies)if(!body.cells().isEmpty()) {
            double reach=body.radius()+128;
            if(world.getPlayers().stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&p.getPos().squaredDistanceTo(body.physicsCentre())<=reach*reach))active.add(body);
        }
        State state=WORLDS.get(world);
        if(active.isEmpty()){if(state!=null)close(world);return;}
        if(state==null){state=new State();WORLDS.put(world,state);}
        var ids=new HashSet<UUID>();for(var body:active)ids.add(body.getUuid());
        for(var id:List.copyOf(state.leases.keySet()))if(!ids.contains(id))release(world,id);
        int budget=REQUESTS_PER_TICK,start=Math.floorMod(state.cursor++,active.size());
        for(int n=0;n<active.size();n++) {
            var body=active.get((start+n)%active.size());var lease=state.leases.computeIfAbsent(body.getUuid(),id->new Lease());
            BodyBounds bounds=body.loadingBounds();
            // Round-trip cache tolerance is protected by the two-block planning halo. This
            // avoids rebuilding hundreds of plans for sub-millimetre contact reactions.
            if(lease.planned==null||lease.revision!=body.shapeRevision()||!near(lease.planned,bounds)) {
                lease.wanted=BodyVolumePlan.columns(bounds,world.getBottomY(),world.getTopY());lease.planned=bounds;lease.revision=body.shapeRevision();
            }
            var desired=new HashMap<Long,BodyVolumePlan.Column>();for(var column:lease.wanted)desired.put(column.key(),column);
            for(var key:List.copyOf(lease.columns.keySet()))if(!desired.containsKey(key))drop(world,body.getUuid(),lease,lease.columns.get(key));
            for(var column:lease.wanted) {
                if(column.equals(lease.columns.get(column.key())))continue;
                if(budget==0)break;
                if(hold(world,body.getUuid(),column)) {lease.columns.put(column.key(),column);budget--;}
            }
        }
    }
    private static boolean near(BodyBounds a,BodyBounds b){return a.min().minus(b.min()).lengthSquared()<.25&&a.max().minus(b.max()).lengthSquared()<.25;}
    private static boolean hold(ServerWorld world,UUID body,BodyVolumePlan.Column column) {
        var backend=CubicAvailability.backend(world);if(backend==CubicAvailability.Backend.UNSUPPORTED)return false;
        var min=new BlockPos(column.x()*16,column.minY()*16,column.z()*16);
        var max=new BlockPos(column.x()*16+15,column.maxY()*16+15,column.z()*16+15);
        if(!world.getWorldBorder().contains(min)||!world.getWorldBorder().contains(max)
            ||!min.equals(BlockPos.fromLong(min.asLong()))||!max.equals(BlockPos.fromLong(max.asLong())))return false;
        if(backend==CubicAvailability.Backend.NATIVE_CUBIC&&!nativeUpdate(world,new Owner(body,column.key()),column))return false;
        // Level31 (radius2) also keeps the pivot's entity ticking when a rider is at the
        // opposite end of a large hull. Cubic data itself belongs to the native XYZ tracker.
        world.getChunkManager().addTicket(TICKET,new ChunkPos(column.x(),column.z()),2,body);return true;
    }
    private static void drop(ServerWorld world,UUID body,Lease lease,BodyVolumePlan.Column column) {
        world.getChunkManager().removeTicket(TICKET,new ChunkPos(column.x(),column.z()),2,body);
        if(CubicAvailability.backend(world)==CubicAvailability.Backend.NATIVE_CUBIC)nativeRemove(world,new Owner(body,column.key()));
        lease.columns.remove(column.key());
    }
    public static void release(ServerWorld world,UUID body) {
        State state=WORLDS.get(world);if(state==null)return;var lease=state.leases.remove(body);if(lease==null)return;
        for(var column:List.copyOf(lease.columns.values()))drop(world,body,lease,column);
    }
    private static void close(ServerWorld world) {
        var state=WORLDS.get(world);if(state==null)return;for(var id:List.copyOf(state.leases.keySet()))release(world,id);WORLDS.remove(world);
    }
    private static Object nativeTracker(ServerWorld world)throws ReflectiveOperationException {
        if(nativeLevel==null) {
            nativeLevel=Class.forName("dev.fallingcloud.bettercubicchunks.server.ICubicServerLevel").getMethod("bcc$cubic");
            tracker=Class.forName("dev.fallingcloud.bettercubicchunks.server.CubicServerLevel").getMethod("tracker");
            var type=Class.forName("dev.fallingcloud.bettercubicchunks.server.tracking.CubeTrackingView");
            view=type.getMethod("of",int.class,int.class,int.class,int.class,int.class,int.class,int.class);
            var index=Class.forName("dev.fallingcloud.bettercubicchunks.server.tracking.CubeViewTracker");
            update=index.getMethod("update",Object.class,type);remove=index.getMethod("remove",Object.class);
        }
        Object level=nativeLevel.invoke(world);return level==null?null:tracker.invoke(level);
    }
    private static boolean nativeUpdate(ServerWorld world,Owner owner,BodyVolumePlan.Column column) {
        if(nativeFailed)return false;
        try {
            Object index=nativeTracker(world);
            if(index==null)return false;
            // Exact one-column XYZ view, including all interior cube Ys. Unlike a radius
            // around the pivot, this does not hold a huge sphere of unrelated terrain.
            Object range=view.invoke(null,column.x(),column.centerY(),column.z(),0,column.radiusY(),column.minY(),column.maxY());
            update.invoke(index,owner,range);return true;
        }catch(ReflectiveOperationException|LinkageError e){fail(e);return false;}
    }
    private static void nativeRemove(ServerWorld world,Owner owner) {
        if(nativeFailed)return;
        try{Object index=nativeTracker(world);if(index!=null)remove.invoke(index,owner);}catch(ReflectiveOperationException|LinkageError e){fail(e);}
    }
    private static void fail(Throwable e){if(!nativeFailed)DescentMod.LOGGER.error("[drmd body volume] native XYZ lease API unavailable; hulls remain safely paused",e);nativeFailed=true;}
}

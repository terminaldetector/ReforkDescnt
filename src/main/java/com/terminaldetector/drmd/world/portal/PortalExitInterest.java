package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.contraption.BodyVolumeLeases;
import com.terminaldetector.drmd.world.contraption.BodyVolumePlan;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.world.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Nearby real users grant a small asynchronous destination lease, never permission to traverse.
 * PortalTransfer still requires actual FULL for the complete actor/group destination and sweeps. */
public final class PortalExitInterest {
    public static final int MAX_CANDIDATES=256,MAX_LINKS=8,MAX_COLUMNS_PER_LINK=9,MAX_CUBES_PER_LINK=27;
    public static final int PROBES_PER_TICK=32,REQUESTS_PER_TICK=16,NEARBY_DISTANCE=32;
    private static final int EXPIRY_TICKS=2;
    // Optional prefetch needs FULL data only. Radius2 additionally requests entity ticking
    // and a two-column FULL halo; travellers acquire ordinary interest after transfer.
    private static final int FULL_TICKET_RADIUS=0;
    private static final ChunkTicketType<UUID> TICKET=ChunkTicketType.create("drmd_portal_exit",Comparator.<UUID>naturalOrder());
    private record NativeOwner(UUID link,long column) {}
    private static final class Candidate {
        final BlockPos source;BlockPos destination;double halfSpan;long observed;
        Candidate(BlockPos source){this.source=source;}
    }
    private static final class Lease {
        final UUID owner=UUID.randomUUID();
        final Map<Long,BodyVolumePlan.Column> held=new HashMap<>();
        List<BodyVolumePlan.Column> wanted=List.of();BlockPos destination;double halfSpan;
    }
    private static final class State {
        final Map<BlockPos,Candidate> candidates=new HashMap<>();final ArrayDeque<Candidate> queue=new ArrayDeque<>();
        final Map<BlockPos,Lease> active=new LinkedHashMap<>();int cursor;long processed=Long.MIN_VALUE;
    }
    // Numeric metadata/UUID values never retain their weak world key or a block entity/player.
    private static final Map<ServerWorld,State> WORLDS=new WeakHashMap<>();
    private PortalExitInterest() {}
    public static void register(){
        ServerTickEvents.END_WORLD_TICK.register(PortalExitInterest::tick);
        ServerWorldEvents.UNLOAD.register((server,world)->close(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var world:server.getWorlds())close(world);});
    }
    /** Called only by a currently ticking, same-dimension linked portal BE before its far read. */
    public static void observe(ServerWorld world,BlockPos source,BlockPos destination,double halfSpan){
        if(!world.getServer().isOnThread())return; // Optional warmup never mutates from a native worker.
        if(source==null||destination==null||!Double.isFinite(halfSpan)||halfSpan<=0||halfSpan>4)return;
        var state=WORLDS.computeIfAbsent(world,ignored->new State());var candidate=state.candidates.get(source);
        if(candidate==null){
            if(state.candidates.size()>=MAX_CANDIDATES)return;
            if(state.queue.size()>=2*MAX_CANDIDATES){state.queue.clear();state.queue.addAll(state.candidates.values());}
            candidate=new Candidate(source.toImmutable());state.candidates.put(candidate.source,candidate);state.queue.addLast(candidate);
        }
        candidate.destination=destination.toImmutable();candidate.halfSpan=halfSpan;candidate.observed=world.getServer().getTicks();
    }
    private static Map<Long,List<Vec3d>> connectedPlayersByCell(ServerWorld world) {
        var positions = new HashMap<Long,List<Vec3d>>();
        // Cubic pilots can be absent from the ordinary world entity index. Snapshot positions
        // from the authoritative connection roster once, keeping the references local to this tick.
        for (var player : world.getServer().getPlayerManager().getPlayerList()) {
            if (player.isRemoved() || player.getWorld() != world || !player.isAlive() || player.isSpectator()) continue;
            var position = player.getPos();
            long cell = ChunkPos.toLong(playerCell(MathHelper.floor(position.x)), playerCell(MathHelper.floor(position.z)));
            positions.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(position);
        }
        return positions;
    }
    private static int playerCell(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, NEARBY_DISTANCE);
    }
    private static boolean nearby(Map<Long,List<Vec3d>> players, BlockPos source) {
        var centre = Vec3d.ofCenter(source);
        int cellX = playerCell(source.getX());
        int cellZ = playerCell(source.getZ());
        for (int x = cellX - 1; x <= cellX + 1; x++) {
            for (int z = cellZ - 1; z <= cellZ + 1; z++) {
                for (var position : players.getOrDefault(ChunkPos.toLong(x, z), List.of())) {
                    if (position.squaredDistanceTo(centre) <= NEARBY_DISTANCE * NEARBY_DISTANCE) return true;
                }
            }
        }
        return false;
    }
    private static void tick(ServerWorld world){
        var state=WORLDS.get(world);if(state==null)return;
        long now=world.getServer().getTicks();if(state.processed==now)return;state.processed=now;
        // All loops have explicit small bounds. Expired/unlinked source metadata grants no lease.
        for(var entry:List.copyOf(state.candidates.entrySet())){
            long age=now-entry.getValue().observed;
            if(age<0||age>EXPIRY_TICKS){releaseLease(world,state,entry.getKey());state.candidates.remove(entry.getKey());}
        }
        var players = state.candidates.isEmpty() ? Map.<Long,List<Vec3d>>of() : connectedPlayersByCell(world);
        var checked=new HashSet<BlockPos>();
        for(var source:List.copyOf(state.active.keySet())){
            checked.add(source);var candidate=state.candidates.get(source);
            if(candidate==null||!nearby(players,source))releaseLease(world,state,source);
            else plan(world,state,candidate);
        }
        int probes=MAX_LINKS; // Active leases above consume at most8 proximity probes.
        int steps=Math.min(PROBES_PER_TICK,state.queue.size());
        for(int i=0;i<steps&&probes<PROBES_PER_TICK;i++){
            var candidate=state.queue.removeFirst();if(state.candidates.get(candidate.source)!=candidate)continue;
            state.queue.addLast(candidate);
            if(checked.contains(candidate.source)||state.active.size()>=MAX_LINKS)continue;
            probes++;if(nearby(players,candidate.source))plan(world,state,candidate);
        }
        var leases=new ArrayList<>(state.active.values());int budget=REQUESTS_PER_TICK;
        int start=leases.isEmpty()?0:Math.floorMod(state.cursor++,leases.size());
        for(int n=0;n<leases.size()&&budget>0;n++){
            var lease=leases.get((start+n)%leases.size());
            for(var column:lease.wanted){
                if(column.equals(lease.held.get(column.key())))continue;
                if(budget==0)break;budget--;
                if(hold(world,lease.owner,column))lease.held.put(column.key(),column);
            }
        }
        if(state.queue.size()>2*MAX_CANDIDATES){state.queue.clear();state.queue.addAll(state.candidates.values());}
        if(state.candidates.isEmpty()&&state.active.isEmpty())WORLDS.remove(world);
    }
    private static void plan(ServerWorld world,State state,Candidate candidate){
        var lease=state.active.get(candidate.source);
        if(lease!=null&&candidate.destination.equals(lease.destination)&&candidate.halfSpan==lease.halfSpan)return;
        double halo=candidate.halfSpan+4;var centre=Vec3d.ofCenter(candidate.destination);
        List<BodyVolumePlan.Column> wanted;
        try{wanted=BodyVolumePlan.columns(new BodyBounds(new Vec3(centre.x-halo,centre.y-halo,centre.z-halo),
                    new Vec3(centre.x+halo,centre.y+halo,centre.z+halo)),world.getBottomY(),world.getTopY());}
        catch(IllegalArgumentException unsupported){releaseLease(world,state,candidate.source);return;}
        long cubes=0;for(var column:wanted)cubes+=(long)column.maxY()-column.minY()+1;
        if(wanted.isEmpty()||wanted.size()>MAX_COLUMNS_PER_LINK||cubes>MAX_CUBES_PER_LINK){releaseLease(world,state,candidate.source);return;}
        if(lease==null){if(state.active.size()>=MAX_LINKS)return;lease=new Lease();state.active.put(candidate.source,lease);}
        for(var column:List.copyOf(lease.held.values()))drop(world,lease,column);
        lease.wanted=wanted;lease.destination=candidate.destination;lease.halfSpan=candidate.halfSpan;
    }
    private static boolean hold(ServerWorld world,UUID owner,BodyVolumePlan.Column column){
        var backend=CubicAvailability.backend(world);if(backend==CubicAvailability.Backend.UNSUPPORTED)return false;
        var min=new BlockPos(column.x()*16,column.minY()*16,column.z()*16);
        var max=new BlockPos(column.x()*16+15,column.maxY()*16+15,column.z()*16+15);
        if(!world.getWorldBorder().contains(min)||!world.getWorldBorder().contains(max)
                ||!min.equals(BlockPos.fromLong(min.asLong()))||!max.equals(BlockPos.fromLong(max.asLong())))return false;
        if(backend==CubicAvailability.Backend.NATIVE_CUBIC&&!BodyVolumeLeases.requestNativeInterest(world,new NativeOwner(owner,column.key()),column))return false;
        // Both backends retain an asynchronous FULL-only vanilla column ticket. Native cube
        // ownership above remains unchanged; neither ticket asserts destination readiness.
        world.getChunkManager().addTicket(TICKET,new ChunkPos(column.x(),column.z()),FULL_TICKET_RADIUS,owner);return true;
    }
    private static void drop(ServerWorld world,Lease lease,BodyVolumePlan.Column column){
        world.getChunkManager().removeTicket(TICKET,new ChunkPos(column.x(),column.z()),FULL_TICKET_RADIUS,lease.owner);
        if(CubicAvailability.backend(world)!=CubicAvailability.Backend.STOCK)
            BodyVolumeLeases.releaseNativeInterest(world,new NativeOwner(lease.owner,column.key()));
        lease.held.remove(column.key());
    }
    private static void releaseLease(ServerWorld world,State state,BlockPos source){
        var lease=state.active.remove(source);if(lease==null)return;
        for(var column:List.copyOf(lease.held.values()))drop(world,lease,column);
    }
    public static void release(ServerWorld world,BlockPos source){
        // Worker-side removal cannot enqueue unbounded tasks; the next world tick expires it.
        if(!world.getServer().isOnThread())return;
        var state=WORLDS.get(world);if(state==null)return;
        state.candidates.remove(source);releaseLease(world,state,source);
    }
    private static void close(ServerWorld world){
        var state=WORLDS.get(world);if(state==null)return;
        for(var source:List.copyOf(state.active.keySet()))releaseLease(world,state,source);
        WORLDS.remove(world);
    }
    /** Debug/read-only status, never a transfer permission or fabricated FULL marker. */
    public static int heldColumns(ServerWorld world,BlockPos source){
        if(!world.getServer().isOnThread())return 0;
        var state=WORLDS.get(world);var lease=state==null?null:state.active.get(source);return lease==null?0:lease.held.size();
    }
}

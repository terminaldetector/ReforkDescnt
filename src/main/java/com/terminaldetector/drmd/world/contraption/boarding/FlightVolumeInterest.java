package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Bounded ordinary asynchronous native interest ahead of actual suit velocity. A lease is
 * never permission to move: BoardingWorldMotion still checks current FULL on both endpoints. */
public final class FlightVolumeInterest {
    public static final int MAX_PILOTS=32,MAX_COLUMNS=16,MAX_CUBES=64,REQUESTS_PER_TICK=16;
    private static final ChunkTicketType<UUID> TICKET=ChunkTicketType.create("drmd_flight_volume",Comparator.<UUID>naturalOrder());
    private record Owner(UUID player,long column){}
    private static final class Lease {
        final UUID player;final Map<Long,BodyVolumePlan.Column> held=new HashMap<>();List<BodyVolumePlan.Column> wanted=List.of();
        Lease(UUID player){this.player=player;}
    }
    private static final class State {final Map<UUID,Lease> leases=new LinkedHashMap<>();int cursor;long tick=Long.MIN_VALUE;}
    private static final Map<ServerWorld,State> WORLDS=new WeakHashMap<>();
    private FlightVolumeInterest(){}
    static void tick(ServerWorld world,List<ServerPlayerEntity> pilots){
        if(!world.getServer().isOnThread())throw new IllegalStateException("flight interest requires owner thread");
        if(CubicAvailability.backend(world)!=CubicAvailability.Backend.NATIVE_CUBIC){close(world);return;}
        var state=WORLDS.computeIfAbsent(world,ignored->new State());
        long now=world.getServer().getTicks();if(state.tick==now)return;state.tick=now;
        var present=new HashSet<UUID>();
        for(var player:pilots){
            if(present.size()>=MAX_PILOTS)break;
            if(player.getWorld()!=world||player.isRemoved())continue;
            var id=player.getUuid();present.add(id);
            var wanted=plan(player.getPos(),DescentPlayerData.get(player).getFlightVelocity(),player.getWidth(),player.getHeight(),world.getBottomY(),world.getTopY());
            if(wanted.isEmpty()){release(world,id);continue;}
            var lease=state.leases.computeIfAbsent(id,Lease::new);lease.wanted=wanted;
            var desired=new HashMap<Long,BodyVolumePlan.Column>();for(var column:wanted)desired.put(column.key(),column);
            for(var held:List.copyOf(lease.held.values()))if(!held.equals(desired.get(held.key())))drop(world,lease,held);
        }
        for(var id:List.copyOf(state.leases.keySet()))if(!present.contains(id))release(world,id);
        var leases=new ArrayList<>(state.leases.values());int budget=REQUESTS_PER_TICK;
        int start=leases.isEmpty()?0:Math.floorMod(state.cursor++,leases.size());
        for(int i=0;i<leases.size()&&budget>0;i++){
            var lease=leases.get((start+i)%leases.size());
            for(var column:lease.wanted){
                if(column.equals(lease.held.get(column.key())))continue;
                if(budget==0)break;budget--;
                if(hold(world,lease.player,column))lease.held.put(column.key(),column);
            }
        }
        if(state.leases.isEmpty())WORLDS.remove(world);
    }
    /** Three-quarter-second lookahead, capped at32m and64 real XYZ cubes per pilot. */
    public static List<BodyVolumePlan.Column> plan(Vec3d position,Vec3d velocity,double width,double height,int bottom,int top){
        if(!Double.isFinite(position.lengthSquared())||!Double.isFinite(velocity.lengthSquared())||!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0)return List.of();
        var step=velocity.multiply(.75);if(step.lengthSquared()>32*32)step=step.normalize().multiply(32);
        var box=new Box(position.x-width/2,position.y,position.z-width/2,position.x+width/2,position.y+height,position.z+width/2).stretch(step);
        List<BodyVolumePlan.Column> columns;
        try{columns=BodyVolumePlan.columns(new BodyBounds(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ)),bottom,top);}
        catch(IllegalArgumentException invalid){return List.of();}
        long count=0;for(var c:columns)count+=(long)c.maxY()-c.minY()+1;
        return columns.size()>MAX_COLUMNS||count>MAX_CUBES?List.of():columns;
    }
    private static boolean hold(ServerWorld world,UUID player,BodyVolumePlan.Column c){
        var min=new BlockPos(c.x()*16,c.minY()*16,c.z()*16);var max=new BlockPos(c.x()*16+15,c.maxY()*16+15,c.z()*16+15);
        if(!world.getWorldBorder().contains(min)||!world.getWorldBorder().contains(max)||!min.equals(BlockPos.fromLong(min.asLong()))||!max.equals(BlockPos.fromLong(max.asLong())))return false;
        if(!BodyVolumeLeases.requestNativeInterest(world,new Owner(player,c.key()),c))return false;
        world.getChunkManager().addTicket(TICKET,new ChunkPos(c.x(),c.z()),2,player);return true;
    }
    private static void drop(ServerWorld world,Lease lease,BodyVolumePlan.Column c){
        world.getChunkManager().removeTicket(TICKET,new ChunkPos(c.x(),c.z()),2,lease.player);
        BodyVolumeLeases.releaseNativeInterest(world,new Owner(lease.player,c.key()));lease.held.remove(c.key());
    }
    static void release(ServerWorld world,UUID player){
        var state=WORLDS.get(world);if(state==null)return;var lease=state.leases.remove(player);if(lease==null)return;
        for(var c:List.copyOf(lease.held.values()))drop(world,lease,c);
    }
    static void close(ServerWorld world){
        var state=WORLDS.get(world);if(state==null)return;
        for(var id:List.copyOf(state.leases.keySet()))release(world,id);WORLDS.remove(world);
    }
}

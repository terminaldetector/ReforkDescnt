package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.TeleportFlyerEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Bounded transaction phase after actual hull/boarding simulation; not an independent sensing/AI scheduler. */
public final class TeleportAbilityRelay {
    public enum Operation { START,COMMIT }
    private record Request(UUID actor,UUID lifetime,long epoch,long tick,Operation operation){}
    private static final int MAX_REQUESTS=16;
    private static final Map<ServerWorld,Frame> FRAMES=new WeakHashMap<>();
    private static final class Frame {
        long tick=Long.MIN_VALUE,submitted,processed,starts,commits,refused,stale;int maxPending,maxGranted,queries,maxHulls,maxActors,maxVolumes;
        String lastAdmission="NONE";
        final LinkedHashMap<UUID,Request> pending=new LinkedHashMap<>();
    }
    public record Stats(long tick,int pending,int maxPending,long submitted,long processed,long starts,long commits,long refused,long stale,
                        int maxGranted,int queries,int maxHulls,int maxActors,int maxVolumes,String lastAdmission){}
    private static final ThreadLocal<CommitPermit> COMMIT_CONTEXT=new ThreadLocal<>();
    /** Unforgeable, one-use, same-tick capability; a direct call cannot bypass the END authority phase. */
    public static final class CommitPermit {
        private final ServerWorld world;private final UUID actor,lifetime;private final long epoch,tick;private final Vec3d point;
        private boolean consumed;
        private CommitPermit(ServerWorld world,TeleportFlyerEntity actor,long epoch,Vec3d point){this.world=world;this.actor=actor.getUuid();this.lifetime=actor.lifetimeNonce();this.epoch=epoch;this.tick=world.getTime();this.point=point;}
        public long epoch(){return epoch;}
        public boolean consume(TeleportFlyerEntity value,Vec3d destination){
            if(consumed||COMMIT_CONTEXT.get()!=this||value.getWorld()!=world||world.getTime()!=tick||!world.getServer().isOnThread()
                ||!actor.equals(value.getUuid())||!lifetime.equals(value.lifetimeNonce())||!point.equals(destination)
                ||!value.ability().matches(TeleportAbilityState.Phase.COMMIT_PENDING,epoch))return false;
            consumed=true;return true;
        }
    }
    private static boolean registered;
    private TeleportAbilityRelay(){}
    public static void register(){
        if(registered)return;registered=true;
        ServerTickEvents.END_WORLD_TICK.register(TeleportAbilityRelay::endWorldTick);
        ServerWorldEvents.UNLOAD.register((server,world)->FRAMES.remove(world));ServerLifecycleEvents.SERVER_STOPPED.register(server->FRAMES.clear());
    }
    public static boolean registered(){return registered;}
    /** Retire only this actor's old-pose request; no other actor's allowance or state is reset. */
    public static void cancelPending(TeleportFlyerEntity actor){
        if(!(actor.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread())return;
        var frame=FRAMES.get(world);if(frame==null)return;var request=frame.pending.get(actor.getUuid());
        if(request!=null&&request.lifetime().equals(actor.lifetimeNonce())){frame.pending.remove(actor.getUuid());frame.stale++;}
    }
    public static boolean enqueue(TeleportFlyerEntity actor,Operation operation,long epoch){
        if(!registered)return false;
        if(!(actor.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()||!actor.serverEligible())return false;
        var frame=FRAMES.computeIfAbsent(world,w->new Frame());long now=world.getTime();
        if(frame.tick!=now){frame.stale+=frame.pending.size();frame.pending.clear();frame.tick=now;frame.queries=0;}
        if(frame.pending.size()>=MAX_REQUESTS||frame.pending.containsKey(actor.getUuid()))return false;
        frame.pending.put(actor.getUuid(),new Request(actor.getUuid(),actor.lifetimeNonce(),epoch,now,operation));frame.submitted++;frame.maxPending=Math.max(frame.maxPending,frame.pending.size());return true;
    }
    public static Stats stats(ServerWorld world){
        var f=FRAMES.get(world);return f==null?new Stats(Long.MIN_VALUE,0,0,0,0,0,0,0,0,0,0,0,0,0,"NONE"):new Stats(f.tick,f.pending.size(),f.maxPending,f.submitted,f.processed,f.starts,f.commits,f.refused,f.stale,f.maxGranted,f.queries,f.maxHulls,f.maxActors,f.maxVolumes,f.lastAdmission);
    }
    private static void endWorldTick(ServerWorld world){
        var frame=FRAMES.get(world);if(frame==null)return;
        var batch=List.copyOf(frame.pending.values());frame.pending.clear();
        for(var request:batch){
            var expected=request.operation()==Operation.START?TeleportAbilityState.Phase.START_PENDING:TeleportAbilityState.Phase.COMMIT_PENDING;
            var entity=world.getEntity(request.actor());
            if(request.tick()!=world.getTime()||!(entity instanceof TeleportFlyerEntity actor)||!actor.lifetimeNonce().equals(request.lifetime())
                ||!actor.serverEligible()||!actor.ability().matches(expected,request.epoch())){frame.stale++;continue;}
            frame.processed++;var budget=DroneNavigation.workBudget(world);boolean reserved=budget.begin(actor.getUuid(),world.getTime());
            if(!reserved){deny(frame,actor);continue;}
            try{
                int granted=budget.take(actor.getUuid(),world.getTime());frame.maxGranted=Math.max(frame.maxGranted,granted);
                var target=actor.chargedEnemy();if(granted<2||target==null||!TeleportFlyerGoal.visible(actor,target,true)||actor.getModEnergy()<TeleportFlyerEntity.TELEPORT_COST){deny(frame,actor);continue;}
                frame.queries++;int left=granted-1;
                if(request.operation()==Operation.START){
                    boolean accepted=false;
                    for(var offset:TeleportFlyerGoal.candidates(actor.getRotationVec(1),actor.getFlightRoll(),TeleportFlyerEntity.CANDIDATE_RADIUS)){
                        if(left--<=0)break;frame.queries++;var point=actor.getPos().add(offset);
                        if(admit(frame,world,actor,point)&&actor.beginWarning(request.epoch(),point)){accepted=true;frame.starts++;break;}
                    }
                    if(!accepted)deny(frame,actor);
                }else{
                    var point=actor.lockedDestination();frame.queries++;
                    boolean accepted=false;
                    if(point!=null&&admit(frame,world,actor,point)){
                        var permit=new CommitPermit(world,actor,request.epoch(),point);COMMIT_CONTEXT.set(permit);
                        try{accepted=actor.commitTeleport(permit);}finally{COMMIT_CONTEXT.remove();}
                    }
                    if(accepted)frame.commits++;else deny(frame,actor);
                }
            }finally{budget.release(actor.getUuid());}
        }
    }
    private static boolean admit(Frame frame,ServerWorld world,TeleportFlyerEntity actor,Vec3d point){
        if(actor.getPos().squaredDistanceTo(point)>TeleportFlyerEntity.MAX_COMMIT_DISPLACEMENT*TeleportFlyerEntity.MAX_COMMIT_DISPLACEMENT
            ||!world.shouldTickEntity(net.minecraft.util.math.BlockPos.ofFloored(point)))return false;
        var result=TeleportDestination.query(world,actor,point);frame.lastAdmission=result.reason().name();
        frame.maxHulls=Math.max(frame.maxHulls,result.hulls());frame.maxActors=Math.max(frame.maxActors,result.actors());frame.maxVolumes=Math.max(frame.maxVolumes,result.volumes());
        if(!result.clear())return false;
        if(!FlightRouteSpace.clearSight(actor,actor.getBoundingBox().getCenter(),point.add(0,actor.getHeight()*.5,0),TeleportFlyerEntity.MAX_COMMIT_DISPLACEMENT,true)){frame.lastAdmission="LINE_REFUSAL";return false;}
        return true;
    }
    private static void deny(Frame frame,TeleportFlyerEntity actor){frame.refused++;actor.cancelCharge();}
}

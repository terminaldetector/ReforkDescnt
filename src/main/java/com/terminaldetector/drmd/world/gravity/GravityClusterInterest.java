package com.terminaldetector.drmd.world.gravity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.function.Consumer;

/** Regional discovery reuses the existing field hash. Owned physics is never decimated. */
public final class GravityClusterInterest {
    public static final int QUERIES_PER_WORLD=64,CANDIDATES_PER_QUERY=128,REGIONS_PER_WORLD=128,DORMANT_CADENCE=4;
    private static final int WAKE_QUERIES=16;
    public enum Level { ACTIVE,NEAR,DORMANT }
    public record Stats(int queries,int returnedCandidates,int maxQueryCandidates,int essentialActors,int activeRegions,int nearRegions,int dormantRegions) {}
    private static final class State {
        long revision=Long.MIN_VALUE;List<Box> regions=List.of();int cursor;
        final LinkedHashSet<Vec3d> wake=new LinkedHashSet<>();
        Stats stats=new Stats(0,0,0,0,0,0,0);
    }
    private static final Map<ServerWorld,State> WORLDS=new WeakHashMap<>();
    static {
        ServerEntityEvents.ENTITY_LOAD.register((actor,world)->{
            if(actor instanceof LivingEntity&&!(actor instanceof PlayerEntity)&&GravityFields.sample(world,actor.getPos())!=null){
                var state=WORLDS.computeIfAbsent(world,w->new State());var p=actor.getPos();
                if(state.wake.size()<QUERIES_PER_WORLD)state.wake.add(new Vec3d(Math.floor(p.x/4)*4+2,Math.floor(p.y/4)*4+2,Math.floor(p.z/4)*4+2));
            }
        });
        ServerWorldEvents.UNLOAD.register((server,world)->WORLDS.remove(world));
    }
    private GravityClusterInterest() {}
    public static Stats stats(ServerWorld world){var state=WORLDS.get(world);return state==null?new Stats(0,0,0,0,0,0,0):state.stats;}
    static void clear(){WORLDS.clear();}
    static void discover(ServerWorld world,Collection<LivingEntity> essential,Set<UUID> processed,Consumer<LivingEntity> tick) {
        if(!world.getServer().isOnThread())throw new IllegalStateException("Gravity interest is world-owned");
        var state=WORLDS.computeIfAbsent(world,w->new State());long revision=GravityFields.revision();boolean dirty=state.revision!=revision;
        if(dirty){state.regions=GravityFields.interestRegions(world.getRegistryKey());state.revision=revision;state.cursor=0;}
        var regions=state.regions;int queries=0,total=0,maximum=0,active=0,near=0,dormant=0,visited=0,woken=0,scanStart=state.cursor;
        while(queries<QUERIES_PER_WORLD&&(visited<Math.min(REGIONS_PER_WORLD,regions.size())||woken<WAKE_QUERIES&&!state.wake.isEmpty())) {
            Box box;int regionIndex=-1;Level level=Level.DORMANT;boolean awakened=woken<WAKE_QUERIES&&!state.wake.isEmpty();
            if(awakened){var iterator=state.wake.iterator();var point=iterator.next();iterator.remove();box=new Box(point,point).expand(2);woken++;level=Level.ACTIVE;}
            else {
                regionIndex=(scanStart+visited++)%regions.size();box=regions.get(regionIndex);
                for(var actor:essential)if(box.expand(1).contains(actor.getPos())){level=Level.ACTIVE;break;}
                if(level==Level.DORMANT)for(var player:world.getPlayers())if(box.expand(32).contains(player.getPos())){level=Level.NEAR;break;}
            }
            if(level==Level.ACTIVE)active++;else if(level==Level.NEAR)near++;else dormant++;
            if(!dirty&&!awakened&&level==Level.DORMANT&&Math.floorMod(world.getTime(),DORMANT_CADENCE)!=0)continue;
            // A skipped dormant scan cannot advance the fair discovery cursor: otherwise a
            // cadence/region-count alias can permanently starve an entire source cluster.
            if(regionIndex>=0)state.cursor=(regionIndex+1)%regions.size();
            double minX=Math.max(world.getWorldBorder().getBoundWest(),box.minX),minY=Math.max(world.getBottomY(),box.minY),minZ=Math.max(world.getWorldBorder().getBoundNorth(),box.minZ);
            double maxX=Math.min(world.getWorldBorder().getBoundEast(),box.maxX),maxY=Math.min(world.getTopY(),box.maxY),maxZ=Math.min(world.getWorldBorder().getBoundSouth(),box.maxZ);
            if(maxX<=minX||maxY<=minY||maxZ<=minZ)continue;
            var actors=new ArrayList<LivingEntity>();
            world.collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class),new Box(minX,minY,minZ,maxX,maxY,maxZ),
                actor->!(actor instanceof PlayerEntity)&&actor.isAlive()&&!actor.isRemoved()&&!processed.contains(actor.getUuid())&&affected(world,actor),actors,CANDIDATES_PER_QUERY);
            queries++;total+=actors.size();maximum=Math.max(maximum,actors.size());
            for(var actor:actors)if(processed.add(actor.getUuid()))tick.accept(actor);
        }
        state.stats=new Stats(queries,total,maximum,essential.size(),active,near,dormant);
    }
    private static boolean affected(ServerWorld world,LivingEntity actor) {
        var sample=GravityFields.sample(world,actor.getPos());
        return sample!=null&&sample.strength()>=.05f;
    }
}

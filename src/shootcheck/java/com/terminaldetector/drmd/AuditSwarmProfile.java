package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import java.util.*;
import java.nio.file.Files;

/** Measured load profiles. The 128 baseline is an assertion; larger scenes report actual limits. */
public final class AuditSwarmProfile {
    private static final int[] COUNTS={128,256,512,1024};
    private static final Vec3d CENTRE=new Vec3d(12.5,207,20.5);
    private static boolean active;
    private static volatile boolean sampling,resultReady;
    private static volatile int stage,serverTicks;
    private static List<UUID> ids=List.of();
    private static UUID target;
    private static long tickStart,sampleStart,lastFrame;
    private static final List<Long> WORK=new ArrayList<>(),FRAMES=new ArrayList<>();
    private static final List<String> ROWS=new ArrayList<>();
    private static Map<UUID,Vec3d> initial=Map.of();
    private static int alive,moving,overlaps,maxCandidates;
    private static double sampleSeconds;
    private static Runnable complete;
    private AuditSwarmProfile() {}
    public static boolean active() {return active;}
    public static void start(MinecraftClient client,Runnable done) {
        active=true;complete=done;stage=0;
        ROWS.add("requested,server_alive,client_synced,moving,exact_overlaps,max_candidates,server_work_mean_ms,server_work_p95_ms,tps,frames,fps,frame_p95_ms,cleanup_remaining");
        ServerTickEvents.START_SERVER_TICK.register(server->{if(active)tickStart=System.nanoTime();});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!active || resultReady)return;
            serverTicks++;
            var world=server.getOverworld();
            if(serverTicks==35) {
                initial=new HashMap<>();
                for(UUID id:ids){var entity=world.getEntity(id);if(entity!=null)initial.put(id,entity.getPos());}
                WORK.clear();sampleStart=System.nanoTime();sampling=true;
            }
            if(sampling && serverTicks>35)WORK.add(System.nanoTime()-tickStart);
            if(serverTicks==95) {
                sampling=false;sampleSeconds=(System.nanoTime()-sampleStart)/1e9;
                alive=moving=overlaps=maxCandidates=0;Set<String> positions=new HashSet<>();
                for(UUID id:ids)if(world.getEntity(id) instanceof DroneEntity drone && drone.isAlive()) {
                    alive++;var start=initial.get(id);
                    if(start!=null&&drone.getPos().squaredDistanceTo(start)>.04)moving++;
                    String position=Math.round(drone.getX()*1000)+":"+Math.round(drone.getY()*1000)+":"+Math.round(drone.getZ()*1000);
                    if(!positions.add(position))overlaps++;
                    var stats=DroneSwarmAi.queryStats(drone);maxCandidates=Math.max(maxCandidates,stats.examinedCandidates());
                }
                if(maxCandidates>DroneSwarmAi.MAX_BOID_CANDIDATES)throw new IllegalStateException("Swarm candidate budget escaped");
                if(stage==0&&(alive!=128||moving<96))throw new IllegalStateException("128 baseline stalled: alive="+alive+" moving="+moving);
                resultReady=true;
            }
        });
        prepare(client);
    }
    private static void prepare(MinecraftClient client) {
        resultReady=true;sampling=false;FRAMES.clear();lastFrame=0;
        client.getServer().execute(()->{
            var world=client.getServer().getOverworld();client.getServer().setDifficulty(Difficulty.NORMAL,true);
            for(UUID id:ids) {var entity=world.getEntity(id);if(entity!=null)entity.discard();}
            if(target!=null){var entity=world.getEntity(target);if(entity!=null)entity.discard();}
            var cow=net.minecraft.entity.EntityType.COW.create(world);cow.setPosition(CENTRE.add(0,0,-30));cow.setNoGravity(true);
            cow.setInvulnerable(true);cow.setInvisible(true);cow.setAiDisabled(true);cow.setPersistent();world.spawnEntity(cow);target=cow.getUuid();
            List<UUID> created=new ArrayList<>();
            for(int i=0;i<COUNTS[stage];i++) {
                var drone=ModEntities.DRONE.create(world);
                drone.setPosition(CENTRE.add(((i&15)-7.5)*1.2,(((i>>8)&3)-1.5)*1.2,(((i>>4)&15)-7.5)*1.2));
                drone.applyRole(switch(i&3){case 0->AiRole.SUPPORT;case 1->AiRole.ASSAULT;case 2->AiRole.MG;default->AiRole.INTERCEPTOR;});
                drone.setPersistent();drone.setInvulnerable(true);world.spawnEntity(drone);drone.setTarget(cow);created.add(drone.getUuid());
            }
            ids=List.copyOf(created);DroneSwarmAi.clear();serverTicks=0;resultReady=false;
        });
    }
    public static void frameFinished() {
        if(!active)return;
        long now=System.nanoTime();
        if(sampling) {if(lastFrame!=0)FRAMES.add(now-lastFrame);lastFrame=now;} else lastFrame=0;
    }
    public static void tick(MinecraftClient client) {
        if(!active||!resultReady||serverTicks<95)return;
        Set<UUID> expected=Set.copyOf(ids);
        int synced=client.world.getEntitiesByClass(DroneEntity.class,new net.minecraft.util.math.Box(CENTRE.add(-128,-128,-128),CENTRE.add(128,128,128)),entity->expected.contains(entity.getUuid())).size();
        if(synced<COUNTS[stage])throw new IllegalStateException("Load profile did not synchronize all actors: "+synced+"/"+COUNTS[stage]);
        if(FRAMES.size()<2)throw new IllegalStateException("No real frame timing sample in load profile");
        var work=List.copyOf(WORK);var render=List.copyOf(FRAMES);
        String row=String.format(Locale.ROOT,"%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.3f,%d,%.3f,%.4f,",
                COUNTS[stage],alive,synced,moving,overlaps,maxCandidates,mean(work)/1e6,p95(work)/1e6,
                work.size()/sampleSeconds,render.size(),1e9/mean(render),p95(render)/1e6);
        AuditRenderCheck.queue("audit-swarm-"+COUNTS[stage],()->cleanup(client,row));
    }
    private static void cleanup(MinecraftClient client,String row) {
        resultReady=false;serverTicks=0;
        client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            for(UUID id:ids){var entity=world.getEntity(id);if(entity!=null)entity.discard();}
            if(target!=null){var entity=world.getEntity(target);if(entity!=null)entity.discard();}
            long remaining=ids.stream().filter(id->{var entity=world.getEntity(id);return entity!=null&&!entity.isRemoved();}).count();
            if(remaining!=0)throw new IllegalStateException("Swarm fixture cleanup left actors");
            ROWS.add(row+remaining);ids=List.of();target=null;DroneSwarmAi.clear();resultReady=true;
            if(++stage<COUNTS.length)client.execute(()->prepare(client));
            else client.execute(()->{
                try {Files.writeString(client.runDirectory.toPath().resolve("swarm-profile.csv"),String.join("\n",ROWS)+"\n");}
                catch(java.io.IOException e){throw new IllegalStateException(e);}
                active=false;complete.run();
            });
        });
    }
    private static double mean(List<Long> values){return values.stream().mapToLong(Long::longValue).average().orElse(0);}
    private static long p95(List<Long> values){var sorted=values.stream().mapToLong(Long::longValue).sorted().toArray();return sorted[Math.min(sorted.length-1,(int)Math.ceil(sorted.length*.95)-1)];}
}

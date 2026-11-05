package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.*;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Difficulty;
import java.nio.file.*;
import java.util.*;

/** Inherited 128/96 load baseline at ordinary and cubic altitude. Not Survival acceptance. */
public final class NativeSwarmBaselineCheck {
    private static final int COUNT=128;
    private static final List<DroneEntity> DRONES=new ArrayList<>();
    private static final List<Entity> PROJECTILES=new ArrayList<>();
    private static final Set<UUID> SHOOTERS=new HashSet<>(),DRAWN=new HashSet<>();
    private static volatile Set<UUID> expected=Set.of();
    private static final Map<UUID,Vec3d> initial=new HashMap<>();
    private static final Map<UUID,Integer> initialAge=new HashMap<>();
    private static final List<Long> WORK=new ArrayList<>(),FRAMES=new ArrayList<>();
    private static final List<String> ROWS=new ArrayList<>();
    private static volatile boolean located,running,sampling,resultReady,done;
    private static boolean requested,spawnRequested;private static volatile int stage,age;
    private static final boolean VERTICAL=Boolean.getBoolean("drmd.gate.verticalTargets");
    private static int ticks,frames,alive,moving,active,lookup,maxCandidates,shots,sampledShots,sampledShooters,indexed,targeting;
    private static long tickStart,sampleStart,lastFrame;private static double seconds;
    private static long captureStart;
    private static Entity cow;
    private NativeSwarmBaselineCheck(){}
    private static Entity entity(DroneEntity d){return d;}
    private static Vec3d centre(){return new Vec3d(12.5,stage==0?207:2156,20.5);}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    public static void register() {
        ROWS.add("height,requested,alive,active_ticks,moving,server_lookup,client_synced,unique_rendered,shots,shooters,indexed,targeting,max_candidates,server_p50_ms,server_p95_ms,server_p99_ms,observed_tps,frames,frame_p95_ms,cleanup_remaining");
        ServerTickEvents.START_SERVER_TICK.register(s->{if(running)tickStart=System.nanoTime();});
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(!running||resultReady||done)return;age++;
            if(age==35){initial.clear();initialAge.clear();for(var d:DRONES){initial.put(entity(d).getUuid(),entity(d).getPos());initialAge.put(entity(d).getUuid(),entity(d).age);}WORK.clear();sampleStart=System.nanoTime();sampling=true;}
            if(sampling&&age>35)WORK.add(System.nanoTime()-tickStart);
            if(age!=95)return;
            sampling=false;seconds=(System.nanoTime()-sampleStart)/1e9;alive=moving=active=lookup=maxCandidates=targeting=0;indexed=Integer.MAX_VALUE;
            for(var d:DRONES)if(entity(d).isAlive()&&!entity(d).isRemoved()) {
                alive++;var id=entity(d).getUuid();if(entity(d).getPos().squaredDistanceTo(initial.get(id))>.04)moving++;
                if(entity(d).age-initialAge.get(id)>=20)active++;
                if(s.getOverworld().getEntity(id)==d)lookup++;
                var stats=DroneSwarmAi.queryStats(d);maxCandidates=Math.max(maxCandidates,stats.examinedCandidates());indexed=Math.min(indexed,stats.indexedDrones());
                var target=((MobEntity)d).getTarget();if(NativeDroneStateCheck.WRITE?target!=null&&HostileEnvironment.isHostileTarget(d,target):target==cow)targeting++;
            }
            sampledShots=shots;sampledShooters=SHOOTERS.size();
            require(cow!=null&&cow.isAlive(),"the inherited invulnerable target must remain available throughout sampling");
            System.out.println("SWARM_BASELINE height="+centre().y+" alive="+alive+" active="+active+" moving="+moving+" shots="+sampledShots+" shooters="+sampledShooters+" lookup="+lookup);
            require(alive==COUNT&&active==COUNT&&moving>=96,"128 baseline must actually tick and move: alive="+alive+" active="+active+" moving="+moving);
            require(maxCandidates<=DroneSwarmAi.MAX_BOID_CANDIDATES,"bounded swarm neighbour work");
            require(indexed>=COUNT&&targeting>=96,"live drones must remain indexed and engaged: indexed="+indexed+" targeting="+targeting);
            require(sampledShots>0&&sampledShooters>=16,"baseline AI must spawn real shots from multiple agents");resultReady=true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(NativeSwarmBaselineCheck::tick);
    }
    private static boolean loaded(MinecraftClient c) {
        var at=BlockPos.ofFloored(centre());var access=WorldAccess.of(c.world);
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-3;z<=2;z++)if(!access.isVolumeAvailable(new D6ChunkPos((at.getX()>>4)+x,(at.getY()>>4)+y,(at.getZ()>>4)+z)))return false;
        return true;
    }
    private static void tick(MinecraftClient c) {
        if(done)return;require(++ticks<4800,"native 128-drone baseline timeout stage="+stage+" age="+age);
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())
            if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(!requested) {
            require(c.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"),"disposable runtime only");requested=true;c.setScreen(null);c.options.hudHidden=true;c.options.setPerspective(Perspective.FIRST_PERSON);
            com.terminaldetector.drmd.client.config.DescentConfig.weaponView=false;com.terminaldetector.drmd.client.config.DescentConfig.cockpit=false;
            com.terminaldetector.drmd.client.config.DescentConfig.planetFloor=false;com.terminaldetector.drmd.client.config.DescentConfig.levelSky=false;
            c.getServer().execute(()->{
                var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());var d=DescentPlayerData.get(p);var at=centre();
                com.terminaldetector.drmd.flight.FlightSystem.disable(p,d);
                p.teleport(p.getServerWorld(),at.x,at.y+12,at.z+6,Set.of(),180,40);p.setInvulnerable(true);
                d.setGravity(0);d.setDrag(0);d.setFlightVelocity(Vec3d.ZERO);com.terminaldetector.drmd.flight.FlightSystem.enableWithoutMount(p,d);
                c.getServer().setDifficulty(Difficulty.NORMAL,true);p.getServerWorld().setTimeOfDay(6000);located=true;
            });return;
        }
        if(located&&!spawnRequested&&loaded(c)){spawnRequested=true;DRAWN.clear();FRAMES.clear();lastFrame=0;c.getServer().execute(()->spawn(c.getServer().getOverworld()));}
    }
    private static void spawn(ServerWorld w) {
        System.out.println("SWARM_CONFIG seed="+w.getSeed()+" centre="+centre()+" difficulty="+w.getDifficulty()+" doMobSpawning="+w.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_SPAWNING)+" roles=SUPPORT,ASSAULT,MG,INTERCEPTOR target="+((NativeDroneStateCheck.WRITE||VERTICAL)?"targetable-resistance-cow":"invulnerable-invisible-cow")+" verticalTargets="+VERTICAL+" manualTargets="+!VERTICAL+" backgroundLOD=false manualDroneTicks=false");
        var at=centre();var target=EntityType.COW.create(w);require(target!=null,"real passive target");target.setPosition(at.add(0,VERTICAL?(stage==0?20:-20):0,VERTICAL?0:-30));target.setNoGravity(true);target.setInvulnerable(!(NativeDroneStateCheck.WRITE||VERTICAL));if(NativeDroneStateCheck.WRITE||VERTICAL)target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE,-1,4,false,false));target.setInvisible(!(NativeDroneStateCheck.WRITE||VERTICAL));target.setAiDisabled(true);target.setPersistent();require(w.spawnEntity(target),"target spawned");cow=target;
        DRONES.clear();PROJECTILES.clear();SHOOTERS.clear();shots=0;
        for(int i=0;i<COUNT;i++) {
            var d=ModEntities.DRONE.create(w);require(d!=null,"registered drone created");entity(d).setPosition(at.add(((i&15)-7.5)*1.2,(((i>>8)&3)-1.5)*1.2,(((i>>4)&15)-7.5)*1.2));
            d.applyRole(switch(i&3){case 0->AiRole.SUPPORT;case 1->AiRole.ASSAULT;case 2->AiRole.MG;default->AiRole.INTERCEPTOR;});
            ((MobEntity)d).setPersistent();entity(d).setInvulnerable(true);require(w.spawnEntity(d),"drone spawn accepted");if(!VERTICAL)((MobEntity)d).setTarget(target);DRONES.add(d);
        }
        expected=Set.copyOf(DRONES.stream().map(d->entity(d).getUuid()).toList());DroneSwarmAi.clear();age=0;captureStart=0;resultReady=false;running=true;
    }
    public static void shot(WeaponCore.FireConfig config,ProjectileEntity shot) {
        if(!running||done||shot==null||config.owner==null||!expected.contains(config.owner.getUuid()))return;
        PROJECTILES.add(shot);if(!resultReady){shots++;SHOOTERS.add(config.owner.getUuid());}
    }
    public static void drew(DroneEntity d){if(expected.contains(entity(d).getUuid()))DRAWN.add(entity(d).getUuid());}
    public static void rendered() {
        if(done)return;long now=System.nanoTime();if(sampling){if(lastFrame!=0)FRAMES.add(now-lastFrame);lastFrame=now;}else lastFrame=0;
        if(!resultReady)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null||++frames<3)return;
        int synced=c.world.getEntitiesByClass(DroneEntity.class,new Box(centre().add(-128,-128,-128),centre().add(128,128,128)),d->expected.contains(entity(d).getUuid())).size();
        if(captureStart==0){captureStart=now;System.out.println("SWARM_CAMERA pos="+c.player.getPos()+" yaw="+c.player.getYaw()+" pitch="+c.player.getPitch()+" camera="+c.gameRenderer.getCamera().getRotation());}
        if((synced!=COUNT||DRAWN.size()<32)&&now-captureStart<8_000_000_000L)return;
        require(FRAMES.size()>=2&&!WORK.isEmpty(),"real timing samples");
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            image.writeTo(c.runDirectory.toPath().resolve("swarm-128-height-"+(int)centre().y+".png"));
            require(synced==COUNT&&DRAWN.size()>=32,"all live actors must synchronize and models must render: synced="+synced+" drawn="+DRAWN.size());
            String row=String.format(Locale.ROOT,"%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.3f,%d,%.4f,",(int)centre().y,COUNT,alive,active,moving,lookup,synced,DRAWN.size(),sampledShots,sampledShooters,indexed,targeting,maxCandidates,percentile(WORK,.5)/1e6,percentile(WORK,.95)/1e6,percentile(WORK,.99)/1e6,WORK.size()/seconds,FRAMES.size(),percentile(FRAMES,.95)/1e6);
            if(NativeDroneStateCheck.WRITE&&stage==1) {
                running=false;sampling=false;done=true;ROWS.add(row+128);
                Files.writeString(c.runDirectory.toPath().resolve("swarm-before-restart.csv"),String.join("\n",ROWS)+"\n");
                c.getServer().execute(()->{
                    PROJECTILES.forEach(Entity::discard);
                    for(int i=0;i<DRONES.size();i++){var d=DRONES.get(i);((LivingEntity)d).setHealth(((LivingEntity)d).getMaxHealth()*.63f);d.setFlightRoll((i&1)==0?25:-25);}
                    NativeDroneStateCheck.watch(expected);c.execute(c::scheduleStop);
                });return;
            }
            resultReady=false;running=false;frames=0;c.getServer().execute(()->cleanup(c,row));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void cleanup(MinecraftClient c,String row) {
        DRONES.forEach(d->entity(d).discard());PROJECTILES.forEach(Entity::discard);if(cow!=null)cow.discard();
        long remaining=DRONES.stream().filter(d->!entity(d).isRemoved()).count();require(remaining==0,"fixture actors removed");ROWS.add(row+remaining);DRONES.clear();PROJECTILES.clear();expected=Set.of();DroneSwarmAi.clear();
        c.execute(()->{
            if(++stage<2){located=false;requested=false;spawnRequested=false;return;}
            try{Files.writeString(c.runDirectory.toPath().resolve("swarm-baseline.csv"),String.join("\n",ROWS)+"\n");Files.writeString(c.runDirectory.toPath().resolve("swarm-baseline-ok.json"),"{\"naturalVerticalTargets\":"+VERTICAL+",\"heights\":[207,2156],\"actorsPerCase\":128,\"minimumMoving\":96,\"realTicksAndShots\":true,\"fullSurvivalOrPersistenceAcceptance\":false}\n");done=true;c.scheduleStop();}catch(Exception e){throw new IllegalStateException(e);}
        });
    }
    private static double percentile(List<Long> values,double fraction){var sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get(Math.max(0,(int)Math.ceil(fraction*sorted.size())-1));}
}

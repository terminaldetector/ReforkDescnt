package com.terminaldetector.drmd;

import com.google.gson.*;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Passive post-production END observer, with bounded proof output and no commit/tick/pose API calls. */
final class TeleportFlyerTestProbe {
    static final class Record {
        long warningTick=Long.MIN_VALUE,warningEpoch=Long.MIN_VALUE,commitTick=Long.MIN_VALUE;Vec3d warned,arrived,preCommitVelocity,commitVelocity;
        float beforeEnergy,afterEnergy;int frames,dropped;long lastBlinks;boolean oneOwner;
        long targetHitAttempts,targetAppliedEvents,lastHitTick=Long.MIN_VALUE;float lastIncomingDamage,lastAppliedDamage;boolean lastBlocked;JsonObject beforeAssertions;
        Death death;RagdollEntity corpse;int corpseAgeAtCapture=-1;long corpseCaptureTick=Long.MIN_VALUE;String corpseCapturePhase="NONE";boolean corpseRegisteredAtCapture;net.minecraft.nbt.NbtCompound corpseTag;List<Box> initialBounds=List.of();
        final BufferedWriter output;
        Record(Path path)throws IOException{output=Files.newBufferedWriter(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
    }
    record Death(Vec3d feet,Vec3d centre,Vec3d velocity,float yaw,float pitch,float bank,Vec3d cause,long tick){}
    private static final Map<TeleportFlyerFixture,Record> ACTIVE=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Path> DEFAULT_PROOF_DIRECTORIES=new WeakHashMap<>();
    static {
        ServerTickEvents.END_WORLD_TICK.register(world->{
            for(var entry:List.copyOf(ACTIVE.entrySet()))if(entry.getKey().world==world)observe(entry.getKey(),entry.getValue());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            for(var entry:ACTIVE.entrySet())if(entity==entry.getKey().actor){var a=entry.getKey().actor;
                entry.getValue().death=new Death(a.getPos(),a.getBoundingBox().getCenter(),a.getVelocity(),a.getYaw(),a.getPitch(),a.getFlightRoll(),source.getSource()==null?null:source.getSource().getPos(),a.getWorld().getTime());}
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            for(var entry:ACTIVE.entrySet())if(entry.getKey().diagnostic&&entity==entry.getKey().enemy&&source.getAttacker()==entry.getKey().actor){
                var r=entry.getValue();r.targetHitAttempts++;r.lastIncomingDamage=amount;r.lastHitTick=entity.getWorld().getTime();
            }
            return true; // Strictly passive; never alter actual physical damage permission.
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,amount,blocked)->{
            for(var entry:ACTIVE.entrySet())if(entry.getKey().diagnostic&&entity==entry.getKey().enemy&&source.getAttacker()==entry.getKey().actor){
                var r=entry.getValue();r.targetAppliedEvents++;r.lastAppliedDamage=amount;r.lastBlocked=blocked;r.lastHitTick=entity.getWorld().getTime();
            }
        });
        ServerWorldEvents.UNLOAD.register((server,world)->{for(var rig:List.copyOf(ACTIVE.keySet()))if(rig.world==world)close(rig);});
    }
    private TeleportFlyerTestProbe(){}
    static synchronized Path proofDirectory(MinecraftServer server)throws IOException{
        String configured=System.getProperty("drmd.test.u07ProofDir");
        if(configured!=null&&!configured.isBlank())return Files.createDirectories(Path.of(configured));

        var directory=DEFAULT_PROOF_DIRECTORIES.get(server);
        if(directory==null){
            var reports=server.getRunDirectory().resolve("reports").resolve("teleport-flyer-proof");
            directory=Files.createTempDirectory(Files.createDirectories(reports),"run-");
            DEFAULT_PROOF_DIRECTORIES.put(server,directory);
            System.out.println("U07_PROOF_DIRECTORY "+directory.toAbsolutePath());
        }
        return directory;
    }
    static void attach(TeleportFlyerFixture rig){
        if(ACTIVE.size()>=4)throw new IllegalStateException("bounded4 U07 probe rigs");
        try{var parent=proofDirectory(rig.world.getServer());ACTIVE.put(rig,new Record(parent.resolve("u07-"+rig.actor.getUuid()+".jsonl")));}
        catch(IOException e){throw new IllegalStateException(e);}
    }
    static Record record(TeleportFlyerFixture rig){return ACTIVE.get(rig);}
    /** Fresh scalars after the existing sample and before unchanged gameplay assertions; no world query or state write. */
    static void beforeAssertions(TeleportFlyerFixture rig){
        if(!rig.diagnostic)return;var r=ACTIVE.get(rig);if(r==null)return;var actor=rig.actor;var row=new JsonObject();
        row.addProperty("samplePhase","BEFORE_UNCHANGED_ASSERTIONS");row.addProperty("worldTick",rig.world.getTime());row.addProperty("fixtureTick",rig.ticks);row.addProperty("actorAge",actor.age);
        row.addProperty("actor",actor.getUuid().toString());row.addProperty("target",rig.enemy.getUuid().toString());row.addProperty("phase",actor.ability().phase().name());row.addProperty("remaining",actor.ability().remaining());row.addProperty("cooldown",actor.ability().cooldown());row.addProperty("epoch",actor.ability().epoch());row.addProperty("blinks",actor.teleportCount());
        row.addProperty("baselineWarningTick",r.warningTick);row.addProperty("baselineWarningEpoch",r.warningEpoch);row.addProperty("baselineWarning",r.warned==null?"none":r.warned.toString());row.addProperty("commitTick",r.commitTick);
        var locked=actor.lockedDestination();var displayed=actor.displayedDestination();row.addProperty("locked",locked==null?"none":locked.toString());row.addProperty("displayed",displayed.toString());
        if(locked!=null){row.addProperty("displayedSquaredError",displayed.squaredDistanceTo(locked));if(r.warned!=null)row.addProperty("firstBaselineSquaredError",r.warned.squaredDistanceTo(locked));}
        row.addProperty("freshOwnVisible",rig.freshOwn);row.addProperty("everOwnVisible",rig.ownAcquired);row.addProperty("observedOwnedShotIds",rig.shots.size());row.addProperty("actualMovingOwnedShot",rig.movingShot);row.addProperty("sampledAttributedDamage",rig.actualDamage);
        row.addProperty("enemyHealth",rig.enemy.getHealth());row.addProperty("enemyInitialHealth",rig.initialEnemyHealth);row.addProperty("enemyAttacker",rig.enemy.getAttacker()==null?"none":rig.enemy.getAttacker().getUuid().toString());row.addProperty("enemyHurtTime",rig.enemy.hurtTime);row.addProperty("enemyRegenTimer",rig.enemy.timeUntilRegen);row.addProperty("enemyCentre",rig.enemy.getBoundingBox().getCenter().toString());
        row.addProperty("physicalHitAttempts",r.targetHitAttempts);row.addProperty("afterDamageEvents",r.targetAppliedEvents);row.addProperty("lastHitTick",r.lastHitTick);row.addProperty("lastIncomingDamage",r.lastIncomingDamage);row.addProperty("lastAppliedDamage",r.lastAppliedDamage);row.addProperty("lastBlocked",r.lastBlocked);
        row.addProperty("sampledLiveOwnedProjectiles",rig.sampledProjectileCount);row.addProperty("projectileSampleCap",48);row.addProperty("projectileSampleOverflow",rig.projectileSampleOverflow);row.add("actualProjectiles",rig.sampledProjectiles);r.beforeAssertions=row;
    }
    /** Coarse factory-layer test only: read the actual production spawn after damage() returned,
     * before this earlier GameTest callback yields to ordinary entity iteration. No new factory/tick/pose call. */
    static void captureAfterActualDamageReturn(TeleportFlyerFixture rig){
        var r=ACTIVE.get(rig);if(r==null||rig.closed||!rig.spawned)throw new IllegalStateException("actual active spawned fixture required for passive birth readback");
        captureCorpse(rig,r,"AFTER_ACTUAL_DAMAGE_RETURN");
    }
    private static void captureCorpse(TeleportFlyerFixture rig,Record r,String phase){
        if(r.death==null||r.corpse!=null)return;
        var found=new ArrayList<RagdollEntity>(2);
        rig.world.collectEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(RagdollEntity.class),new Box(r.death.centre(),r.death.centre()).expand(6),Entity::isAlive,found,2);
        if(!found.isEmpty()){
            r.oneOwner=found.size()==1;r.corpse=found.getFirst();r.corpseAgeAtCapture=r.corpse.age;r.corpseCaptureTick=rig.world.getTime();r.corpseCapturePhase=phase;
            r.corpseRegisteredAtCapture=rig.world.getEntity(r.corpse.getUuid())==r.corpse;
            r.initialBounds=List.copyOf(r.corpse.partBounds());r.corpseTag=new net.minecraft.nbt.NbtCompound();r.corpse.saveSelfNbt(r.corpseTag);
        }
    }
    private static void observe(TeleportFlyerFixture rig,Record r){
        if(rig.closed||!rig.spawned)return;var a=rig.actor;var phase=a.ability().phase();
        if(phase==TeleportAbilityState.Phase.WARN&&r.warningTick==Long.MIN_VALUE){r.warningTick=rig.world.getTime();r.warningEpoch=a.ability().epoch();r.warned=a.lockedDestination();}
        if(a.teleportCount()>r.lastBlinks&&r.commitTick==Long.MIN_VALUE){r.commitTick=rig.world.getTime();r.arrived=a.getPos();r.commitVelocity=a.getVelocity();r.afterEnergy=a.getModEnergy();}
        else if(r.commitTick==Long.MIN_VALUE){r.beforeEnergy=a.getModEnergy();r.preCommitVelocity=a.getVelocity();}
        r.lastBlinks=a.teleportCount();
        captureCorpse(rig,r,"END_WORLD_TICK");
        var row=new JsonObject();row.addProperty("worldTick",rig.world.getTime());row.addProperty("actorAge",a.age);row.addProperty("phase",phase.name());row.addProperty("remaining",a.ability().remaining());row.addProperty("cooldown",a.ability().cooldown());
        row.addProperty("epoch",a.ability().epoch());row.addProperty("blinks",a.teleportCount());row.addProperty("energy",a.getModEnergy());row.addProperty("health",a.getHealth());row.addProperty("shield",a.getDroneShield());
        row.addProperty("x",a.getX());row.addProperty("y",a.getY());row.addProperty("z",a.getZ());row.addProperty("vx",a.getVelocity().x);row.addProperty("vy",a.getVelocity().y);row.addProperty("vz",a.getVelocity().z);
        row.addProperty("yaw",a.getYaw());row.addProperty("pitch",a.getPitch());row.addProperty("bank",a.getFlightRoll());row.addProperty("target",a.getTarget()==null?"none":a.getTarget().getUuid().toString());
        if(a.lockedDestination()!=null)row.addProperty("lockedDestination",a.lockedDestination().toString());
        var s=TeleportAbilityRelay.stats(rig.world);row.addProperty("relayTick",s.tick());row.addProperty("relayPending",s.pending());row.addProperty("maxPending",s.maxPending());row.addProperty("granted",s.maxGranted());row.addProperty("queries",s.queries());row.addProperty("lastAdmission",s.lastAdmission());
        row.addProperty("maxVolumes",s.maxVolumes());row.addProperty("maxHulls",s.maxHulls());row.addProperty("maxActors",s.maxActors());
        if(r.corpse!=null){row.addProperty("corpse",r.corpse.getUuid().toString());row.addProperty("corpseAgeAtCapture",r.corpseAgeAtCapture);row.addProperty("corpseCaptureTick",r.corpseCaptureTick);row.addProperty("corpseCapturePhase",r.corpseCapturePhase);row.addProperty("corpseRegisteredAtCapture",r.corpseRegisteredAtCapture);row.addProperty("oneCorpse",r.oneOwner);row.addProperty("initialCorpseBounds",r.initialBounds.toString());}
        if(r.beforeAssertions!=null)row.add("beforeAssertions",r.beforeAssertions);
        write(r,row,false);
    }
    private static void write(Record r,JsonObject row,boolean last){
        if(!last&&r.frames>=255){r.dropped++;return;}row.addProperty("row",++r.frames);
        try{r.output.write(row.toString());r.output.newLine();r.output.flush();}catch(IOException e){throw new IllegalStateException(e);}
    }
    static void close(TeleportFlyerFixture rig){
        var r=ACTIVE.remove(rig);if(r==null)return;var last=new JsonObject();last.addProperty("event","CLOSE");last.addProperty("dropped",r.dropped);last.addProperty("maxRows",256);if(r.beforeAssertions!=null)last.add("beforeAssertions",r.beforeAssertions);write(r,last,true);
        try{r.output.close();}catch(IOException e){throw new IllegalStateException(e);}
    }
}

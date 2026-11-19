package com.terminaldetector.drmd;

import com.google.gson.*;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.TypeFilter;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Flag-gated passive observer. No damage return changes, source rewriting, ticks or fixture state changes. */
final class ShapesCombatTrace {
    private static final int MAX_RIGS=4,MAX_ROWS=512,MAX_SHOT_IDS=256,MAX_PROJECTILE_CANDIDATES=96;
    private static final Map<ShapesFixture,Trace> ACTIVE=new IdentityHashMap<>();
    private static long refusedRigs;
    static {
        // Fabric1.21.1 ALLOW_DAMAGE is injected before isSleeping(), ahead of vanilla's hurt-window branch.
        // AFTER_DAMAGE is TAIL only: early false returns (including equal damage during hurt cooldown) skip it.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            for(var trace:ACTIVE.values())if(trace.contains(entity))trace.damage("ATTEMPT",entity,source,amount,Float.NaN,false);
            return true; // Observer never changes permission, even when a trace record is dropped.
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,amount,blocked)->{
            for(var trace:ACTIVE.values())if(trace.contains(entity))trace.damage("AFTER_DAMAGE",entity,source,base,amount,blocked);
        });
        ServerWorldEvents.UNLOAD.register((server,world)->{
            for(var rig:List.copyOf(ACTIVE.keySet()))if(rig.world==world)close(rig);
        });
    }
    private ShapesCombatTrace(){}
    static void attach(ShapesFixture rig){
        if(ACTIVE.size()>=MAX_RIGS){refusedRigs++;return;}
        try{ACTIVE.put(rig,new Trace(rig));}catch(IOException e){throw new IllegalStateException("Shapes passive trace admission failed",e);}
    }
    static void sample(ShapesFixture rig){var trace=ACTIVE.get(rig);if(trace!=null&&rig.ticks%5==0)trace.sample();}
    static void close(ShapesFixture rig){var trace=ACTIVE.remove(rig);if(trace!=null)trace.close();}
    private static JsonElement id(Entity entity){return entity==null?JsonNull.INSTANCE:new JsonPrimitive(entity.getUuid().toString());}
    private static JsonObject actor(Entity e){
        var row=new JsonObject();row.add("uuid",id(e));row.addProperty("type",net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString());
        row.addProperty("x",e.getX());row.addProperty("y",e.getY());row.addProperty("z",e.getZ());
        row.addProperty("vx",e.getVelocity().x);row.addProperty("vy",e.getVelocity().y);row.addProperty("vz",e.getVelocity().z);row.addProperty("age",e.age);
        if(e instanceof net.minecraft.entity.mob.MobEntity mob){
            row.add("targetUuid",id(mob.getTarget()));row.addProperty("navigationIdle",mob.getNavigation().isIdle());var path=mob.getNavigation().getCurrentPath();
            if(path!=null){row.addProperty("pathIndex",path.getCurrentNodeIndex());row.addProperty("pathLength",path.getLength());row.addProperty("pathDestination",path.getTarget().toShortString());}
        }
        if(e instanceof LivingEntity living){row.addProperty("health",living.getHealth());row.add("sampledAttacker",id(living.getAttacker()));row.addProperty("hurtTime",living.hurtTime);row.addProperty("regenTimer",living.timeUntilRegen);}
        return row;
    }
    private static final class Trace {
        final ShapesFixture rig;final BufferedWriter output;final Set<UUID> seenShots=new HashSet<>();
        final long[][] attempts=new long[3][3],after=new long[3][3];
        int rows;long droppedRows,droppedShotIds;
        Trace(ShapesFixture rig)throws IOException{
            this.rig=rig;String directory=System.getProperty("drmd.test.shapesTraceDir");if(directory==null||directory.isBlank())throw new IOException("fresh shapesTraceDir required");
            var parent=Path.of(directory);Files.createDirectories(parent);
            output=Files.newBufferedWriter(parent.resolve("shapes-"+rig.infantry.getUuid()+".jsonl"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            var row=new JsonObject();row.addProperty("event","ATTACH");row.addProperty("maxRows",MAX_ROWS);row.addProperty("maxRigs",MAX_RIGS);row.addProperty("maxShotIds",MAX_SHOT_IDS);row.addProperty("fixtureClass",rig.getClass().getName());
            row.add("infantry",actor(rig.infantry));if(rig.scout!=null)row.add("scout",actor(rig.scout));row.add("victim",actor(rig.victim));write(row,false);
        }
        boolean contains(LivingEntity entity){return entity==rig.victim||entity==rig.infantry||entity==rig.scout;}
        int shooter(Entity entity){return entity==rig.infantry?0:entity==rig.scout?1:2;}
        int recipient(Entity entity){return entity==rig.victim?0:entity==rig.infantry?1:2;}
        void damage(String event,LivingEntity entity,DamageSource source,float base,float amount,boolean blocked){
            int s=shooter(source.getAttacker()),r=recipient(entity);if(event.equals("ATTEMPT"))attempts[s][r]++;else after[s][r]++;
            var row=new JsonObject();row.addProperty("event",event);row.add("recipient",actor(entity));row.add("sourceAttacker",id(source.getAttacker()));row.add("sourceDirectEntity",id(source.getSource()));
            row.addProperty("baseOrIncomingDamage",base);if(Float.isFinite(amount))row.addProperty("eventDamage",amount);row.addProperty("blocked",blocked);
            // WeaponCore.mobAttack uses the living shooter as DamageSource, not a projectile UUID.
            // Actual projectile IDs are observed independently in FRAME rows, never invented here.
            write(row,false);
        }
        void sample(){
            var row=new JsonObject();row.addProperty("event","FRAME");row.add("infantry",actor(rig.infantry));if(rig.scout!=null)row.add("scout",actor(rig.scout));row.add("victim",actor(rig.victim));
            row.addProperty("ownVisible",rig.infantry.canSee(rig.victim));row.addProperty("preOwnInvestigation",rig.preOwnInvestigation);row.addProperty("sampledAttributedDamage",rig.attributedDamage);
            var observation=com.terminaldetector.drmd.ai.DroneSquadCoordinator.message(rig.infantry);if(observation.isPresent()){
                var m=observation.get();var mail=new JsonObject();mail.addProperty("scout",m.scout().toString());mail.addProperty("target",m.target().toString());mail.addProperty("x",m.x());mail.addProperty("y",m.y());mail.addProperty("z",m.z());mail.addProperty("observedAt",m.observedAt());mail.addProperty("expiresAt",m.expiresAt());row.add("mail",mail);
            }
            var shots=new JsonArray();int included=0;var candidates=new ArrayList<ProjectileEntity>();
            var area=rig.infantry.getBoundingBox().union(rig.victim.getBoundingBox());if(rig.scout!=null)area=area.union(rig.scout.getBoundingBox());
            rig.world.collectEntitiesByType(TypeFilter.instanceOf(ProjectileEntity.class),area.expand(128),Entity::isAlive,candidates,MAX_PROJECTILE_CANDIDATES);
            row.addProperty("projectileCandidateCount",candidates.size());row.addProperty("projectileCandidateCapReached",candidates.size()==MAX_PROJECTILE_CANDIDATES);
            for(var shot:candidates)if(shot.getOwnerLiving()==rig.infantry||shot.getOwnerLiving()==rig.scout){
                if(!seenShots.contains(shot.getUuid())){if(seenShots.size()<MAX_SHOT_IDS)seenShots.add(shot.getUuid());else droppedShotIds++;}
                if(included++<16){var entry=actor(shot);entry.add("owner",id(shot.getOwnerLiving()));shots.add(entry);}
            }
            row.add("actualProjectiles",shots);row.addProperty("projectilesBeyondFrameCap",Math.max(0,included-16));write(row,false);
        }
        void write(JsonObject row,boolean finalRow){
            if(!finalRow&&rows>=MAX_ROWS-1){droppedRows++;return;}
            row.addProperty("worldTick",rig.world.getTime());row.addProperty("fixtureTick",rig.ticks);row.addProperty("row",++rows);
            try{output.write(row.toString());output.newLine();output.flush();}catch(IOException e){throw new IllegalStateException("Shapes passive trace write failure",e);}
        }
        void close(){
            var row=new JsonObject();row.addProperty("event","CLOSE");row.addProperty("droppedRows",droppedRows);row.addProperty("droppedShotIds",droppedShotIds);row.addProperty("refusedRigs",refusedRigs);row.addProperty("seenShotIds",seenShots.size());
            var attempt=new JsonArray();var applied=new JsonArray();for(int i=0;i<3;i++){var a=new JsonArray();var b=new JsonArray();for(int j=0;j<3;j++){a.add(attempts[i][j]);b.add(after[i][j]);}attempt.add(a);applied.add(b);}
            row.addProperty("matrixLegend","shooter rows wingman/scout/other; recipient columns victim/wingman/scout");row.add("attemptCounts",attempt);row.add("afterDamageCounts",applied);
            write(row,true);try{output.close();}catch(IOException e){throw new IllegalStateException(e);}
        }
    }
}

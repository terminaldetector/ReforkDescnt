package com.terminaldetector.drmd;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.physics.ragdoll.RagdollDebrisGroups;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Opt-in real elapsed-time gate. A rehearsal can never produce TWO_HOUR_SOAK_PASS. */
public final class RagdollSoakGameTests implements FabricGameTest {
    static final long TWO_HOURS_NANOS=Duration.ofHours(2).toNanos(),WAVE_NANOS=Duration.ofMinutes(1).toNanos();
    static final int MIN_WAVES=12,MAX_WAVES=128;
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ragdoll-city-dungeon-soak",tickLimit=4_000_000)
    public void actualCityDungeonExplosionsAndLifecycleContinueForTwoElapsedHours(TestContext c){
        String mode=System.getProperty("drmd.lifecycle.mode","");
        if(mode.equals("cold")){RagdollColdVerification.start(c);return;}
        c.assertTrue(mode.equals("soak")||mode.equals("rehearsal"),"explicit soak/rehearsal/cold fixture mode required");
        String source=System.getProperty("drmd.lifecycle.source","");c.assertTrue(source.matches("[0-9a-f]{40}"),"exact tested source40 required");
        var state=RagdollSoakState.get(c.getWorld());state.begin(source,mode);long started=System.nanoTime(),worldStarted=c.getWorld().getTime();
        int[] waves={0},peakRoofs={0},peakRagdolls={0};double[] peakAverageMSPT={0};Wave[] active={null};boolean[] closed={false};long[] heartbeat={0};
        write("actual-java.json",Map.of("pid",ProcessHandle.current().pid(),"executable",ProcessHandle.current().info().command().orElse(""),"jvmArgs",ManagementFactory.getRuntimeMXBean().getInputArguments(),"classPath",System.getProperty("java.class.path"),"source",source,"mode",mode,"utc",Instant.now().toString(),"system",System.getProperties().entrySet().stream().filter(e->e.getKey().toString().startsWith("fabric.")).collect(java.util.stream.Collectors.toMap(e->e.getKey().toString(),e->e.getValue().toString()))));
        c.runAtEveryTick(()->{if(closed[0])return;try{
            long elapsed=System.nanoTime()-started;c.getWorld().resetIdleTimeout();
            if(active[0]==null){c.assertTrue(waves[0]<MAX_WAVES,"bounded128zone source cap, no automatic raise");active[0]=new Wave(c,waves[0]);}
            boolean cleaned=active[0].sample();
            if(c.getWorld().getTime()%20==0){
                int roofs=0,ragdolls=0;for(var e:c.getWorld().iterateEntities()){if(e instanceof BlockBodyEntity b&&b.physicsGravity())roofs++;if(e instanceof RagdollEntity)ragdolls++;}
                peakRoofs[0]=Math.max(peakRoofs[0],roofs);peakRagdolls[0]=Math.max(peakRagdolls[0],ragdolls);peakAverageMSPT[0]=Math.max(peakAverageMSPT[0],c.getWorld().getServer().getAverageNanosPerTick()/1e6);
                c.assertTrue(ragdolls<=128,"original live128cohort allowance");c.assertTrue(roofs<=64,"live permanent-roof allowance64; preserve pressure as NONPASS");
            }
            if(elapsed-heartbeat[0]>=Duration.ofMinutes(1).toNanos()){heartbeat[0]=elapsed;System.out.println("RAGDOLL_SOAK_HEARTBEAT elapsedMs="+elapsed/1_000_000+" waves="+waves[0]+" worldTicks="+(c.getWorld().getTime()-worldStarted)+" peakRagdolls="+peakRagdolls[0]+" peakLoadedPermanentRoofs="+peakRoofs[0]+" peakRollingAverageMSPT="+peakAverageMSPT[0]);}
            if(!cleaned)return;
            boolean enough=mode.equals("rehearsal")||elapsed>=TWO_HOURS_NANOS&&waves[0]+1>=MIN_WAVES;
            if(!enough&&elapsed<(waves[0]+1)*WAVE_NANOS)return; // retained real roof keeps its normal ticks; no fake waits/ticks
            if(enough&&!active[0].prepareColdPayload())return;
            state.row(active[0].receipt());active[0].retain();active[0]=null;waves[0]++;
            state.data.putLong("ElapsedNanos",System.nanoTime()-started);state.data.putLong("WorldTicks",c.getWorld().getTime()-worldStarted);state.data.putInt("Waves",waves[0]);
            state.data.putInt("PeakRagdolls",peakRagdolls[0]);state.data.putInt("PeakLoadedPermanentRoofs",peakRoofs[0]);state.data.putDouble("PeakRollingAverageMSPT",peakAverageMSPT[0]);state.markDirty();
            c.assertTrue(c.getWorld().getServer().saveAll(false,true,true),"actual world/entity/PersistentState save completed");
            if(!enough)return;
            c.assertTrue(mode.equals("rehearsal")||state.data.getLong("ElapsedNanos")>=TWO_HOURS_NANOS&&state.data.getLong("WorldTicks")>=MIN_WAVES*1200,"two actual hours and at least12 real1200tick lifecycles");
            String status=mode.equals("soak")?"TWO_HOUR_SOAK_PREPARE_PASS":"REHEARSAL_ONLY_PREPARE_PASS";state.data.putString("Status",status);state.markDirty();c.getWorld().getServer().saveAll(false,true,true);
            write("prepare-result.json",Map.ofEntries(Map.entry("status",status),Map.entry("source",source),Map.entry("run",state.data.getUuid("Run").toString()),Map.entry("elapsedNanos",state.data.getLong("ElapsedNanos")),Map.entry("worldTicks",state.data.getLong("WorldTicks")),Map.entry("waves",waves[0]),Map.entry("peakRagdolls",peakRagdolls[0]),Map.entry("peakLoadedPermanentRoofs",peakRoofs[0]),Map.entry("peakRollingAverageMSPT",peakAverageMSPT[0]),Map.entry("coldAcceptance","OPEN until separate process verifies saved content"),Map.entry("fps","null: TestServer has no client")));closed[0]=true;c.complete();
        }catch(RuntimeException|Error failure){closed[0]=true;if(active[0]!=null)active[0].retain();state.data.putString("Status","FIRST_FAILURE");state.data.putString("Failure",failure.toString());state.markDirty();write("first-failure.json",Map.of("failure",failure.toString(),"elapsedNanos",System.nanoTime()-started,"completedWaves",waves[0]));c.getWorld().getServer().saveAll(false,true,true);throw failure;}});
    }
    static void write(String name,Object value){try{var dir=Path.of("lifecycle-proof");Files.createDirectories(dir);String text=new GsonBuilder().setPrettyPrinting().create().toJson(value);if(text.length()>1024*1024)throw new IllegalStateException("proof1MiB cap");Files.writeString(dir.resolve(name),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}catch(java.io.IOException failure){throw new IllegalStateException("proof I/O",failure);}}
    private static final class Wave {
        final TestContext c;final int ordinal;final BlastStructuralCollapseGameTests.Fixture blast;final RagdollLifecycleFixture cohort;
        final List<DroneEntity> drones=new ArrayList<>();final List<RagdollEntity> bodies=new ArrayList<>();final Map<String,Integer> drops=new TreeMap<>();
        boolean spawned,killed,blastDone,cleaned;long died;UUID group;long full,sleep;int coldStage,coldAge;long coldAt;
        final List<DroneEntity> coldDrones=new ArrayList<>();final List<RagdollEntity> coldBodies=new ArrayList<>();TntEntity coldTnt;
        Wave(TestContext c,int ordinal){this.c=c;this.ordinal=ordinal;blast=new BlastStructuralCollapseGameTests.Fixture(c,ordinal*256,ordinal%2==0?BlastStructuralCollapseGameTests.Mode.TNT:BlastStructuralCollapseGameTests.Mode.CHEST);
            cohort=new RagdollLifecycleFixture(c,blast.base.add(40,-20,0),45,21);
            for(int i=0;i<128;i++){DroneEntity d=(i%2==0?ModEntities.DRONE:ModEntities.JET).create(c.getWorld());c.assertTrue(d!=null,"original species factory");d.setPosition(Vec3d.ofBottomCenter(cohort.floor.add(i%16*3,3,i/16*3)));d.setAiDisabled(true);d.setHealth(1);drones.add(d);cohort.own(d);}}
        boolean sample(){
            if(!spawned){if(!blast.ready()||!cohort.chunks.entityTicking(drones.stream().map(Entity::getBlockPos).toArray(BlockPos[]::new)))return false;blast.start();for(var d:drones)c.assertTrue(c.getWorld().spawnEntity(d),"single ordinary128admission");spawned=true;return false;}
            if(!blastDone)blastDone=blast.sample();
            if(!killed){if(drones.stream().anyMatch(d->d.age<2))return false;died=c.getWorld().getTime();var damage=c.getWorld().getDamageSources().genericKill();for(var d:drones)c.assertTrue(d.damage(damage,Float.MAX_VALUE),"actual128deaths in one tick");
                bodies.addAll(c.getWorld().getEntitiesByClass(RagdollEntity.class,cohort.area,Entity::isAlive));bodies.forEach(cohort::own);c.assertTrue(bodies.size()==128&&bodies.stream().map(Entity::getUuid).distinct().count()==128&&bodies.stream().mapToInt(b->b.simulation().parts().size()).sum()==512,"128unique physical corpses retain512 actual parts");group=bodies.getFirst().debrisGroupId();c.assertTrue(group!=null&&bodies.stream().allMatch(b->group.equals(b.debrisGroupId())),"one causal group, not merged mass");drops.putAll(dropCounts());killed=true;return false;}
            if(!cleaned){if(bodies.stream().anyMatch(b->!b.isRemoved()||c.getWorld().getEntity(b.getUuid())!=null))return false;
                c.assertTrue(c.getWorld().getTime()-died>=1200&&RagdollDebrisGroups.get(c.getWorld()).snapshot(group).isEmpty(),"actualTTL and logical cleanup");
                for(var b:bodies)c.assertTrue(b.simulatedPhysicsTicks()+b.skippedPhysicsTicks()==1200,"original1200 successful full/sleep ticks");c.assertTrue(drops.equals(dropCounts()),"no material/drop erase during corpse cleanup");
                full=bodies.stream().mapToLong(RagdollEntity::simulatedPhysicsTicks).sum();sleep=bodies.stream().mapToLong(RagdollEntity::skippedPhysicsTicks).sum();cleaned=true;}
            return cleaned&&blastDone;
        }
        Map<String,Integer> dropCounts(){var counts=new TreeMap<String,Integer>();for(var item:c.getWorld().getEntitiesByClass(ItemEntity.class,cohort.area,e->true))counts.merge(net.minecraft.registry.Registries.ITEM.getId(item.getStack().getItem()).toString(),item.getStack().getCount(),Integer::sum);return counts;}
        boolean prepareColdPayload(){
            var w=c.getWorld();if(coldStage==0){
                for(int i=0;i<2;i++){DroneEntity d=(i==0?ModEntities.DRONE:ModEntities.JET).create(w);c.assertTrue(d!=null,"cold explosion victim factory");d.setPosition(Vec3d.ofBottomCenter(cohort.floor.add(i*4,2,0)));d.setAiDisabled(true);var tag=new NbtCompound();d.writeNbt(tag);tag.putFloat("shield",0);tag.putFloat("armor",0);tag.putFloat("Health",1);d.readNbt(tag);c.assertTrue(w.spawnEntity(d),"actual registered cold victims");coldDrones.add(d);}coldStage=1;return false;}
            if(coldStage==1){if(coldDrones.stream().anyMatch(d->d.age<2))return false;coldTnt=new TntEntity(w,cohort.floor.getX()+2.5,cohort.floor.getY()+2.5,cohort.floor.getZ()+.5,null);coldTnt.setVelocity(Vec3d.ZERO);coldTnt.setFuse(1);c.assertTrue(w.spawnEntity(coldTnt),"real TNT causes final pre-save deaths");coldStage=2;return false;}
            if(coldStage==2){if(!coldTnt.isRemoved()||coldTnt.age<1)return false;c.assertTrue(coldDrones.stream().noneMatch(Entity::isAlive),"actual explosion killed both unshielded victims");coldBodies.addAll(w.getEntitiesByClass(RagdollEntity.class,cohort.area,Entity::isAlive));c.assertTrue(coldBodies.size()==2,"two real explosion corpses retained for cold restart");coldAge=coldBodies.getFirst().age;coldAt=w.getTime();coldStage=3;return false;}
            if(coldBodies.stream().anyMatch(b->b.age<coldAge+8)||w.getTime()-coldAt<8)return false;
            return true;
        }
        NbtCompound receipt(){var tag=new NbtCompound();tag.putInt("Ordinal",ordinal);tag.putString("Scene",blast.mode.name());tag.putLong("Base",blast.base.asLong());tag.putLong("CohortFloor",cohort.floor.asLong());tag.putLong("FullTicks",full);tag.putLong("SleepTicks",sleep);tag.putLong("Deaths",128);tag.putLong("SavedAt",c.getWorld().getTime());
            if(blast.body!=null){tag.putUuid("Roof",blast.body.getUuid());tag.putLong("RoofPosition",blast.body.getBlockPos().asLong());tag.put("RoofMaterial",RagdollSoakState.material(blast.body));}
            if(blast.chestPos!=null){tag.putLong("Chest",blast.chestPos.asLong());tag.put("ChestData",blast.chestBefore.copy());}
            var staticCells=new NbtList();for(var p:java.util.stream.Stream.concat(blast.obsidian.stream(),blast.supports.stream()).distinct().toList()){
                var cell=new NbtCompound();cell.putLong("Pos",p.asLong());cell.put("State",NbtHelper.fromBlockState(c.getWorld().getBlockState(p)));staticCells.add(cell);
            }tag.put("StaticCells",staticCells);
            var ids=new NbtList();for(var b:coldBodies){var value=new NbtCompound();value.putUuid("Id",b.getUuid());value.putLong("Position",b.getBlockPos().asLong());value.putInt("Life",b.physicalLifeTicks());value.putDouble("Mass",b.physicsMass());value.putInt("Parts",b.simulation().parts().size());value.putUuid("Group",b.debrisGroupId());ids.add(value);}tag.put("ColdBodies",ids);return tag;}
        void retain(){blast.retainForColdSave();cohort.retain=true;cohort.close();}
    }
}

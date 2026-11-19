package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.physics.ragdoll.RagdollDebrisGroups;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.*;

/** Reuses the original world. Never calls an entity factory/readNbt to manufacture a reload. */
final class RagdollColdVerification {
    static void start(TestContext c){
        c.assertTrue(RagdollColdProbe.initialized&&RagdollColdProbe.error==null,"actual cold initializer/input admitted: "+RagdollColdProbe.error);
        RagdollSoakGameTests.write("actual-java.json",Map.of("pid",ProcessHandle.current().pid(),"executable",ProcessHandle.current().info().command().orElse(""),"mode","cold","source",System.getProperty("drmd.lifecycle.source"),"classPath",System.getProperty("java.class.path")));
        var w=c.getWorld();var state=RagdollSoakState.get(w);var data=state.data;
        c.assertTrue(data.getInt("Schema")==1&&data.getString("Source").equals(System.getProperty("drmd.lifecycle.source")),"same durable source/run receipt restored from original save");
        c.assertTrue(data.getString("Status").endsWith("PREPARE_PASS"),"earlier real process completed prepare");
        var input=new ArrayList<>(RagdollColdProbe.expected.entrySet());var permanent=new LinkedHashMap<UUID,NbtList>();var expectedBodies=new HashMap<UUID,NbtCompound>();
        var rows=data.getList("Rows",NbtElement.COMPOUND_TYPE);for(int i=0;i<rows.size();i++){var row=rows.getCompound(i);if(row.containsUuid("Roof"))permanent.put(row.getUuid("Roof"),row.getList("RoofMaterial",NbtElement.COMPOUND_TYPE));for(var value:row.getList("ColdBodies",NbtElement.COMPOUND_TYPE)){var body=(NbtCompound)value;expectedBodies.put(body.getUuid("Id"),body);}}
        int[] staticNext={0},next={0},admission={0},bodyAge={0},restoredCorpses={0},restoredRoofs={0},restoredItems={0};long[] pauseUntil={0},started={w.getTime()};
        AiFixtureChunks[] current={null};Entity[] entity={null};boolean[] done={false};var corpses=new ArrayList<RagdollEntity>();var held=new ArrayList<AiFixtureChunks>();var corpseSavedLife=new HashMap<UUID,Integer>();
        c.runAtEveryTick(()->{if(done[0])return;try{
            w.resetIdleTimeout();c.assertTrue(RagdollColdProbe.error==null,"bounded passive load recorder: "+RagdollColdProbe.error);
            c.assertTrue(w.getTime()-started[0]<12000,"finite ordinary cold-load/cleanup deadline");
            if(w.getTime()<pauseUntil[0])return;
            if(next[0]<input.size()){
                var entry=input.get(next[0]);var saved=entry.getValue();var position=saved.getList("Pos",NbtElement.DOUBLE_TYPE);var at=BlockPos.ofFloored(position.getDouble(0),position.getDouble(1),position.getDouble(2));
                if(current[0]==null){current[0]=new AiFixtureChunks(w,at,0);admission[0]=0;entity[0]=null;}
                if(entity[0]==null){
                    c.assertTrue(++admission[0]<=100,"actual saved UUID becomes entity-ticking without respawn: "+entry.getKey());
                    var passive=RagdollColdProbe.loaded.get(entry.getKey());
                    if(saved.getString("id").equals("minecraft:item")&&passive!=null){
                        c.assertTrue(saved.getCompound("Item").equals(passive.getCompound("Item")),"actual saved drop content decoded before legitimate Vanilla merge/expiry");
                        restoredItems[0]++;current[0].close();current[0]=null;next[0]++;return;
                    }
                    var live=w.getEntity(entry.getKey());if(live==null||!current[0].entityTicking(live.getBlockPos()))return;entity[0]=live;bodyAge[0]=live.age;
                    var loaded=RagdollColdProbe.loaded.get(entry.getKey());c.assertTrue(loaded!=null,"passive actual ENTITY_LOAD preceded all manual observation");
                    c.assertTrue(loaded.getUuid("UUID").equals(entry.getKey()),"original cold identity");
                    if(live instanceof RagdollEntity body){
                        var expected=expectedBodies.get(body.getUuid());c.assertTrue(expected!=null&&expected.getInt("Parts")==body.simulation().parts().size()&&Math.abs(expected.getDouble("Mass")-body.physicsMass())<1e-10,"saved corpse retains actual material/part mass and authored identity");
                        c.assertTrue(saved.getString("flightHull").equals(loaded.getString("flightHull"))&&saved.getInt("rigVersion")==loaded.getInt("rigVersion")&&saved.getFloat("hullScale")==loaded.getFloat("hullScale"),"saved authored rig identity/scale/version");
                        c.assertTrue(saved.getInt("life")==loaded.getInt("life")&&saved.getUuid("debrisGroup").equals(loaded.getUuid("debrisGroup")),"physical lifetime and causal owner survive real cold decode");
                        var before=saved.getCompound("ragdoll").getList("parts",NbtElement.COMPOUND_TYPE);var after=loaded.getCompound("ragdoll").getList("parts",NbtElement.COMPOUND_TYPE);c.assertTrue(before.size()==after.size(),"every saved physical part restored");
                        for(int p=0;p<before.size();p++)vectors(c,before.getCompound(p),after.getCompound(p),List.of("p","v","l"));
                        corpses.add(body);corpseSavedLife.put(body.getUuid(),saved.getInt("life"));held.add(current[0]);restoredCorpses[0]++;
                    }else if(live instanceof BlockBodyEntity body){
                        c.assertTrue(saved.getCompound("shape").equals(loaded.getCompound("shape")),"actual saved sparse material restored without world/static duplicate");
                        vectors(c,saved.getCompound("body"),loaded.getCompound("body"),List.of("origin","v","l"));
                        c.assertTrue(permanent.containsKey(body.getUuid())&&permanent.get(body.getUuid()).equals(RagdollSoakState.material(body)),"same permanent roof retains every cell/state exactly");restoredRoofs[0]++;
                    }else if(live instanceof ItemEntity){c.assertTrue(saved.getCompound("Item").equals(loaded.getCompound("Item")),"real saved drop stack content restored without reconstruction");restoredItems[0]++;}
                    return;
                }
                if(entity[0].age-bodyAge[0]<8)return;
                c.assertTrue(w.getEntity(entry.getKey())==entity[0],"same restored live actor received ordinary ticks");
                if(current[0]!=null){if(!(entity[0] instanceof RagdollEntity))current[0].close();current[0]=null;}entity[0]=null;next[0]++;
                if(next[0]%16==0)pauseUntil[0]=w.getTime()+400; // allow released ordinary chunk tickets to retire; no forced unload/hand ticks
                return;
            }
            c.assertTrue(restoredCorpses[0]==2&&restoredRoofs[0]==permanent.size(),"all retained roofs and both actual TNT corpses cold-loaded");
            if(staticNext[0]<rows.size()){
                var row=rows.getCompound(staticNext[0]);var base=BlockPos.fromLong(row.getLong("Base"));
                if(current[0]==null){current[0]=new AiFixtureChunks(w,base,0);admission[0]=0;}
                c.assertTrue(++admission[0]<=100,"bounded actual saved static scene admission");if(!current[0].entityTicking(base))return;
                var cells=row.getList("StaticCells",NbtElement.COMPOUND_TYPE);for(int j=0;j<cells.size();j++){var cell=cells.getCompound(j);c.assertTrue(cell.getCompound("State").equals(NbtHelper.fromBlockState(w.getBlockState(BlockPos.fromLong(cell.getLong("Pos"))))),"post-blast static ownership survived real save/restart");}
                if(row.contains("Chest",NbtElement.LONG_TYPE)){var pos=BlockPos.fromLong(row.getLong("Chest"));c.assertTrue(w.getBlockEntity(pos) instanceof ChestBlockEntity chest&&row.getCompound("ChestData").equals(chest.createNbt(w.getRegistryManager())),"actual dungeon chest inventory/name/position preserved");}
                current[0].close();current[0]=null;staticNext[0]++;if(staticNext[0]%16==0)pauseUntil[0]=w.getTime()+400;return;
            }
            if(corpses.stream().anyMatch(b->!b.isRemoved()||w.getEntity(b.getUuid())!=null))return;
            for(var b:corpses)c.assertTrue(b.physicalLifeTicks()==1201&&b.simulatedPhysicsTicks()+b.skippedPhysicsTicks()==1200-corpseSavedLife.get(b.getUuid())&&RagdollDebrisGroups.get(w).snapshot(b.debrisGroupId()).isEmpty(),"live post-reload TTL cleanup neither duplicates nor resets its saved lifetime");
            held.forEach(AiFixtureChunks::close);done[0]=true;data.putString("Status",data.getString("Mode").equals("soak")?"TWO_HOUR_SOAK_AND_COLD_PASS":"SHORT_COLD_PROOF_PASS");state.markDirty();w.getServer().saveAll(false,true,true);
            RagdollSoakGameTests.write("cold-result.json",Map.of("status",data.getString("Status"),"run",data.getUuid("Run").toString(),"source",data.getString("Source"),"restoredCorpses",restoredCorpses[0],"restoredRoofs",restoredRoofs[0],"restoredDrops",restoredItems[0],"worldTicks",w.getTime()-started[0],"actualElapsedPrepareNanos",data.getLong("ElapsedNanos")));c.complete();
        }catch(RuntimeException|Error failure){done[0]=true;if(current[0]!=null)current[0].close();held.forEach(AiFixtureChunks::close);RagdollSoakGameTests.write("cold-first-failure.json",Map.of("failure",failure.toString(),"loaded",next[0]));throw failure;}});
    }
    private static void vectors(TestContext c,NbtCompound before,NbtCompound after,List<String> keys){
        for(String key:keys)for(String axis:List.of("x","y","z"))c.assertTrue(Math.abs(before.getDouble(key+axis)-after.getDouble(key+axis))<1e-9,"saved physical "+key+axis+" came from actual cold decoder");
        double dot=before.getDouble("qx")*after.getDouble("qx")+before.getDouble("qy")*after.getDouble("qy")+before.getDouble("qz")*after.getDouble("qz")+before.getDouble("qw")*after.getDouble("qw");c.assertTrue(Math.abs(dot)>1-1e-10,"saved physical quaternion unchanged up to normalization/sign");
    }
}

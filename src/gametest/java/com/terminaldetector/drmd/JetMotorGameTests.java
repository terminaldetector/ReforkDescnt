package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Real autonomous goal and vanilla collision/movement ticks, not a manually ticked motor. */
public final class JetMotorGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="jet-accelerate",tickLimit=180)
    public void jetAcceleratesFromRestInsteadOfJumpingToCruiseSpeed(TestContext c){check(c,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="jet-reverse",tickLimit=180)
    public void jetCannotInstantlyReverseItsFlightVelocity(TestContext c){check(c,true);}
    private static void check(TestContext c,boolean reversing) {
        var w=c.getWorld();var base=c.getAbsolutePos(new BlockPos(7000,130,1000));var start=Vec3d.ofBottomCenter(base);var chunks=new HashSet<ChunkPos>();
        for(int x=(base.getX()>>4)-2;x<=(base.getX()>>4)+2;x++)for(int z=(base.getZ()>>4)-2;z<=(base.getZ()>>4)+2;z++) {
            var ch=new ChunkPos(x,z);if(!w.getForcedChunks().contains(ch.toLong())){w.setChunkForced(x,z,true);chunks.add(ch);}
        }
        var jet=ModEntities.JET.create(w);var target=EntityType.COW.create(w);
        jet.setPosition(start);jet.setPersistent();jet.setInvulnerable(true);jet.setAiDisabled(true);
        target.setPosition(start.add(reversing?-24:24,0,0));target.setNoGravity(true);target.setAiDisabled(true);target.setInvulnerable(true);
        c.assertTrue(w.spawnEntity(jet)&&w.spawnEntity(target),"real JET motor actors spawned");jet.setTarget(target);DroneSwarmAi.clear();
        int[] ticks={0},admission={0};boolean[] started={false},finished={false};Vec3d[] previous={Vec3d.ZERO};double[] first={0},peak={0},step={0};
        c.runAtEveryTick(()->{
            if(finished[0])return;
            if(!started[0]){c.assertTrue(++admission[0]<80,"JET fixture admitted");if(jet.age<5||target.age<5)return;DroneSwarmAi.clear();jet.setPhase(DroneAi.Phase.ORBIT);previous[0]=reversing?new Vec3d(.5,0,0):Vec3d.ZERO;jet.setVelocity(previous[0]);jet.setAiDisabled(false);started[0]=true;return;}
            var v=jet.getVelocity();double change=v.subtract(previous[0]).length();step[0]=Math.max(step[0],change);previous[0]=v;peak[0]=Math.max(peak[0],v.length());if(++ticks[0]==1)first[0]=v.length();
            boolean violation=reversing?change>.12:first[0]>.075;
            if(!violation&&ticks[0]<40)return;finished[0]=true;double distance=jet.getPos().distanceTo(start);boolean alive=jet.isAlive(),locked=jet.getTarget()==target;
            for(var e:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(e instanceof ProjectileEntity p&&p.getOwnerLiving()==jet)e.discard();
            jet.discard();target.discard();for(var ch:chunks)w.setChunkForced(ch.x,ch.z,false);DroneSwarmAi.clear();
            c.assertTrue(!violation&&alive&&locked&&peak[0]>=.35&&distance>=8,"actual JET acceleration/velocity continuity: reversing="+reversing+" first="+first[0]+" maxStep="+step[0]+" peak="+peak[0]+" distance="+distance+" ticks="+ticks[0]);c.complete();
        });
    }
}

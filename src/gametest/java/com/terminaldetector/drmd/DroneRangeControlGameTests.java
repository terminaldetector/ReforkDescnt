package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Real goal motion in free space; a single target assignment isolates role steering from detection. */
public final class DroneRangeControlGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-range-flank",tickLimit=160)
    public void interceptorClosesLongRangeWhileFlanking(TestContext c){check(c,AiRole.INTERCEPTOR,24,0,22);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-range-hold",tickLimit=160)
    public void artilleryApproachesItsStandoffRingFromOutside(TestContext c){check(c,AiRole.ARTILLERY,24,0,22);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-range-pressure",tickLimit=160)
    public void pressureDroneClosesLongRangeDuringOrbit(TestContext c){check(c,AiRole.MG,24,0,22);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-range-flank-near",tickLimit=160)
    public void interceptorOpensDistanceInsideItsRing(TestContext c){check(c,AiRole.INTERCEPTOR,3.5,4.25,1000);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-range-hold-near",tickLimit=160)
    public void artilleryBacksOffBeforeTheEmergencyRadius(TestContext c){check(c,AiRole.ARTILLERY,9,10.5,1000);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-range-pressure-near",tickLimit=160)
    public void pressureDroneOpensDistanceInsideItsRing(TestContext c){check(c,AiRole.MG,6,6.75,1000);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-range-support-retreat",tickLimit=160)
    public void unaccompaniedSupportRetreatsInsteadOfCancellingItsOwnThrust(TestContext c){check(c,AiRole.SUPPORT,6,10,1000);}
    private static void check(TestContext c,AiRole role,double initial,double min,double max) {
        var w=c.getWorld();var base=c.getAbsolutePos(new BlockPos(5000,120,1000));var start=Vec3d.ofBottomCenter(base);
        var chunks=new AiFixtureChunks(w,base,2);
        var drone=ModEntities.DRONE.create(w);var target=EntityType.COW.create(w);
        drone.applyRole(role);drone.setPosition(start);drone.setPersistent();drone.setInvulnerable(true);drone.setAiDisabled(true);
        target.setPosition(start.add(initial,0,0));target.setNoGravity(true);target.setAiDisabled(true);target.setInvulnerable(true);
        DroneSwarmAi.clear();
        int[] ticks={0},admission={0};boolean[] started={false},finished={false},spawned={false};
        c.runAtEveryTick(()->{
            if(finished[0])return;
            if(!started[0]){
                if(++admission[0]>=80){finished[0]=true;boolean ready=chunks.entityTicking(drone.getBlockPos(),target.getBlockPos());String detail=chunks.readiness(drone.getBlockPos(),target.getBlockPos());int da=drone.age,ta=target.age;drone.discard();target.discard();chunks.close();c.assertTrue(false,"range fixture admission: spawned="+spawned[0]+" ticking="+ready+" droneAge="+da+" targetAge="+ta+" "+detail);return;}
                if(!spawned[0]){if(!chunks.entityTicking(drone.getBlockPos(),target.getBlockPos()))return;boolean ok=w.spawnEntity(drone)&&w.spawnEntity(target);if(!ok){finished[0]=true;drone.discard();target.discard();chunks.close();c.assertTrue(false,"real range-control actors spawned");return;}drone.setTarget(target);spawned[0]=true;return;}
                if(drone.age<5||target.age<5)return;DroneSwarmAi.clear();drone.setPhase(DroneAi.Phase.ORBIT);drone.setAiDisabled(false);started[0]=true;
            }
            if(++ticks[0]<40)return;finished[0]=true;
            double distance=drone.getPos().distanceTo(target.getPos());boolean alive=drone.isAlive(),locked=drone.getTarget()==target;var phase=drone.getPhase();
            for(var e:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(e instanceof com.terminaldetector.drmd.entity.ProjectileEntity p&&p.getOwnerLiving()==drone)e.discard();
            drone.discard();target.discard();chunks.close();DroneSwarmAi.clear();
            c.assertTrue(alive&&locked&&phase==DroneAi.Phase.ORBIT&&distance>=min&&distance<=max,"actual role range control in40 ticks before RUN: role="+role+" initial="+initial+" expected="+min+".."+max+" distance="+distance+" phase="+phase+" locked="+locked);c.complete();
        });
    }
}

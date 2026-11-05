package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Real target selectors; no setTarget calls or manual entity ticks. */
public final class DroneTargetVolumeGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-target-up",tickLimit=140)
    public void acquiresTargetTwentyBlocksAbove(TestContext c){acquire(c,20);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-target-down",tickLimit=140)
    public void acquiresTargetTwentyBlocksBelow(TestContext c){acquire(c,-20);}
    private static void acquire(TestContext c,int dy) {
        var w=c.getWorld();var at=Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(7,36,7)));
        var drone=ModEntities.DRONE.create(w);var cow=EntityType.COW.create(w);
        drone.applyRole(AiRole.HEAVY);drone.setPosition(at);drone.setPersistent();
        cow.setPosition(at.add(0,dy,0));cow.setNoGravity(true);cow.setAiDisabled(true);cow.setPersistent();
        cow.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,-1,4,false,false));
        c.assertTrue(w.spawnEntity(cow)&&w.spawnEntity(drone),"registered target and active drone spawned");
        c.runAtTick(100,()->{
            boolean acquired=drone.getTarget()==cow;
            var actual=drone.getTarget();
            drone.discard();cow.discard();
            c.assertTrue(acquired,"natural vertical target: dy="+dy+" actual="+actual);
            c.complete();
        });
    }
}

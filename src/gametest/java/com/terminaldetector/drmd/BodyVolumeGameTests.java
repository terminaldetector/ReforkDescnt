package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class BodyVolumeGameTests implements FabricGameTest {
    private BlockBodyEntity body(TestContext c,int x,int z) {
        var w=c.getWorld();w.getChunk(x>>4,z>>4);
        return BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()),
            new BlockBodyEntity.Cell(new BlockPos(127,0,0),Blocks.IRON_BLOCK.getDefaultState())),new Vec3d(x,150,z));
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-lifetime",tickLimit=200)
    public void playerAtFarEndHoldsTheWholeInteriorAndRemovalReleasesAllTickets(TestContext c) {
        var b=body(c,4096,4096);var p=c.createMockCreativeServerPlayerInWorld();p.setPosition(4222,152,4096);
        c.waitAndRun(6,()->{try {
            var plan=BodyVolumeLeases.heldPlan(c.getWorld(),b.getUuid());
            c.assertTrue(plan.stream().anyMatch(x->x.x()==256)&&plan.stream().anyMatch(x->x.x()==260)&&plan.stream().anyMatch(x->x.x()==263),"far rider holds pivot, empty interior and opposite end");
            b.discard();c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),b.getUuid())==0,"real entity removal releases all owned leases immediately");
        }finally{b.discard();p.discard();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-away",tickLimit=200)
    public void leavingTheHullDoesNotKeepAPlayerlessFleetLoadedForever(TestContext c) {
        var b=body(c,8192,8192);var p=c.createMockCreativeServerPlayerInWorld();p.setPosition(8318,152,8192);
        c.waitAndRun(6,()->{
            c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),b.getUuid())>0,"active rider creates volume leases");p.setPosition(0,150,0);
            c.waitAndRun(3,()->{try {c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),b.getUuid())==0,"absent active players release the full volume");}
                finally{b.discard();p.discard();}c.complete();});
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-volume-move",tickLimit=200)
    public void movingVolumeDropsOldColumnsAndRetainsTheNewHullInsteadOfItsOldPivot(TestContext c) {
        var b=body(c,12288,12288);var p=c.createMockCreativeServerPlayerInWorld();p.setPosition(12414,152,12288);
        c.waitAndRun(6,()->{
            c.assertTrue(BodyVolumeLeases.heldColumns(c.getWorld(),b.getUuid())>0,"initial volume is leased");
            b.setPosition(b.getPos().add(512,0,0));p.setPosition(p.getPos().add(512,0,0));
            c.waitAndRun(6,()->{try {
                var plan=BodyVolumeLeases.heldPlan(c.getWorld(),b.getUuid());
                c.assertTrue(plan.stream().anyMatch(x->x.x()==800)&&plan.stream().noneMatch(x->x.x()<790),"new complete volume replaces old column tickets");
            }finally{b.discard();p.discard();}c.complete();});
        });
    }
}

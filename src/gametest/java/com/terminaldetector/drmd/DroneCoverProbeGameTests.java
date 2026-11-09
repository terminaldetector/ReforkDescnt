package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.DroneAi;
import com.terminaldetector.drmd.ai.DroneSwarmAi;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Loaded-only critical-health cover queries, then one actual ordinary-goal flee observation. */
public final class DroneCoverProbeGameTests implements FabricGameTest {
    private static Vec3d cover(DroneEntity drone,Vec3d from,Vec3d away)throws Exception{
        var method=DroneAi.CombatGoal.class.getDeclaredMethod("nearestCoverDirection",Vec3d.class,Vec3d.class);
        method.setAccessible(true);
        return (Vec3d)method.invoke(new DroneAi.CombatGoal(drone),from,away);
    }
    private static Vec3d origin(TestContext c,int offset){
        var at=c.getAbsolutePos(new BlockPos(34000+offset,150,1000));
        // Actual FULL columns, including every collision-shape neighbour in the 25-block rays.
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)c.getWorld().getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        return Vec3d.ofCenter(at);
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-flee-unloaded")
    public void coverProbeCannotMaterializeAnUnknownColumn(TestContext c)throws Exception{
        var w=c.getWorld();var at=new Vec3d(1240000.5,120.5,2350000.5);var pos=BlockPos.ofFloored(at);
        var drone=FlightEnemyKind.ORB.create(w);drone.setPosition(at);
        try{
            c.assertTrue(w.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)==null,"remote cover-query column begins unavailable");
            var result=cover(drone,at,new Vec3d(1,0,0));
            c.assertTrue(w.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)==null,"a read-only flee probe must not synchronously load/generate unknown terrain");
            c.assertTrue(result.distanceTo(new Vec3d(1,0,0))<1e-8,"unknown space is no cover, so ordinary away intent remains");
        }finally{drone.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-flee-hull")
    public void anActualRotatedHullCanProvideTheNearestEscapeCover(TestContext c)throws Exception{
        var w=c.getWorld();var at=origin(c,0);var expected=new Vec3d(1,0,.6).normalize();
        var q=ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.9);var shift=q.rotate(new Vec3(.5,.5,.5));var centre=at.add(expected.multiply(8));
        var hull=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),centre.add(-shift.x(),-shift.y(),-shift.z()),q,Vec3d.ZERO,Vec3d.ZERO);
        hull.setPhysicsGravity(false);var drone=FlightEnemyKind.ORB.create(w);drone.setPosition(at);
        try{
            c.assertTrue(hull.raycast(at,at.add(expected.multiply(25))).isPresent(),"fixture ray hits real rotated material rather than a proxy AABB");
            c.assertTrue(cover(drone,at,new Vec3d(1,0,0)).dotProduct(expected)>.9999,"critical-health escape can choose actual physical-hull cover");
        }finally{hull.discard();drone.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-flee-existing-cover")
    public void existingUpperTerrainCoverStillWinsOverEmptyAwaySpace(TestContext c)throws Exception{
        checkTerrain(c,200,new Vec3d(1,0,0),new Vec3d(1,.5,0).normalize(),"the existing horizontal upper-cover choice is preserved");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-flee-lower-cover")
    public void aFlyingDroneCanChooseCoverBelowItsRetreatLine(TestContext c)throws Exception{
        checkTerrain(c,400,new Vec3d(1,0,0),new Vec3d(1,-.5,0).normalize(),"airborne retreat can choose the lower half of its escape cone");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-flee-vertical-cover")
    public void verticalRetreatKeepsAUsefulPerpendicularCoverBasis(TestContext c)throws Exception{
        checkTerrain(c,600,new Vec3d(0,1,0),new Vec3d(.5,1,0).normalize(),"vertical flight must not collapse its cover probes into repeated world-UP");
    }
    private static void checkTerrain(TestContext c,int offset,Vec3d away,Vec3d expected,String why)throws Exception{
        var w=c.getWorld();var at=origin(c,offset);var cell=BlockPos.ofFloored(at.add(expected.multiply(8)));var before=w.getBlockState(cell);
        var drone=FlightEnemyKind.ORB.create(w);drone.setPosition(at);w.setBlockState(cell,Blocks.BEDROCK.getDefaultState(),3);
        try{c.assertTrue(cover(drone,at,away).dotProduct(expected)>.9999,why);c.assertTrue(w.getBlockState(cell).isOf(Blocks.BEDROCK),"cover discovery does not destroy its material");}
        finally{w.setBlockState(cell,before,3);drone.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-flee-live",tickLimit=150)
    public void theOrdinaryLowHealthGoalDescendsToCoverWithoutReturningFire(TestContext c){
        ServerWorld w=c.getWorld();var base=c.getAbsolutePos(new BlockPos(38000,150,1000));var lease=new AiFixtureChunks(w,base,2);
        var at=Vec3d.ofCenter(base);var drone=FlightEnemyKind.ORB.create(w);var target=EntityType.COW.create(w);
        drone.setPosition(at);drone.setHealth(20);drone.setPersistent();drone.setInvulnerable(true);drone.setAiDisabled(true);
        target.setPosition(at.add(-10,0,0));target.setNoGravity(true);target.setAiDisabled(true);target.setInvulnerable(true);
        var cover=BlockPos.ofFloored(at.add(new Vec3d(1,-.5,0).normalize().multiply(8)));var before=w.getBlockState(cover);w.setBlockState(cover,Blocks.BEDROCK.getDefaultState(),3);
        int[] admission={0},ticks={0};double[] lowest={at.y};boolean[] spawned={false},started={false},done={false},fired={false};
        Runnable cleanup=()->{for(var shot:w.getEntitiesByClass(ProjectileEntity.class,new Box(at,at).expand(32),p->p.getOwnerLiving()==drone))shot.discard();drone.discard();target.discard();w.setBlockState(cover,before,3);lease.close();DroneSwarmAi.clear();};
        c.runAtEveryTick(()->{
            if(done[0])return;w.resetIdleTimeout();
            if(!spawned[0]){
                if(++admission[0]>=80){done[0]=true;cleanup.run();c.assertTrue(false,"ordinary flee fixture obtains actual entity-ticking columns");return;}
                if(!lease.entityTicking(drone.getBlockPos(),target.getBlockPos()))return;
                c.assertTrue(w.spawnEntity(drone)&&w.spawnEntity(target),"real default ORB and threat are registered");drone.setTarget(target);spawned[0]=true;return;
            }
            if(!started[0]){if(drone.age<5||target.age<5)return;DroneSwarmAi.clear();drone.setAiDisabled(false);started[0]=true;}
            ticks[0]++;lowest[0]=Math.min(lowest[0],drone.getY());
            fired[0]|=!w.getEntitiesByClass(ProjectileEntity.class,new Box(at,at).expand(32),p->p.getOwnerLiving()==drone).isEmpty();
            if(ticks[0]<20)return;
            done[0]=true;boolean fled=drone.getX()>at.x+.4&&lowest[0]<at.y-.25;boolean targetHeld=drone.getTarget()==target;boolean intact=w.getBlockState(cover).isOf(Blocks.BEDROCK);var end=drone.getPos();cleanup.run();
            c.assertTrue(fled&&targetHeld&&intact&&!fired[0],"ordinary critical-health flight descends toward intact cover and does not fire: delta="+end.subtract(at)+" low="+(lowest[0]-at.y)+" target="+targetHeld+" fired="+fired[0]);c.complete();
        });
    }
}

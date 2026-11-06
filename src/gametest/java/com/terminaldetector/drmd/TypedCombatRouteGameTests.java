package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.DroneSwarmAi;
import com.terminaldetector.drmd.entity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Default combat roles must obtain a real firing lane through cover, without a test-supplied route. */
public final class TypedCombatRouteGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="typed-route-orb",tickLimit=500)
    public void orbFindsFiringLaneThroughOffsetCover(TestContext c){check(c,FlightEnemyKind.ORB);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="typed-route-rocket",tickLimit=500)
    public void rocketFindsFiringLaneThroughOffsetCover(TestContext c){check(c,FlightEnemyKind.ROCKET);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="typed-route-jet",tickLimit=500)
    public void jetFindsFiringLaneThroughOffsetCover(TestContext c){check(c,FlightEnemyKind.JET);}
    private static void check(TestContext c,FlightEnemyKind kind){
        var w=c.getWorld();var base=c.getAbsolutePos(new BlockPos(11000+kind.ordinal()*1000,100,1000));
        var start=Vec3d.ofBottomCenter(base).add(.27,.37,.19);var destination=start.add(18,0,0);var lease=new AiFixtureChunks(w,base,3);
        int width=(int)Math.ceil(kind.diameter)+1;var wall=new ArrayList<BlockPos>();
        for(int y=-20;y<=20;y++)for(int z=-20;z<=20;z++){
            if(y>=4&&y<4+width&&z>=0&&z<width)continue;
            var p=base.add(6,y,z);wall.add(p);w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),3);
        }
        var drone=kind.create(w);var target=EntityType.COW.create(w);
        Entity actor=drone;actor.setPosition(start);drone.setPersistent();drone.setInvulnerable(true);drone.setAiDisabled(true);
        target.setPosition(destination);target.setAiDisabled(true);target.setNoGravity(true);target.setInvulnerable(true);
        int[] admission={0},ticks={0};boolean[] spawned={false},started={false},finished={false},lane={false},shotThrough={false};
        Runnable cleanup=()->{
            for(var e:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(e instanceof ProjectileEntity p&&p.getOwnerLiving()==drone)e.discard();
            actor.discard();target.discard();for(var p:wall)w.setBlockState(p,Blocks.AIR.getDefaultState(),3);lease.close();DroneSwarmAi.clear();
        };
        c.runAtEveryTick(()->{
            if(finished[0])return;w.resetIdleTimeout();
            if(!started[0]){
                if(++admission[0]>=80){finished[0]=true;String detail=lease.readiness(actor.getBlockPos(),target.getBlockPos());cleanup.run();c.assertTrue(false,"typed firing-lane fixture admission "+kind+" "+detail);return;}
                if(!spawned[0]){if(!lease.entityTicking(actor.getBlockPos(),target.getBlockPos()))return;boolean ok=w.spawnEntity(drone)&&w.spawnEntity(target);if(!ok){finished[0]=true;cleanup.run();c.assertTrue(false,"typed combat actors spawned");return;}drone.setTarget(target);spawned[0]=true;return;}
                if(actor.age<5||target.age<5)return;DroneSwarmAi.clear();drone.setAiDisabled(false);started[0]=true;return;
            }
            ticks[0]++;var from=actor.getBoundingBox().getCenter();var to=target.getBoundingBox().getCenter();double plane=base.getX()+6.5,dx=to.x-from.x;
            if(Math.abs(dx)>1e-8){double t=(plane-from.x)/dx;var at=from.lerp(to,t);
                if(t>=0&&t<=1&&at.y>=base.getY()+4&&at.y<=base.getY()+4+width&&at.z>=base.getZ()&&at.z<=base.getZ()+width&&w.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,actor)).getType()==HitResult.Type.MISS)lane[0]=true;
            }
            for(var e:w.iterateEntities())if(e instanceof ProjectileEntity p&&p.getOwnerLiving()==drone&&e.getX()>base.getX()+7&&e.getPos().squaredDistanceTo(to)<9)shotThrough[0]=true;
            if(!(lane[0]&&shotThrough[0])&&ticks[0]<380)return;
            finished[0]=true;boolean intact=wall.stream().allMatch(p->w.getBlockState(p).isOf(Blocks.BEDROCK));var position=actor.getPos().subtract(Vec3d.of(base));boolean live=actor.isAlive(),role=drone.getRole()==kind.defaultRole;cleanup.run();
            c.assertTrue(live&&role&&intact&&lane[0]&&shotThrough[0],"autonomous typed firing lane: kind="+kind+" ticks="+ticks[0]+" lane="+lane[0]+" actualShotBeyondCover="+shotThrough[0]+" position="+position);c.complete();
        });
    }
}

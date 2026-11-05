package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual support goal and vanilla movement, isolated from weapon destruction by using a non-firing role. */
public final class DroneRouteGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-route-side",tickLimit=420)
    public void supportClimbsToAnOpeningInAVerticalWall(TestContext c){route(c,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-route-up",tickLimit=420)
    public void supportMovesSidewaysToAnOpeningAboveIt(TestContext c){route(c,true);}
    private static void route(TestContext c,boolean vertical) {
        var w=c.getWorld();var base=c.getAbsolutePos(new BlockPos(vertical?2000:1000,80,1000));
        var start=Vec3d.ofBottomCenter(base).add(.0,.1,.0);
        var goal=start.add(vertical?0:6,vertical?6:0,0);
        var chunks=new HashSet<ChunkPos>();
        for(int x=(base.getX()>>4)-2;x<=(base.getX()>>4)+2;x++)for(int z=(base.getZ()>>4)-2;z<=(base.getZ()>>4)+2;z++) {
            var chunk=new ChunkPos(x,z);if(!w.getForcedChunks().contains(chunk.toLong())){w.setChunkForced(x,z,true);chunks.add(chunk);}
        }
        var wall=new ArrayList<BlockPos>();
        for(int a=-20;a<=20;a++)for(int b=-20;b<=20;b++) {
            // Both apertures require a three-block detour perpendicular to the direct pursuit.
            if((a==3||a==4)&&b==0)continue;
            var p=base.add(vertical?a:3,vertical?3:a,b);wall.add(p);w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),3);
        }
        var drone=ModEntities.DRONE.create(w);var ally=ModEntities.DRONE.create(w);var threat=EntityType.COW.create(w);
        drone.applyRole(AiRole.SUPPORT);drone.setPosition(start);drone.setPersistent();drone.setInvulnerable(true);
        ally.applyRole(AiRole.MG);ally.setPosition(goal);ally.setHealth(1);ally.setAiDisabled(true);ally.setPersistent();ally.setInvulnerable(true);
        threat.setPosition(start.add(0,0,-6));threat.setAiDisabled(true);threat.setNoGravity(true);threat.setInvulnerable(true);
        c.assertTrue(w.spawnEntity(drone)&&w.spawnEntity(ally)&&w.spawnEntity(threat),"real route actors spawned");
        // One threat assignment isolates routing from detection; no manual ticks, velocity or follow teleports.
        drone.setTarget(threat);DroneSwarmAi.clear();
        int[] ticks={0};boolean[] finished={false};
        Runnable cleanup=()->{drone.discard();ally.discard();threat.discard();for(var p:wall)w.setBlockState(p,Blocks.AIR.getDefaultState(),3);for(var ch:chunks)w.setChunkForced(ch.x,ch.z,false);DroneSwarmAi.clear();};
        c.runAtEveryTick(()->{
            if(finished[0])return;int tick=++ticks[0];var at=drone.getPos();
            boolean crossed=vertical?at.y>base.getY()+4.5:at.x>base.getX()+4.5;
            if(crossed||tick>=380) {
                finished[0]=true;boolean intact=wall.stream().allMatch(p->w.getBlockState(p).isOf(Blocks.BEDROCK));cleanup.run();
                c.assertTrue(crossed&&intact,"physical route through intact aperture: vertical="+vertical+" tick="+tick+" local="+at.subtract(Vec3d.of(base)));
                c.complete();
            }
        });
    }
}

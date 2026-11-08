package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.mega.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class UfoAiGameTests implements FabricGameTest {
    private void admitted(TestContext c,java.util.function.Consumer<Fixture> action){
        var at=c.getAbsolutePos(new BlockPos(8,220,8));var chunks=new AiFixtureChunks(c.getWorld(),at,3);boolean[] started={false};int[] wait={0};
        var positions=new ArrayList<BlockPos>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)positions.add(at.add(16*x,0,16*z));
        c.runAtEveryTick(()->{if(started[0])return;if(!chunks.entityTicking(positions.toArray(BlockPos[]::new))){if(++wait[0]<80)return;started[0]=true;chunks.close();c.assertTrue(false,"AI fixture needs actual loaded entity-ticking columns");return;}started[0]=true;
            var b=ModEntities.SKY_UFO.create(c.getWorld());b.setAutopilot(false);b.refreshPositionAndAngles(at.getX(),at.getY(),at.getZ(),0,0);c.assertTrue(c.getWorld().spawnEntity(b),"real physical UFO");b.tick();
            var p=c.createMockCreativeServerPlayerInWorld();p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.getAbilities().flying=false;p.setNoGravity(true);DescentPlayerData.get(p).setEnabled(false);p.setPosition(b.localToWorld(new Vec3d(-2.5,-.4,44)));p.setVelocity(Vec3d.ZERO);
            var f=new Fixture(c,b,p,chunks);try{action.accept(f);}catch(Throwable failure){f.close();throw failure;}
        });
    }
    private record Fixture(TestContext c,SkyUfoEntity body,ServerPlayerEntity target,AiFixtureChunks chunks) implements AutoCloseable{
        List<ProjectileEntity> shots(){return c.getWorld().getEntitiesByClass(ProjectileEntity.class,body.getBoundingBox().expand(120),s->{var n=s.writeNbt(new NbtCompound());return n.containsUuid("owner")&&n.getUuid("owner").equals(body.getUuid());});}
        public void close(){shots().forEach(ProjectileEntity::discard);UfoControlSystem.release(target);target.discard();body.discard();chunks.close();}
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="ufo-autonomous-fire",tickLimit=180)
    public void anUnpilotedDefaultHullFiresItsRealBatteriesAtASurvivalTarget(TestContext c){admitted(c,f->{
        f.body.setAutopilot(true);
        c.waitAndRun(35,()->{try{
            c.assertTrue(!UfoControlSystem.controls(f.target),"target cannot be a fabricated pilot");
            c.assertTrue(!f.shots().isEmpty(),"unpiloted default UFO must fire actual hull-owned projectiles");
        }finally{f.close();}c.complete();});
    });}
}

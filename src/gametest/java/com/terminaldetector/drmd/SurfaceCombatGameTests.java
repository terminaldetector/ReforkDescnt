package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.gravity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual ordinary AI ticks, rather than invoking the steering helper directly. */
public final class SurfaceCombatGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="surface-combat-live",tickLimit=300)
    public void defaultTripodPursuesAndFiresFromAWallWithLocalEyes(TestContext context) {
        var world=context.getWorld();BlockPos centre=context.getAbsolutePos(new BlockPos(6,80,6));
        Set<Long> added=new HashSet<>();
        int cx=centre.getX()>>4,cz=centre.getZ()>>4;
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+3;z++) {
            long key=ChunkPos.toLong(x,z);
            if(!world.getForcedChunks().contains(key)) {world.setChunkForced(x,z,true);added.add(key);}
        }
        for(BlockPos p:BlockPos.iterate(centre.add(-2,-5,-4),centre.add(10,5,32)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(BlockPos p:BlockPos.iterate(centre.add(0,-5,-4),centre.add(0,5,32)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        MobEntity actor=ModEntities.TRIPOD.create(world);
        var target=EntityType.COW.create(world);target.setAiDisabled(true);target.setNoGravity(true);target.setInvulnerable(true);
        Vec3d start=new Vec3d(centre.getX()+1.02,centre.getY()+.5,centre.getZ()+.5);
        actor.setPosition(start);target.setPosition(start.add(0,0,24));
        UUID field=UUID.randomUUID();
        Runnable cleanup=()->{
            GravityFields.remove(field);actor.discard();target.discard();
            for(var shot:world.getEntitiesByClass(ProjectileEntity.class,new Box(centre).expand(100),s->s.getOwnerLiving()==actor))shot.discard();
            EntityGravitySystem.tick(world.getServer());
            for(long key:added)world.setChunkForced(ChunkPos.getPackedX(key),ChunkPos.getPackedZ(key),false);
        };
        try {
            context.assertTrue(!actor.isAiDisabled(),"Tripod AI enabled without opt-in");
            context.assertTrue(ActorFrames.trySetUp(actor,new Vec3d(1,0,0)),"wall body fits before spawn");
            world.spawnEntity(actor);world.spawnEntity(target);actor.setTarget(target);
            GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),actor.getBlockPos(),new Vec3d(-1,0,0),64,1,FieldShape.SPHERE,"live wall combat"));
            EntityGravitySystem.tick(world.getServer());
        } catch(Throwable failure){cleanup.run();throw failure;}
        Set<UUID> shots=new HashSet<>();
        boolean[] finished={false};
        // Forced chunks become entity-ticking asynchronously. Count ordinary mob ticks,
        // not global fixture ticks that can elapse while this entity is still frozen.
        for(int i=1;i<299;i++)context.runAtTick(i,()->{
            if(finished[0])return;
            for(var shot:world.getEntitiesByClass(ProjectileEntity.class,new Box(centre).expand(100),s->s.getOwnerLiving()==actor))shots.add(shot.getUuid());
            if(actor.age<70)return;
            finished[0]=true;
            try {
                System.out.println("SURFACE_COMBAT entityTicks="+actor.age+" shots="+shots.size());
                context.assertTrue(actor.isAlive() && actor.getZ()>start.z+1,"ordinary AI pursues along wall");
                context.assertTrue(Math.abs(actor.getX()-start.x)<.1,"body remains on the surface");
                context.assertTrue(actor.getEyePos().subtract(actor.getPos()).normalize().distanceTo(new Vec3d(1,0,0))<1e-6,"combat sight uses local eyes");
                context.assertTrue(!shots.isEmpty(),"ordinary wall AI actually fired an owned projectile");
            } finally {cleanup.run();}
            context.complete();
        });
        context.runAtTick(299,()->{
            if(!finished[0]) {
                try {context.assertTrue(false,"Tripod did not receive 70 ordinary entity ticks; age="+actor.age);}
                finally {cleanup.run();}
            }
        });
    }
}

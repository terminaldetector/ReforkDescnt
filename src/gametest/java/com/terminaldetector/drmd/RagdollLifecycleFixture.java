package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.*;

/** Finite, private ordinary-world scene. No hand ticks, motion reset or production budget edits. */
final class RagdollLifecycleFixture implements AutoCloseable {
    final TestContext context;final ServerWorld world;final BlockPos floor;final Box area;final AiFixtureChunks chunks;
    final List<Entity> owned=new ArrayList<>();final Map<BlockPos,BlockState> cells=new LinkedHashMap<>();final List<Runnable> cleanup=new ArrayList<>();
    boolean closed,retain;int callbacks;
    RagdollLifecycleFixture(TestContext context,int width,int length,int xOffset){
        this.context=context;world=context.getWorld();floor=context.getAbsolutePos(new BlockPos(1000+xOffset,80,1000));
        chunks=new AiFixtureChunks(world,floor.add(width/2,0,length/2),width>20?2:1);
        area=new Box(Vec3d.of(floor.add(-5,0,-5)),Vec3d.of(floor.add(width+6,12,length+6)));
        for(var p:BlockPos.iterate(floor.add(-3,0,-3),floor.add(width+3,7,length+3)))put(p,p.getY()==floor.getY()?Blocks.OBSIDIAN.getDefaultState():Blocks.AIR.getDefaultState());
    }
    void put(BlockPos pos,BlockState state){var key=pos.toImmutable();cells.putIfAbsent(key,world.getBlockState(key));world.setBlockState(key,state,2);}
    void own(Entity entity){owned.add(entity);}
    void drive(int limit,Runnable action){context.runAtEveryTick(()->{if(closed)return;try{context.assertTrue(++callbacks<=limit,"bounded ordinary lifecycle clock: callbacks="+callbacks);action.run();}catch(RuntimeException|Error failure){close();throw failure;}});}
    void complete(){close();context.complete();}
    public void close(){if(closed)return;closed=true;if(!retain){owned.forEach(Entity::discard);cleanup.forEach(Runnable::run);cells.forEach((p,s)->world.setBlockState(p,s,2));}chunks.close();}
    void assertTicks(Entity entity,int age,long time,int minimum){context.assertTrue(world.getEntity(entity.getUuid())==entity&&chunks.entityTicking(entity.getBlockPos())&&entity.age-age>=minimum&&world.getTime()-time>=minimum,"same identity advances on actual ordinary entity/world ticks");}
    RagdollEntity supportedJet(int offset,UUID group){
        var body=new RagdollEntity(ModEntities.RAGDOLL,world);body.setPosition(Vec3d.ofCenter(floor.add(offset,0,0)).add(0,.5+.235+1e-5,0));
        var tag=new NbtCompound();body.writeNbt(tag);tag.remove("ragdoll");tag.putInt("hullVersion",1);tag.putString("flightHull","jet");tag.putFloat("hullScale",1);tag.putInt("rigVersion",2);
        tag.putInt("debrisVersion",1);tag.putUuid("debrisGroup",group);tag.putString("debrisCause","authored-rest-fixture");tag.putLong("debrisBorn",world.getTime());body.readNbt(tag);
        // Authored pre-spawn save pose: all three parts contact the same floor, anchors remain exact,
        // and both wing swings are ~3deg, well within the actual v2 35deg cone. No live motion is reset.
        double theta=Math.asin(.235/Math.hypot(.935,.185))-Math.atan2(.185,.935);
        var core=body.simulation().parts().getFirst().body();
        for(var joint:body.rig().joints()){
            double angle=joint.second()==1?theta:-theta;var rotation=new Quat(0,0,Math.sin(angle/2),Math.cos(angle/2));
            var part=body.simulation().parts().get(joint.second());part.body().withRotation(rotation)
                .withPosition(core.position().plus(joint.firstAnchor()).minus(rotation.rotate(joint.secondAnchor())));
        }
        var authored=new NbtCompound();body.writeNbt(authored);body.readNbt(authored);own(body);return body;
    }
}

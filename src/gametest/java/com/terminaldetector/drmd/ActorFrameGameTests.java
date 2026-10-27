package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.UUID;

public final class ActorFrameGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="actor-frames")
    public void unspawnedNbtCopyCannotAcquireAReleaseLease(TestContext context) {
        var cow=actor(context);var copy=EntityType.COW.create(context.getWorld());
        BlockPos overhead=cow.getBlockPos().up();
        try {
            context.assertTrue(ActorFrames.trySetUp(cow,new Vec3d(1,0,0)),"original physical pose set");
            copy.readNbt(cow.writeNbt(new NbtCompound()));cow.discard();
            context.getWorld().setBlockState(overhead,Blocks.STONE.getDefaultState(),2);
            context.assertTrue(!ActorFrames.tryRelease(copy),"test copy has obstructed upright headroom");
            EntityGravitySystem.tick(context.getWorld().getServer());
            context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(copy) && copy.getFinalGravity()>0,
                "physical-pose index cannot give an unregistered NBT object a runtime lease");
        } finally {cow.discard();copy.discard();context.getWorld().setBlockState(overhead,Blocks.AIR.getDefaultState(),2);EntityGravitySystem.tick(context.getWorld().getServer());}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="actor-frames")
    public void groundBestiaryUsesLocalBodyAndSurfaceSteeringOnSixAxes(TestContext context) {
        var world=context.getWorld();BlockPos centre=context.getAbsolutePos(new BlockPos(6,80,6));
        for(BlockPos p:BlockPos.iterate(centre.add(-8,-8,-8),centre.add(8,8,8)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(var type:new net.minecraft.entity.EntityType<?>[]{com.terminaldetector.drmd.entity.ModEntities.TRIPOD,
            com.terminaldetector.drmd.entity.ModEntities.SPIDER_TURRET}) {
            var mob=(net.minecraft.entity.mob.MobEntity)type.create(world);
            try {
                context.assertTrue(!mob.isAiDisabled(),"ground bestiary AI enabled by default");mob.setAiDisabled(true);
                mob.setPosition(Vec3d.ofCenter(centre));world.spawnEntity(mob);
                for(Direction direction:Direction.values()) {
                    Vec3d up=Vec3d.of(direction.getVector());
                    context.assertTrue(ActorFrames.trySetUp(mob,up),"roster adapter accepts clear six-axis pose "+type+" "+direction);
                    context.assertTrue(mob.getEyePos().subtract(mob.getPos()).normalize().distanceTo(up)<1e-6,"bestiary eye follows body");
                    Vec3d heading=ActorFrameMath.toWorld(new Vec3d(.4,0,.6),up);
                    mob.setVelocity(up.multiply(.11));ActorFrames.driveSurface(mob,heading,.2);
                    context.assertTrue(Math.abs(mob.getVelocity().dotProduct(up)-.11)<1e-8,"surface drive preserves normal force");
                    context.assertTrue(Math.abs(ActorFrames.tangent(mob,mob.getVelocity()).length()-.2)<1e-8,"movement lies in local tangent plane");
                    com.terminaldetector.drmd.ai.FlightAttitude.steer(mob,heading,1000,1);
                    context.assertTrue(mob.getRotationVector().distanceTo(heading.normalize())<1e-5,"AI world heading matches transformed interaction ray");
                }
            } finally {mob.discard();}
        }
        context.complete();
    }

    private static CowEntity actor(TestContext context) {
        BlockPos centre=context.getAbsolutePos(new BlockPos(4,80,4));
        for(BlockPos p:BlockPos.iterate(centre.add(-5,-5,-5),centre.add(5,5,5)))
            context.getWorld().setBlockState(p,Blocks.AIR.getDefaultState(),2);
        var cow=EntityType.COW.create(context.getWorld());cow.setAiDisabled(true);
        cow.setPosition(Vec3d.ofCenter(centre));context.getWorld().spawnEntity(cow);return cow;
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="actor-frames")
    public void sixAxesShareBodyEyeAndLookWithoutTeleporting(TestContext context) {
        var cow=actor(context);Vec3d feet=cow.getPos();cow.setYaw(0);cow.setPitch(-90);
        try {
            for(Direction direction:Direction.values()) {
                Vec3d up=Vec3d.of(direction.getVector());
                context.assertTrue(ActorFrames.trySetUp(cow,up),"clear pose accepted "+direction);
                var box=cow.getBoundingBox();var expected=ActorFrames.body(cow,feet,up);
                context.assertTrue(box.equals(expected) && cow.getPos().equals(feet),"actual body updated without teleport "+direction);
                Vec3d eye=cow.getEyePos().subtract(feet).normalize();
                context.assertTrue(eye.distanceTo(up)<1e-6,"server interaction eye follows up "+direction);
                context.assertTrue(cow.getRotationVector().distanceTo(up)<1e-6,"look follows local pitch "+direction);
                context.assertTrue(cow.getRotationVecClient().distanceTo(up)<1e-6,"client-style ray follows same basis "+direction);
            }
        } finally {cow.discard();}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="actor-frames")
    public void obstructionRejectsTurnAndReleaseWithoutChangingTheSafeBody(TestContext context) {
        var cow=actor(context);Vec3d feet=cow.getPos();var world=context.getWorld();
        BlockPos side=cow.getBlockPos().east();BlockPos overhead=cow.getBlockPos().up();
        try {
            world.setBlockState(side,Blocks.STONE.getDefaultState(),2);
            var original=cow.getBoundingBox();
            context.assertTrue(!ActorFrames.trySetUp(cow,new Vec3d(1,0,0)),"turn into occupied headroom rejected");
            context.assertTrue(cow.getBoundingBox().equals(original) && !ActorFrames.active(cow),"rejected turn leaves old geometry");
            world.setBlockState(side,Blocks.AIR.getDefaultState(),2);
            context.assertTrue(ActorFrames.trySetUp(cow,new Vec3d(1,0,0)),"sideways body fits");
            world.setBlockState(overhead,Blocks.STONE.getDefaultState(),2);
            context.assertTrue(!ActorFrames.tryRelease(cow) && ActorFrames.active(cow),"unsafe upright release deferred");
            context.assertTrue(cow.getPos().equals(feet) && world.isSpaceEmpty(cow),"safe pose preserved in corridor");
            world.setBlockState(overhead,Blocks.AIR.getDefaultState(),2);
            context.assertTrue(ActorFrames.tryRelease(cow) && !ActorFrames.active(cow),"release succeeds after headroom opens");
        } finally {world.setBlockState(side,Blocks.AIR.getDefaultState(),2);world.setBlockState(overhead,Blocks.AIR.getDefaultState(),2);cow.discard();}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="actor-frames")
    public void savedPoseRestoresButTemporaryAccelerationOwnershipDoesNot(TestContext context) {
        var cow=actor(context);var copy=EntityType.COW.create(context.getWorld());
        try {
            context.assertTrue(ActorFrames.trySetUp(cow,new Vec3d(0,-1,0)),"ceiling pose set");
            var nbt=cow.writeNbt(new NbtCompound());copy.readNbt(nbt);
            context.assertTrue(ActorFrames.up(copy).equals(new Vec3d(0,-1,0)),"saved physical pose restored");
            context.assertTrue(copy.getBoundingBox().equals(cow.getBoundingBox()),"reload restores actual body");
            context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(copy) && !copy.hasNoGravity(),"pose is not a runtime gravity lease");
            nbt.getCompound("drmd:actor_frame").putFloat("x",Float.NaN);copy.readNbt(nbt);
            context.assertTrue(!ActorFrames.active(copy),"invalid saved frame rejected");
            nbt.getCompound("drmd:actor_frame").putInt("version",99);copy.readNbt(nbt);
            context.assertTrue(!ActorFrames.active(copy),"unknown saved version rejected");
        } finally {cow.discard();copy.discard();}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="actor-frames")
    public void fieldExitReleasesBodyEvenWhenTheWorldHasNoEmittersLeft(TestContext context) {
        var cow=actor(context);UUID id=UUID.randomUUID();
        try {
            GravityFields.put(new GravityFields.Field(id,context.getWorld().getRegistryKey(),cow.getBlockPos(),
                new Vec3d(1,0,0),16,1,FieldShape.SPHERE,"frame lifecycle"));
            EntityGravitySystem.tick(context.getWorld().getServer());
            context.assertTrue(ActorFrames.up(cow).distanceTo(new Vec3d(-1,0,0))<1e-6,"field owns physical pose");
            GravityFields.remove(id);EntityGravitySystem.tick(context.getWorld().getServer());
            context.assertTrue(!ActorFrames.active(cow) && cow.getFinalGravity()>0,"last emitter removal restores vanilla body and gravity");
        } finally {GravityFields.remove(id);cow.discard();EntityGravitySystem.tick(context.getWorld().getServer());}
        context.complete();
    }
}

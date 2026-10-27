package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.UUID;

public final class EntityFieldGravityGameTests implements FabricGameTest {
    private static CowEntity actor(TestContext context,boolean noGravity) {
        var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(4,80,4));
        for(var p:BlockPos.iterate(centre.add(-6,-6,-6),centre.add(6,6,6)))
            if(!world.getBlockState(p).isAir())world.setBlockState(p,net.minecraft.block.Blocks.AIR.getDefaultState(),2);
        var cow=EntityType.COW.create(world);cow.setAiDisabled(true);cow.setNoGravity(noGravity);
        cow.setPosition(Vec3d.ofCenter(centre));world.spawnEntity(cow);return cow;
    }
    private static void field(TestContext context,UUID id,BlockPos origin,Vec3d down,float power) {
        GravityFields.put(new GravityFields.Field(id,context.getWorld().getRegistryKey(),origin,down,32,power,FieldShape.SPHERE,"actor test"));
    }
    private static void tick(TestContext context){EntityGravitySystem.tick(context.getWorld().getServer());}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void ordinaryActorNeverSerializesTheTemporaryGravityOverride(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();double original=cow.getFinalGravity();
        try {
            context.assertTrue(original>0,"ordinary cow starts with standard acceleration");
            field(context,id,cow.getBlockPos(),new Vec3d(1,0,0),1);tick(context);
            context.assertTrue(EntityGravitySystem.suppressesVanillaGravity(cow) && cow.getFinalGravity()==0,"only standard acceleration suppressed");
            context.assertTrue(!cow.hasNoGravity(),"saved flag still belongs to the actor");
            var nbt=cow.writeNbt(new NbtCompound());
            context.assertTrue(!nbt.getBoolean("NoGravity"),"save contains original gravity");
            var copy=EntityType.COW.create(context.getWorld());copy.readNbt(nbt);
            context.assertTrue(copy.getUuid().equals(cow.getUuid()),"test copy retains saved identity");
            context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(copy) && copy.getFinalGravity()>0,"reloaded object cannot inherit an old runtime lease");
            copy.discard();GravityFields.remove(id);tick(context);
            context.assertTrue(!cow.hasNoGravity() && Math.abs(cow.getFinalGravity()-original)<1e-9,"leaving the field restores standard acceleration");
        } finally {GravityFields.remove(id);cow.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void naturallyWeightlessActorAndExternalFlagOwnerSurviveFieldExit(TestContext context) {
        var cow=actor(context,true);var id=UUID.randomUUID();
        try {
            field(context,id,cow.getBlockPos(),new Vec3d(-1,0,0),1);tick(context);
            context.assertTrue(cow.hasNoGravity() && cow.writeNbt(new NbtCompound()).getBoolean("NoGravity"),"original weightlessness saved inside field");
            GravityFields.remove(id);tick(context);
            context.assertTrue(cow.hasNoGravity() && cow.getFinalGravity()==0,"field exit preserves weightlessness");
            cow.setNoGravity(false);field(context,id,cow.getBlockPos(),new Vec3d(-1,0,0),1);tick(context);
            cow.setNoGravity(true);GravityFields.remove(id);tick(context);
            context.assertTrue(cow.hasNoGravity(),"another owner's flag change survives release");
        } finally {GravityFields.remove(id);cow.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void airborneGravityAccumulatesAndScalesAlongAllSixAxes(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();var origin=cow.getBlockPos();var initial=cow.getPos();
        try {
            for(var direction:Direction.values()) {
                Vec3d down=Vec3d.of(direction.getVector());cow.setPosition(initial);cow.setVelocity(Vec3d.ZERO);
                field(context,id,origin,down,.5f);tick(context);double first=cow.getVelocity().dotProduct(down);
                context.assertTrue(first>.02 && first<.04,"half-strength acceleration on "+direction);
                tick(context);double second=cow.getVelocity().dotProduct(down);
                context.assertTrue(second>first*1.8,"normal velocity accumulates on "+direction);
                cow.setPosition(initial);cow.setVelocity(Vec3d.ZERO);field(context,id,origin,down,1);tick(context);
                context.assertTrue(Math.abs(cow.getVelocity().dotProduct(down)/first-2)<.01,"field strength scales acceleration on "+direction);
            }
        } finally {GravityFields.remove(id);cow.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void ridingAndClearingReleaseOnlyRuntimeGravity(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();var cart=EntityType.MINECART.create(context.getWorld());
        cart.setPosition(cow.getPos());context.getWorld().spawnEntity(cart);
        try {
            field(context,id,cow.getBlockPos(),new Vec3d(1,0,0),1);tick(context);
            context.assertTrue(EntityGravitySystem.suppressesVanillaGravity(cow),"field owns acceleration");
            context.assertTrue(cow.startRiding(cart,true),"actor mounted the vehicle");tick(context);
            context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(cow) && !cow.hasNoGravity(),"rider releases runtime override without flag mutation");
            cow.stopRiding();tick(context);EntityGravitySystem.clear();
            context.assertTrue(cow.getFinalGravity()>0 && !cow.hasNoGravity(),"clear releases acceleration immediately");
        } finally {GravityFields.remove(id);cow.stopRiding();cow.discard();cart.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=40,batchId="entity-field-live")
    public void actualLivingTravelDoesNotAddWorldDownInsideASidewaysField(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();var start=cow.getPos();
        cow.setAiDisabled(false);cow.getNavigation().stop();cow.setMovementSpeed(0);
        var world=context.getWorld();long chunk=cow.getChunkPos().toLong();
        boolean forced=world.getForcedChunks().contains(chunk);
        world.setChunkForced(cow.getChunkPos().x,cow.getChunkPos().z,true);
        field(context,id,cow.getBlockPos(),new Vec3d(1,0,0),.5f);tick(context);
        context.runAtTick(8,()->{
            try {
                context.assertTrue(cow.getX()>start.x+.3 && cow.getVelocity().x>.05,"actual entity ticks accelerate along field down");
                context.assertTrue(Math.abs(cow.getY()-start.y)<.01,"vanilla travel adds no second world-Y gravity");
                context.assertTrue(!cow.hasNoGravity(),"actual ticks leave the original flag unchanged");
            } finally {
                GravityFields.remove(id);cow.discard();tick(context);
                if(!forced)world.setChunkForced(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),false);
            }
            context.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void airborneFieldPassCannotAdvanceTheActorASecondTime(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();var start=cow.getPos();
        try {
            field(context,id,cow.getBlockPos(),new Vec3d(1,0,0),.5f);tick(context);
            context.assertTrue(cow.getPos().distanceTo(start)<1e-9 && cow.getVelocity().x>.02,"field changes velocity without moving the actor");
            cow.setAiDisabled(false);
            context.assertTrue(cow.canMoveVoluntarily(),"ordinary travel is enabled for this actor");
            cow.travel(Vec3d.ZERO);var moved=cow.getPos();
            context.assertTrue(moved.x>start.x+.02 && moved.x<start.x+.04,"ordinary travel integrates one movement step");
            tick(context);
            context.assertTrue(cow.getPos().distanceTo(moved)<1e-9,"next field pass does not integrate a duplicate step");
        } finally {GravityFields.remove(id);cow.discard();tick(context);}
        context.complete();
    }
}

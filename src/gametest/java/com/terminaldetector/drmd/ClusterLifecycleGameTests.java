package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.bombardment.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

public final class ClusterLifecycleGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void regularClusterCreatesSevenChildrenExactlyOnce(TestContext context) {checkBurst(context,OrdnanceType.CLUSTER,7);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void heavyClusterCreatesFourteenChildrenExactlyOnce(TestContext context) {checkBurst(context,OrdnanceType.HEAVY_CLUSTER,14);}
    private void checkBurst(TestContext context,OrdnanceType type,int expected) {
        var world=context.getWorld();var position=Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,9,5)));
        UUID owner=UUID.randomUUID();var parent=ModEntities.AERIAL_BOMB.create(world);parent.setPosition(position);parent.configure(type,null,null);
        var nbt=parent.writeNbt(new NbtCompound());nbt.putBoolean("Landed",true);nbt.putInt("FlightAge",20);nbt.putUuid("owner",owner);parent.readNbt(nbt);
        world.spawnEntity(parent);parent.tick();
        var children=world.getEntitiesByClass(AerialBombEntity.class,parent.getBoundingBox().expand(4),entity->{
            var saved=entity.writeNbt(new NbtCompound());return saved.getBoolean("sub") && saved.containsUuid("owner") && saved.getUuid("owner").equals(owner);
        });
        context.assertTrue(parent.isRemoved() && children.size()==expected,"complete design burst count "+expected);
        parent.tick();
        var spent=ModEntities.AERIAL_BOMB.create(world);spent.readNbt(parent.writeNbt(new NbtCompound()));spent.tick();
        context.assertTrue(spent.isRemoved(),"saved spent parent cannot release another burst");
        for(var child:children) {
            var saved=child.writeNbt(new NbtCompound());
            context.assertTrue(saved.getBoolean("sub") && !saved.getBoolean("dispensed") && saved.getFloat("blastMul")>0,"child payload/arming state serialized");
            child.discard();
        }
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void expiredAndVoidParentsCannotSpawnAnotherGeneration(TestContext context) {
        var world=context.getWorld();var bomb=ModEntities.AERIAL_BOMB.create(world);
        bomb.configure(OrdnanceType.CLUSTER,null,null);bomb.setPosition(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,9,5))));
        var state=bomb.writeNbt(new NbtCompound());state.putInt("FlightAge",1800);bomb.readNbt(state);bomb.tick();
        context.assertTrue(bomb.isRemoved(),"spent lifetime ends without another cluster generation");
        var lower=ModEntities.AERIAL_BOMB.create(world);lower.configure(OrdnanceType.CLUSTER,null,null);lower.setPosition(0,world.getBottomY()-20,0);lower.tick();
        context.assertTrue(lower.isRemoved(),"void does not create explosions or children in unloaded space");
        context.complete();
    }
}

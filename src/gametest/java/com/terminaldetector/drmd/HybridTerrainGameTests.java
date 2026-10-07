package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.geometry.*;
import com.terminaldetector.drmd.world.micro.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class HybridTerrainGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void explicitOrganicModePreservesMaterialMaskAndSave(TestContext ctx) {
        var world=ctx.getWorld();var p=ctx.getAbsolutePos(new BlockPos(2,4,2));
        world.setBlockState(p,Blocks.STONE.getDefaultState());
        ctx.assertTrue(HybridTerrainCommands.apply(world,p,p,true)==1,"explicit conversion");
        var be=(CarvedBlockEntity)world.getBlockEntity(p);var snapshot=be.getRenderData();
        BlockDamage.hit(world,p,Vec3d.ofCenter(p).add(.4,0,0),12);
        ctx.assertTrue(be.organic() && be.mask()!=MicroGrid.FULL,"damage retains organic mode");
        ctx.assertTrue(snapshot.mask()==MicroGrid.FULL,"immutable render snapshot");
        var nbt=be.createNbt(world.getRegistryManager());
        var copy=new CarvedBlockEntity(p,world.getBlockState(p));copy.read(nbt,world.getRegistryManager());
        ctx.assertTrue(copy.organic() && copy.mask()==be.mask() && copy.source().isOf(Blocks.STONE),"NBT round trip");
        long mask=be.mask();HybridTerrainCommands.apply(world,p,p,false);
        ctx.assertTrue(!be.organic() && be.mask()==mask,"rigid mode does not repair damage");ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void collisionRecomputesWhenRigidNeighbourIsPlacedAndRemoved(TestContext ctx) {
        var world=ctx.getWorld();var p=ctx.getAbsolutePos(new BlockPos(2,4,2));
        for(BlockPos q:BlockPos.iterate(p.add(-1,-1,-1),p.add(1,1,1))) world.setBlockState(q,Blocks.AIR.getDefaultState());
        world.setBlockState(p,Blocks.STONE.getDefaultState());HybridTerrainCommands.apply(world,p,p,true);
        var state=world.getBlockState(p);var free=state.getCollisionShape(world,p).getBoundingBox();
        ctx.assertTrue(Math.abs(free.minX-.25)<1e-9 && Math.abs(free.maxX-.75)<1e-9,"dynamic shape is not a cached cube");
        world.setBlockState(p.east(),Blocks.STONE.getDefaultState());
        ctx.assertTrue(Math.abs(state.getCollisionShape(world,p).getBoundingBox().maxX-1)<1e-9,"rigid neighbour pins shared face");
        world.setBlockState(p.east(),Blocks.AIR.getDefaultState());
        ctx.assertTrue(Math.abs(state.getCollisionShape(world,p).getBoundingBox().maxX-.75)<1e-9,"neighbour removal invalidates shape");ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void organicSelectionSkipsUnsupportedAndCanRestoreIntactStone(TestContext ctx) {
        var world=ctx.getWorld();var p=ctx.getAbsolutePos(new BlockPos(1,4,2));
        world.setBlockState(p,Blocks.STONE.getDefaultState());world.setBlockState(p.east(),Blocks.CHEST.getDefaultState());
        world.setBlockState(p.east(2),Blocks.OAK_PLANKS.getDefaultState());
        ctx.assertTrue(HybridTerrainCommands.apply(world,p,p.east(2),true)==1,"allowlist and inventory guard");
        ctx.assertTrue(world.getBlockState(p.east()).isOf(Blocks.CHEST) && world.getBlockState(p.east(2)).isOf(Blocks.OAK_PLANKS),"unsupported blocks untouched");
        HybridTerrainCommands.apply(world,p,p,false);
        ctx.assertTrue(world.getBlockState(p).isOf(Blocks.STONE),"intact rigid mode restores source");
        boolean rejected=false;
        try { HybridTerrainCommands.apply(world,p,p.add(4096,0,0),true); } catch(IllegalArgumentException e) {rejected=true;}
        ctx.assertTrue(rejected && world.getBlockState(p).isOf(Blocks.STONE),"oversized selection rejects before mutation");ctx.complete();
    }
}

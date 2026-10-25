package com.terminaldetector.drmd;

import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.micro.TunnelCarving;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class DrillSafetyGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void softenedStoneMakesProgressInsteadOfReturningCobblestoneForever(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,5,4));world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        context.assertTrue(WeaponFx.melt(world,at,2,null),"first material stage applied");
        context.assertTrue(WeaponFx.melt(world,at,2,null) && world.getBlockState(at).isAir(),"second stage actually removes material");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hotRockDoesNotCreateLavaAndProtectedBlocksOrContainersStayIntact(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,5,4));world.setBlockState(at,Blocks.NETHERRACK.getDefaultState(),2);
        WeaponFx.melt(world,at,2,null);context.assertTrue(world.getFluidState(at).isEmpty(),"mining does not create a lava source");
        world.setBlockState(at,Blocks.BEDROCK.getDefaultState(),2);context.assertTrue(!WeaponFx.melt(world,at,3,null) && world.getBlockState(at).isOf(Blocks.BEDROCK),"bedrock preserved");
        world.setBlockState(at,Blocks.CHEST.getDefaultState(),2);context.assertTrue(!WeaponFx.melt(world,at,3,null) && world.getBlockEntity(at)!=null,"container not replaced by a heat palette");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void repeatingOneBoreDoesNotRewriteIdenticalBoundaryMasks(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(5,5,5));
        for(var p:BlockPos.iterate(at.add(-2,-2,-2),at.add(2,2,2)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        Vec3d middle=Vec3d.ofCenter(at);
        int first=TunnelCarving.carveCapsule(world,middle,middle.add(0,1,0),1.1,null);
        int second=TunnelCarving.carveCapsule(world,middle,middle.add(0,1,0),1.1,null);
        context.assertTrue(first>0 && second==0,"same bore only counts real geometry changes");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void remoteMiningCannotPullAnUnloadedChunkIntoMemory(TestContext context) {
        var world=context.getWorld();var far=new BlockPos(500000,80,500000);
        context.assertTrue(!world.isChunkLoaded(far),"fixture remote target starts unloaded");
        context.assertTrue(!WeaponFx.melt(world,far,3,null) && !world.isChunkLoaded(far),"guard runs before block-state reads");context.complete();
    }
}

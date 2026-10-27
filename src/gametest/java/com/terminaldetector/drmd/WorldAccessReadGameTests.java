package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.access.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import java.util.concurrent.CompletableFuture;

public final class WorldAccessReadGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void availableAirAndUnavailableVolumeAreDistinct(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);
        var at=context.getAbsolutePos(new BlockPos(4,4,4));world.setBlockState(at,Blocks.AIR.getDefaultState(),2);
        context.assertTrue(access.backend()==CubicAvailability.Backend.STOCK,"explicit stock backend in the ordinary-world control");
        context.assertTrue(access.readIfLoaded(at).orElseThrow().isAir(),"loaded air is a present state");
        var far=at.add(8_000_000,0,0);
        context.assertTrue(!world.isChunkLoaded(far),"remote test column starts unavailable");
        context.assertTrue(access.readIfLoaded(far).isEmpty() && !world.isChunkLoaded(far),"read does not create a column or invent air");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void backendReadReturnsTheActualStateAndRejectsOutsideHeight(TestContext context) {
        var world=context.getWorld();var access=WorldAccess.of(world);
        var at=context.getAbsolutePos(new BlockPos(4,4,4));world.setBlockState(at,Blocks.IRON_BLOCK.getDefaultState(),2);
        context.assertTrue(access.readIfLoaded(at).orElseThrow().isOf(Blocks.IRON_BLOCK),"actual backend state, not a saved observation");
        world.setBlockState(at,Blocks.GOLD_BLOCK.getDefaultState(),2);
        context.assertTrue(access.readIfLoaded(at).orElseThrow().isOf(Blocks.GOLD_BLOCK),"same-tick changes are immediately authoritative");
        context.assertTrue(access.readIfLoaded(new BlockPos(at.getX(),world.getTopY(),at.getZ())).isEmpty(),"outside the dimension has no readable cell");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void workerThreadCannotReadMutableWorldStorage(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,4,4));
        world.setBlockState(at,Blocks.IRON_BLOCK.getDefaultState(),2);
        boolean denied=CompletableFuture.supplyAsync(()->WorldAccess.of(world).readIfLoaded(at).isEmpty()).join();
        context.assertTrue(denied && world.getBlockState(at).isOf(Blocks.IRON_BLOCK),"worker is denied before touching backend cells");
        context.complete();
    }
}

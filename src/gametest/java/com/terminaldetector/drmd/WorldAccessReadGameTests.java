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
        boolean denied=CompletableFuture.supplyAsync(()-> {
            var access=WorldAccess.of(world);
            return access.readIfLoaded(at).isEmpty() && access.cubeState(access.cubeAt(at))==VolumetricWorldAccess.CubeState.WRONG_THREAD;
        }).join();
        context.assertTrue(denied && world.getBlockState(at).isOf(Blocks.IRON_BLOCK),"worker is denied before touching backend cells");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void volumetricMetadataDistinguishesUnknownFromOutsideAndLoadedAir(TestContext context) {
        var world=context.getWorld();VolumetricWorldAccess access=WorldAccess.of(world);
        var at=context.getAbsolutePos(new BlockPos(4,4,4));world.setBlockState(at,Blocks.AIR.getDefaultState(),2);
        var bounds=access.bounds();
        context.assertTrue(bounds.bottomY()==world.getBottomY() && bounds.topYExclusive()==world.getTopY(),"actual dimension registry bounds");
        context.assertTrue(access.cubeState(access.cubeAt(at))==VolumetricWorldAccess.CubeState.FULL && access.readIfLoaded(at).orElseThrow().isAir(),"FULL loaded air remains present");
        var far=at.add(8_000_000,0,0);
        context.assertTrue(!world.isChunkLoaded(far),"unknown control starts absent");
        context.assertTrue(access.cubeState(access.cubeAt(far))==VolumetricWorldAccess.CubeState.UNAVAILABLE && !world.isChunkLoaded(far),"metadata never materializes a remote column");
        var outside=new BlockPos(at.getX(),bounds.topYExclusive(),at.getZ());
        context.assertTrue(access.cubeState(access.cubeAt(outside))==VolumetricWorldAccess.CubeState.OUTSIDE_HEIGHT,"outside height is distinguishable from unknown");
        context.assertTrue(access.cubeState(new com.terminaldetector.drmd.d6.D6ChunkPos(Integer.MAX_VALUE,at.getY()>>4,0))==VolumetricWorldAccess.CubeState.UNREPRESENTABLE,"overflowing horizontal cube cannot wrap to loaded space");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void signedCubeAddressesDoNotUseTheVanillaTwelveBitYCodec(TestContext context) {
        VolumetricWorldAccess access=WorldAccess.of(context.getWorld());
        var negative=access.cubeAt(new BlockPos(-17,-17,-1));
        context.assertTrue(negative.x()==-2 && negative.y()==-2 && negative.z()==-1,"all signed axes use floor division");
        var high=access.cubeAt(new BlockPos(9,2301,10));
        var low=access.cubeAt(new BlockPos(9,-1795,10));
        context.assertTrue(high.y()==143 && low.y()==-113 && !high.equals(low) && high.asLong()!=low.asLong(),"addresses remain distinct across a 4096-block Y difference without querying either cell");
        context.complete();
    }
}

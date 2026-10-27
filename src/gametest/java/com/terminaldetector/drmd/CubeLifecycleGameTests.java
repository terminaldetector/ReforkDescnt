package com.terminaldetector.drmd;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public final class CubeLifecycleGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void subscriptionHasOneInitialStateAndOneIdempotentClose(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(3,4,3));
        var events=new ArrayList<CubeLifecycle.Event>();
        var subscription=WorldAccess.of(world).watchCube(D6ChunkPos.ofBlock(at.getX(),at.getY(),at.getZ()),events::add).orElseThrow();
        context.assertTrue(events.size()==1 && events.getFirst().change()==CubeLifecycle.Change.AVAILABLE && events.getFirst().available(),"initial native metadata read");
        subscription.close();subscription.close();
        context.assertTrue(events.size()==2 && events.getLast().change()==CubeLifecycle.Change.CLOSED
            && events.getLast().session()==events.getFirst().session(),"idempotent close within one world session");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void observingUnknownVolumeNeverCreatesItsColumn(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(3,4,3)).add(8_000_000,0,0);
        var events=new ArrayList<CubeLifecycle.Event>();
        var subscription=WorldAccess.of(world).watchCube(D6ChunkPos.ofBlock(at.getX(),at.getY(),at.getZ()),events::add).orElseThrow();
        context.assertTrue(!world.isChunkLoaded(at) && events.size()==1 && !events.getFirst().available(),"unavailable metadata without a ticket");
        subscription.close();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void interestBudgetRejectsExcessAndRecoversAfterClose(TestContext context) {
        var world=context.getWorld();var handles=new ArrayList<CubeLifecycle.Subscription>();
        for(int i=0;i<CubeLifecycle.MAX_CUBES;i++)handles.add(WorldAccess.of(world).watchCube(new D6ChunkPos(10000+i,0,10000),event->{}).orElseThrow());
        context.assertTrue(WorldAccess.of(world).watchCube(new D6ChunkPos(12000,0,10000),event->{}).isEmpty(),"bounded interest cells");
        for(var handle:handles)handle.close();
        var again=WorldAccess.of(world).watchCube(new D6ChunkPos(12000,0,10000),event->{});
        context.assertTrue(again.isPresent(),"closed cells release their budget");again.orElseThrow().close();context.complete();
    }
}

package com.terminaldetector.drmd.world.store;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceIngestNativeRefreshQueueTest {
    @Test void repeatedCubeArrivalsCoalesceWithoutStarvingOtherColumns() {
        var queue=new SurfaceIngest.NativeRefreshQueue();queue.offer(1);queue.offer(2);
        assertEquals(List.of(1L),queue.rotate(1));queue.offer(1);
        assertEquals(List.of(2L),queue.rotate(1));assertEquals(2,queue.size());
        assertEquals(0,queue.evictions());
    }
    @Test void metadataAttemptsRetainUnknownColumnsUntilActualAcknowledgement() {
        var queue=new SurfaceIngest.NativeRefreshQueue();queue.offer(1);queue.offer(2);
        for(int i=0;i<10;i++)assertEquals(2,queue.rotate(4).size());
        assertEquals(2,queue.size());queue.acknowledge(1);
        assertEquals(List.of(2L),queue.rotate(4));assertEquals(1,queue.size());
    }
    @Test void oneTickProbeBudgetNeverRepeatsASmallQueueOrConsumesMoreThanFour() {
        var queue=new SurfaceIngest.NativeRefreshQueue();queue.offer(1);
        assertEquals(List.of(1L),queue.rotate(4));assertEquals(List.of(),queue.rotate(0));
        for(long i=2;i<=7;i++)queue.offer(i);
        assertEquals(List.of(1L,2L,3L,4L),queue.rotate(4));
        assertEquals(List.of(5L,6L,7L,1L),queue.rotate(4));assertEquals(7,queue.size());
    }
    @Test void overloadedNativeInterestKeeps4096KeysAndExposesOldestEviction() {
        var queue=new SurfaceIngest.NativeRefreshQueue();
        for(long i=0;i<4096;i++)queue.offer(i);
        queue.offer(4095);assertEquals(0,queue.evictions());
        queue.offer(4096);assertEquals(4096,queue.size());assertEquals(1,queue.evictions());
        var keys=queue.rotate(4097);assertEquals(4096,keys.size());
        assertFalse(keys.contains(0L));assertTrue(keys.contains(4096L));
    }
    @Test void serverCloseRemovesOldSessionKeysAndOverloadCounters() {
        var queue=new SurfaceIngest.NativeRefreshQueue();
        for(long i=0;i<=4096;i++)queue.offer(i);
        assertEquals(1,queue.evictions());queue.clear();
        assertEquals(0,queue.size());assertEquals(0,queue.evictions());assertTrue(queue.rotate(4).isEmpty());
        queue.offer(42);assertEquals(List.of(42L),queue.rotate(4));
    }
}

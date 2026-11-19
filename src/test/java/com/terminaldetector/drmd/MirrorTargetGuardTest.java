package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.MirrorTargetGuard;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class MirrorTargetGuardTest {
    @Test void aDriverRefusalRollsBackOnlyItsOwnedTargetAndAlwaysRestoresTheOuterFrame(){
        var guard=new MirrorTargetGuard();var cleanup=new AtomicInteger();var restored=new AtomicInteger();
        assertNull(guard.acquire(640,360,4096,()->{throw new IllegalStateException("driver refused depth attachment");},cleanup::incrementAndGet,restored::incrementAndGet));
        assertEquals(1,cleanup.get());assertEquals(1,restored.get());assertTrue(guard.reason().contains("driver refused depth attachment"));
    }
    @Test void repeatedFramesDoNotRepeatedlyAllocateARefusedTargetButResizeOrWorldResetCanRetry(){
        var guard=new MirrorTargetGuard();var calls=new AtomicInteger();
        java.util.function.Supplier<String> reject=()->{calls.incrementAndGet();throw new IllegalStateException("refused");};
        assertNull(guard.acquire(640,360,4096,reject,()->{},()->{}));
        assertNull(guard.acquire(640,360,4096,reject,()->{},()->{}));assertEquals(1,calls.get());
        assertEquals("new size",guard.acquire(320,180,4096,()->"new size",()->fail("no rollback on success"),()->{}));
        guard.reset();assertEquals("new world",guard.acquire(640,360,4096,()->"new world",()->{},()->{}));
    }
    @Test void zeroAreaAndOverflowSizedTargetsNeverReachTheAllocator(){
        var guard=new MirrorTargetGuard();var calls=new AtomicInteger();java.util.function.Supplier<String> allocate=()->{calls.incrementAndGet();return "target";};
        assertNull(guard.acquire(0,720,4096,allocate,()->{},()->{}));
        assertNull(guard.acquire(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,allocate,()->{},()->{}));
        assertNull(guard.acquire(5000,10,4096,allocate,()->{},()->{}));assertEquals(0,calls.get());
    }
    @Test void failedCleanupDoesNotSkipOuterRestorationOrPretendTheReflectionSucceeded(){
        var guard=new MirrorTargetGuard();var restored=new AtomicInteger();
        assertNull(guard.acquire(1280,720,4096,()->{throw new IllegalArgumentException("incomplete");},()->{throw new IllegalStateException("cleanup");},restored::incrementAndGet));
        assertEquals(1,restored.get());assertTrue(guard.reason().contains("cleanup"));
    }
}

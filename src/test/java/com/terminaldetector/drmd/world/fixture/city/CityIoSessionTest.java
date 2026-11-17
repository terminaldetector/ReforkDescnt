package com.terminaldetector.drmd.world.fixture.city;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CityIoSessionTest {
    @TempDir Path directory;
    private static final CityBlueprint.Limits BOUNDS=new CityBlueprint.Limits(-64,512,-1024,1024,-1024,1024);
    private static final CityIoSession.Context CONTEXT=new CityIoSession.Context(new UUID(80,81),"minecraft:overworld",777,BOUNDS,CityManifest.Backend.STOCK);
    private static final CityIoSession.Draft DRAFT=new CityIoSession.Draft(CONTEXT,717,new CityBlueprint.Point(0,64,0));
    private static <T>T await(CompletableFuture<T> future)throws Exception{return future.get(10,TimeUnit.SECONDS);}
    private static void cleanup(CityIoSession io)throws Exception{try{await(io.requestClose(CityBatchJournal.Disposition.PAUSED));}catch(ExecutionException expectedFailure){/* body asserts failed futures; cleanup still releases ownership */}}
    private static CityBatchJournal.Batch first(CityIoSession.Opened opened){
        var cell=opened.plan().cells().getFirst();return new CityBatchJournal.Batch(0,0,1,List.of(new CityBatchJournal.Change(cell.position(),"minecraft:stone",cell.state())));
    }
    private static void waitFor(CountDownLatch latch)throws IOException{
        try{if(!latch.await(10,TimeUnit.SECONDS))throw new IOException("test-owned I/O barrier timed out");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
    }

    @Test void workerOwnsAllFilesystemOperationsAndCloseReleasesTheSaveLease()throws Exception{
        Thread caller=Thread.currentThread();var seen=new java.util.concurrent.CopyOnWriteArrayList<Thread>();
        var io=new CityIoSession(directory,CONTEXT,DRAFT,operation->{assertNotSame(caller,Thread.currentThread());seen.add(Thread.currentThread());});
        CityIoSession.Opened opened;
        try{opened=await(io.opened());await(io.prepare(first(opened)));await(io.commit(0));}
        finally{cleanup(io);}
        assertTrue(seen.size()>=3);assertEquals(1,seen.stream().distinct().count());
        try(var lease=CitySaveIdentity.openExisting(directory)){assertEquals(opened.manifest().identity().saveId(),lease.saveId());}
    }
    @Test void secondPlanCannotReplaceTheSinglePersistentSlot()throws Exception{
        var io=CityIoSession.create(directory,DRAFT);await(io.opened());cleanup(io);
        Path manifest=directory.resolve("drmd/city/current.manifest"),journal=directory.resolve("drmd/city/current.journal");
        byte[] m=Files.readAllBytes(manifest),j=Files.readAllBytes(journal);
        var second=CityIoSession.create(directory,new CityIoSession.Draft(CONTEXT,999,new CityBlueprint.Point(0,64,0)));
        try{assertThrows(ExecutionException.class,()->await(second.opened()));assertThrows(ExecutionException.class,()->await(second.closed()));}
        finally{cleanup(second);}
        assertArrayEquals(m,Files.readAllBytes(manifest));assertArrayEquals(j,Files.readAllBytes(journal));
    }
    @Test void existingOpenDoesNotProvisionAndChangedOwnerOrBoundsCannotAcquireTheJob()throws Exception{
        var missing=CityIoSession.openExisting(directory,CONTEXT);
        try{assertThrows(ExecutionException.class,()->await(missing.opened()));}finally{cleanup(missing);}
        assertFalse(Files.exists(directory.resolve("drmd")));
        var created=CityIoSession.create(directory,DRAFT);await(created.opened());cleanup(created);
        for(var invalid:List.of(new CityIoSession.Context(UUID.randomUUID(),CONTEXT.dimension(),CONTEXT.worldSeed(),BOUNDS,CONTEXT.backend()),
            new CityIoSession.Context(CONTEXT.owner(),CONTEXT.dimension(),CONTEXT.worldSeed(),new CityBlueprint.Limits(-64,528,-1024,1024,-1024,1024),CONTEXT.backend()))){
            var io=CityIoSession.openExisting(directory,invalid);try{assertThrows(ExecutionException.class,()->await(io.opened()));}finally{cleanup(io);}
        }
    }
    @Test void prepareAndCommitPrefixReopenAsDataWithoutAutomaticExecution()throws Exception{
        var io=CityIoSession.create(directory,DRAFT);var opened=await(io.opened());var batch=first(opened);
        try{await(io.prepare(batch));await(io.commit(0));}finally{cleanup(io);}
        var restored=CityIoSession.openExisting(directory,CONTEXT);
        try{var next=await(restored.opened());assertTrue(next.restored());assertEquals(1,next.snapshot().cursor());assertEquals(1,next.snapshot().nextBatch());assertTrue(next.snapshot().pending().isEmpty());}
        finally{cleanup(restored);}
    }
    @Test void closeDrainsOnePreparedRequestThenForcesCancellationWithoutQueueGrowth()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var io=new CityIoSession(directory,CONTEXT,DRAFT,operation->{if(operation.equals("prepare")){entered.countDown();waitFor(release);}});
        var opened=await(io.opened());var pending=io.prepare(first(opened));
        try{
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertThrows(ExecutionException.class,()->await(io.commit(0)));
            var closed=io.requestClose(CityBatchJournal.Disposition.CANCELLED);assertFalse(closed.isDone());
            assertThrows(ExecutionException.class,()->await(io.control(CityBatchJournal.Disposition.PAUSED,-1)));
            release.countDown();await(pending);await(closed);
        }finally{release.countDown();cleanup(io);}
        var restored=CityIoSession.openExisting(directory,CONTEXT);
        try{var next=await(restored.opened());assertEquals(CityBatchJournal.Disposition.CANCELLED,next.snapshot().disposition());assertTrue(next.snapshot().pending().isPresent());}
        finally{cleanup(restored);}
    }
    @Test void normalCloseCannotWeakenAnAcknowledgedAfterImageFence()throws Exception{
        var io=CityIoSession.create(directory,DRAFT);var opened=await(io.opened());
        try{await(io.prepare(first(opened)));await(io.control(CityBatchJournal.Disposition.VERIFY_AFTER_ONLY,0));}finally{cleanup(io);}
        var restored=CityIoSession.openExisting(directory,CONTEXT);
        try{assertEquals(CityBatchJournal.Disposition.VERIFY_AFTER_ONLY,await(restored.opened()).snapshot().disposition());}finally{cleanup(restored);}
    }
    @Test void cancellationWhileOpeningIsDurableBeforeTheCloseAcknowledgment()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var io=new CityIoSession(directory,CONTEXT,DRAFT,operation->{if(operation.equals("open")){entered.countDown();waitFor(release);}});
        try{
            assertTrue(entered.await(10,TimeUnit.SECONDS));var closed=io.requestClose(CityBatchJournal.Disposition.CANCELLED);assertFalse(closed.isDone());
            release.countDown();await(closed);
        }finally{release.countDown();cleanup(io);}
        var restored=CityIoSession.openExisting(directory,CONTEXT);
        try{assertEquals(CityBatchJournal.Disposition.CANCELLED,await(restored.opened()).snapshot().disposition());}finally{cleanup(restored);}
    }
    @Test void failedOperationClosesOwnershipAndNeverPretendsItDrainedSuccessfully()throws Exception{
        var io=new CityIoSession(directory,CONTEXT,DRAFT,operation->{if(operation.equals("prepare"))throw new IOException("test-owned request failure");});
        var opened=await(io.opened());
        try{
            assertThrows(ExecutionException.class,()->await(io.prepare(first(opened))));assertThrows(ExecutionException.class,()->await(io.closed()));
            assertThrows(ExecutionException.class,()->await(io.commit(0)));
        }finally{cleanup(io);}
        try(var lease=CitySaveIdentity.openExisting(directory)){assertEquals(opened.manifest().identity().saveId(),lease.saveId());}
    }
}

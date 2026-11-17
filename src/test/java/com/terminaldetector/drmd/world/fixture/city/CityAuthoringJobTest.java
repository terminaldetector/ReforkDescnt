package com.terminaldetector.drmd.world.fixture.city;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

/** Temporal controls: the disk acknowledgment, world CAS and durable cursor are distinct. */
class CityAuthoringJobTest {
    private static final UUID OWNER=new UUID(101,902),OTHER=new UUID(102,903);
    private static final CityBlueprint.Plan PLAN=CityBlueprint.create(93,new CityBlueprint.Point(0,64,0),
        new CityBlueprint.Limits(-64,512,-1024,1024,-1024,1024));
    private static final CityBatchJournal.Identity ID=new CityBatchJournal.Identity(new UUID(11,12),new UUID(13,14),OWNER,"minecraft:overworld",PLAN.hash());

    private static final class World implements CityAuthoringJob.Storage {
        final Map<CityBlueprint.Point,String> cells=new HashMap<>();
        final Thread server=Thread.currentThread();boolean available=true;int reads,commits;
        CityAuthoringJob.CommitResult next=CityAuthoringJob.CommitResult.COMMITTED;
        private void thread(){assertSame(server,Thread.currentThread(),"world access escaped to I/O thread");}
        public String worldKey(){return "minecraft:overworld";}
        public boolean pageAndHaloAvailable(CityAuthoringJob.Page page){thread();return available;}
        public Optional<String> readLoaded(CityBlueprint.Point point){thread();reads++;return available?Optional.of(cells.getOrDefault(point,"minecraft:stone")):Optional.empty();}
        public CityAuthoringJob.CommitResult compareAndSet(CityBatchJournal.Batch batch){
            thread();commits++;assertTrue(batch.changes().size()<=64);
            if(!available)return CityAuthoringJob.CommitResult.UNAVAILABLE;
            for(var c:batch.changes())if(!cells.getOrDefault(c.position(),"minecraft:stone").equals(c.before()))return CityAuthoringJob.CommitResult.CONFLICT;
            if(next==CityAuthoringJob.CommitResult.COMMITTED||next==CityAuthoringJob.CommitResult.NO_CHANGE||next==CityAuthoringJob.CommitResult.NOTIFICATION_FAILED)
                for(var c:batch.changes())cells.put(c.position(),c.after());
            return next;
        }
    }
    private static final class Disk implements CityAuthoringJob.Journal {
        final List<CityBatchJournal.Batch> prepared=new ArrayList<>();final List<Long> committed=new ArrayList<>();
        final List<CityBatchJournal.Disposition> controls=new ArrayList<>();
        CompletableFuture<Void> prepareAck,commitAck,controlAck;
        public CompletableFuture<Void> prepare(CityBatchJournal.Batch batch){prepared.add(batch);return prepareAck=new CompletableFuture<>();}
        public CompletableFuture<Void> commit(long batch){committed.add(batch);return commitAck=new CompletableFuture<>();}
        public CompletableFuture<Void> control(CityBatchJournal.Disposition disposition,long batch){controls.add(disposition);return controlAck=new CompletableFuture<>();}
    }
    private static CityBatchJournal.Snapshot empty(){return new CityBatchJournal.Snapshot(ID,0,0,Optional.empty(),false);}
    private static CityAuthoringJob job(World world,Disk disk){return new CityAuthoringJob(PLAN,ID,empty(),world,disk);}
    private static CityBatchJournal.Batch prepare(CityAuthoringJob job,Disk disk){assertTrue(job.apply(OWNER));job.tick();return disk.prepared.getFirst();}

    @Test void planningAndForeignControlsNeverStartWorldOrJournalWork(){
        var w=new World();var d=new Disk();var job=job(w,d);for(int i=0;i<4;i++)job.tick();
        assertEquals(CityAuthoringJob.State.PLANNED,job.inspect().state());assertEquals(0,w.reads);assertTrue(d.prepared.isEmpty());
        assertThrows(SecurityException.class,()->job.apply(OTHER));assertThrows(SecurityException.class,()->job.pause(OTHER));
        assertThrows(SecurityException.class,()->job.resume(OTHER));assertThrows(SecurityException.class,()->job.cancel(OTHER));
        assertEquals(CityAuthoringJob.State.PLANNED,job.inspect().state());
    }
    @Test void batchIsOnePageAtMost64AndCursorWaitsForBothDurableAcknowledgments() throws InterruptedException {
        var w=new World();var d=new Disk();var job=job(w,d);var batch=prepare(job,d);
        assertFalse(batch.changes().isEmpty());assertTrue(batch.changes().size()<=64);
        var page=CityAuthoringJob.Page.of(batch.changes().getFirst().position());
        assertTrue(batch.changes().stream().allMatch(c->CityAuthoringJob.Page.of(c.position()).equals(page)));
        for(int i=0;i<3;i++)job.tick();assertEquals(0,w.commits);assertEquals(0,job.inspect().cursor());
        var worker=Thread.ofPlatform().start(()->d.prepareAck.complete(null));worker.join();assertEquals(0,w.commits);
        job.tick();assertEquals(1,w.commits);assertEquals(List.of(0L),d.committed);assertEquals(0,job.inspect().cursor());
        for(int i=0;i<3;i++)job.tick();assertEquals(1,w.commits);assertEquals(1,d.prepared.size());
        d.commitAck.complete(null);job.tick();assertEquals(batch.advance(),job.inspect().cursor());assertEquals(1,w.commits);
    }
    @Test void pauseDuringPrepareRequiresManualResumeAndCancelCannotBeResumed(){
        var w=new World();var d=new Disk();var job=job(w,d);prepare(job,d);assertTrue(job.pause(OWNER));
        d.prepareAck.complete(null);job.tick();job.tick();assertEquals(0,w.commits);assertEquals(CityAuthoringJob.State.PAUSED,job.inspect().state());
        assertFalse(job.resume(OWNER));assertEquals(List.of(CityBatchJournal.Disposition.PAUSED),d.controls);d.controlAck.complete(null);job.tick();
        assertTrue(job.resume(OWNER));job.tick();assertEquals(1,w.commits);
        var w2=new World();var d2=new Disk();var cancelled=job(w2,d2);prepare(cancelled,d2);assertTrue(cancelled.cancel(OWNER));
        d2.prepareAck.complete(null);for(int i=0;i<4;i++)cancelled.tick();assertEquals(0,w2.commits);
        assertFalse(cancelled.resume(OWNER));assertFalse(cancelled.apply(OWNER));assertEquals(CityAuthoringJob.State.CANCELLED,cancelled.inspect().state());
    }
    @Test void unloadedPageBeforeOrAfterPrepareCannotWriteOrForceLoading(){
        var w=new World();var d=new Disk();var job=job(w,d);w.available=false;job.apply(OWNER);job.tick();
        assertEquals(0,w.reads);assertEquals(0,w.commits);assertTrue(d.prepared.isEmpty());assertEquals(CityAuthoringJob.Reason.UNAVAILABLE,job.inspect().reason());
        w.available=true;assertTrue(job.resume(OWNER));job.tick();assertEquals(1,d.prepared.size());
        w.available=false;d.prepareAck.complete(null);job.tick();assertEquals(0,w.commits);assertEquals(CityAuthoringJob.State.PAUSED,job.inspect().state());
    }
    @Test void foreignOrMixedPendingStatePausesAndAllAfterAdvancesWithoutOverwriting(){
        var w=new World();var d=new Disk();var job=job(w,d);var batch=prepare(job,d);d.prepareAck.complete(null);
        assertTrue(batch.advance()>1,"mixed-state control needs more than one actual planned cell");
        var first=batch.changes().getFirst();w.cells.put(first.position(),"minecraft:diamond_block");job.tick();
        assertEquals(0,w.commits);assertEquals(CityAuthoringJob.Reason.CONFLICT,job.inspect().reason());assertTrue(d.committed.isEmpty());
        w.cells.put(first.position(),first.after());assertTrue(job.resume(OWNER));job.tick();
        assertEquals(CityAuthoringJob.State.PAUSED,job.inspect().state());assertEquals(0,w.commits);
        for(var c:batch.changes())w.cells.put(c.position(),c.after());assertTrue(job.resume(OWNER));job.tick();
        assertEquals(0,w.commits);assertEquals(List.of(0L),d.committed);d.commitAck.complete(null);job.tick();assertEquals(batch.advance(),job.inspect().cursor());
    }
    @Test void lostPrepareOrCommitAcknowledgmentsQuarantineWithoutRepeatingWorldWrites(){
        var w=new World();var d=new Disk();var job=job(w,d);prepare(job,d);d.prepareAck.completeExceptionally(new java.io.IOException("disk full"));job.tick();
        assertEquals(0,w.commits);assertEquals(CityAuthoringJob.State.QUARANTINED,job.inspect().state());assertFalse(job.resume(OWNER));
        var w2=new World();var d2=new Disk();var second=job(w2,d2);prepare(second,d2);d2.prepareAck.complete(null);second.tick();
        assertEquals(1,w2.commits);d2.commitAck.completeExceptionally(new java.io.IOException("commit force lost"));second.tick();
        assertEquals(0,second.inspect().cursor());assertEquals(CityAuthoringJob.State.QUARANTINED,second.inspect().state());
        for(int i=0;i<4;i++)second.tick();assertEquals(1,w2.commits);assertFalse(second.resume(OWNER));
        var w3=new World();var d3=new Disk();var third=job(w3,d3);prepare(third,d3);w3.next=CityAuthoringJob.CommitResult.NOTIFICATION_FAILED;
        d3.prepareAck.complete(null);third.tick();assertEquals(1,w3.commits);d3.controlAck.completeExceptionally(new java.io.IOException("verification force lost"));third.tick();
        assertEquals(CityAuthoringJob.State.QUARANTINED,third.inspect().state());assertEquals(CityBatchJournal.Disposition.PAUSED,third.inspect().durableDisposition());
        assertFalse(third.resume(OWNER));for(int i=0;i<4;i++)third.tick();assertEquals(1,w3.commits);assertEquals(1,d3.controls.size());
    }
    @Test void reopenedPendingAfterImageNeverWritesWithoutExplicitOwnerResume(){
        var w=new World();var d=new Disk();var original=job(w,d);var batch=prepare(original,d);
        for(var c:batch.changes())w.cells.put(c.position(),c.after());var recovered=new CityBatchJournal.Snapshot(ID,0,0,Optional.of(batch),false);
        var restoreDisk=new Disk();var restored=new CityAuthoringJob(PLAN,ID,recovered,w,restoreDisk);
        for(int i=0;i<3;i++)restored.tick();assertEquals(0,w.commits);assertTrue(restoreDisk.committed.isEmpty());assertFalse(restored.apply(OWNER));
        assertTrue(restored.resume(OWNER));restored.tick();assertEquals(0,w.commits);assertEquals(List.of(0L),restoreDisk.committed);
        restoreDisk.commitAck.complete(null);restored.tick();assertEquals(batch.advance(),restored.inspect().cursor());
    }
    @Test void notificationFailureKeepsPhysicalAfterImagePendingUntilManualVerification(){
        var w=new World();var d=new Disk();var job=job(w,d);var batch=prepare(job,d);w.next=CityAuthoringJob.CommitResult.NOTIFICATION_FAILED;
        d.prepareAck.complete(null);job.tick();assertEquals(1,w.commits);assertEquals(CityAuthoringJob.State.PAUSED,job.inspect().state());
        assertEquals(CityAuthoringJob.Reason.NOTIFICATION_FAILED,job.inspect().reason());assertEquals(0,job.inspect().cursor());
        assertNull(d.commitAck);assertTrue(d.committed.isEmpty());job.tick();assertEquals(1,w.commits);
        assertFalse(job.resume(OWNER));assertEquals(List.of(CityBatchJournal.Disposition.VERIFY_AFTER_ONLY),d.controls);
        d.controlAck.complete(null);job.tick();assertEquals(CityBatchJournal.Disposition.VERIFY_AFTER_ONLY,job.inspect().durableDisposition());
        for(var c:batch.changes())w.cells.put(c.position(),c.before());assertTrue(job.resume(OWNER));job.tick();
        assertEquals(CityAuthoringJob.Reason.CONFLICT,job.inspect().reason());assertEquals(1,w.commits);assertTrue(d.committed.isEmpty());
        for(var c:batch.changes())w.cells.put(c.position(),c.after());
        var first=batch.changes().getFirst();w.cells.put(first.position(),"minecraft:diamond_block");assertTrue(job.resume(OWNER));job.tick();
        assertEquals(CityAuthoringJob.Reason.CONFLICT,job.inspect().reason());assertTrue(d.committed.isEmpty());
        w.cells.put(first.position(),first.after());assertTrue(job.resume(OWNER));job.tick();assertEquals(List.of(0L),d.committed);assertEquals(1,w.commits);
        d.commitAck.complete(null);job.tick();assertEquals(batch.advance(),job.inspect().cursor());assertEquals(1,d.prepared.size());
    }
    @Test void recoveredForeignIdentityOrOffPlanPendingCellFailsBeforeAnyWorldRead(){
        var w=new World();var d=new Disk();var foreign=new CityBatchJournal.Identity(ID.saveId(),ID.jobId(),OTHER,ID.world(),PLAN.hash());
        assertThrows(IllegalArgumentException.class,()->new CityAuthoringJob(PLAN,ID,new CityBatchJournal.Snapshot(foreign,0,0,Optional.empty(),false),w,d));
        var change=new CityBatchJournal.Change(new CityBlueprint.Point(1000,64,1000),"minecraft:air","minecraft:iron_block");
        var batch=new CityBatchJournal.Batch(0,0,1,List.of(change));
        assertThrows(IllegalArgumentException.class,()->new CityAuthoringJob(PLAN,ID,new CityBatchJournal.Snapshot(ID,0,0,Optional.of(batch),false),w,d));
        assertEquals(0,w.reads);assertTrue(d.prepared.isEmpty());
        var tail=new CityAuthoringJob(PLAN,ID,new CityBatchJournal.Snapshot(ID,0,0,Optional.empty(),true),w,d);
        assertEquals(CityAuthoringJob.State.QUARANTINED,tail.inspect().state());assertFalse(tail.resume(OWNER));
    }
    @Test void finalDurableBatchCompletesWithoutSubmittingAnotherWrite(){
        int last=PLAN.cells().size()-1;var snapshot=new CityBatchJournal.Snapshot(ID,last,1000,Optional.empty(),false);
        var w=new World();var d=new Disk();var job=new CityAuthoringJob(PLAN,ID,snapshot,w,d);assertTrue(job.resume(OWNER));job.tick();
        assertEquals(1,d.prepared.getFirst().advance());d.prepareAck.complete(null);job.tick();assertEquals(1,w.commits);d.commitAck.complete(null);job.tick();
        assertEquals(CityAuthoringJob.State.COMPLETED,job.inspect().state());assertEquals(PLAN.cells().size(),job.inspect().cursor());
        for(int i=0;i<3;i++)job.tick();assertEquals(1,d.prepared.size());assertEquals(1,w.commits);
    }
    @Test void atomicBackendRejectionDoesNotCommitJournalOrRetryAutomatically(){
        var w=new World();var d=new Disk();var job=job(w,d);prepare(job,d);w.next=CityAuthoringJob.CommitResult.CONFLICT;
        d.prepareAck.complete(null);job.tick();assertEquals(1,w.commits);assertTrue(w.cells.isEmpty());assertTrue(d.committed.isEmpty());
        assertEquals(CityAuthoringJob.Reason.CONFLICT,job.inspect().reason());for(int i=0;i<3;i++)job.tick();assertEquals(1,w.commits);
        w.next=CityAuthoringJob.CommitResult.COMMITTED;assertTrue(job.resume(OWNER));job.tick();assertEquals(2,w.commits);assertEquals(List.of(0L),d.committed);
    }
    @Test void cancellationDominatesPauseAndOnlyOneControlWaitsBehindPrepare(){
        var w=new World();var d=new Disk();var job=job(w,d);prepare(job,d);assertTrue(job.pause(OWNER));assertTrue(job.cancel(OWNER));
        assertTrue(d.controls.isEmpty());assertEquals(CityBatchJournal.Disposition.PAUSED,job.inspect().durableDisposition());
        for(int i=0;i<3;i++)job.tick();assertTrue(d.controls.isEmpty());assertEquals(0,w.commits);
        d.prepareAck.complete(null);job.tick();assertEquals(List.of(CityBatchJournal.Disposition.CANCELLED),d.controls);
        assertEquals(CityAuthoringJob.State.CANCELLED,job.inspect().state());assertEquals(CityBatchJournal.Disposition.PAUSED,job.inspect().durableDisposition());
        assertFalse(job.resume(OWNER));d.controlAck.complete(null);job.tick();assertEquals(CityBatchJournal.Disposition.CANCELLED,job.inspect().durableDisposition());
        assertEquals(0,w.commits);assertEquals(1,d.controls.size());
    }
    @Test void cancellationWaitsForCommittedPrefixAcknowledgmentBeforeTerminalRecord(){
        var w=new World();var d=new Disk();var job=job(w,d);var batch=prepare(job,d);d.prepareAck.complete(null);job.tick();
        assertEquals(1,w.commits);assertTrue(job.cancel(OWNER));job.tick();assertTrue(d.controls.isEmpty());assertEquals(0,job.inspect().cursor());
        d.commitAck.complete(null);job.tick();assertEquals(batch.advance(),job.inspect().cursor());assertEquals(List.of(CityBatchJournal.Disposition.CANCELLED),d.controls);
        d.controlAck.complete(null);job.tick();assertEquals(CityBatchJournal.Disposition.CANCELLED,job.inspect().durableDisposition());
        job.tick();assertEquals(1,w.commits);assertEquals(1,d.prepared.size());
    }
    @Test void realDurableCancellationAndVerificationRestoreWithoutImplicitWorldWork(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)throws java.io.IOException {
        var w=new World();var d=new Disk();var original=job(w,d);var batch=prepare(original,d);
        for(var disposition:List.of(CityBatchJournal.Disposition.CANCELLED,CityBatchJournal.Disposition.VERIFY_AFTER_ONLY)){
            var file=directory.resolve(disposition+".journal");
            try(var writer=CityBatchJournal.create(file,ID)){writer.prepare(batch);writer.control(disposition,disposition==CityBatchJournal.Disposition.CANCELLED?-1:0);}
            CityBatchJournal.Snapshot snapshot;try(var reader=CityBatchJournal.openExisting(file,ID)){snapshot=reader.snapshot();}
            var disk=new Disk();var restored=new CityAuthoringJob(PLAN,ID,snapshot,w,disk);for(int i=0;i<3;i++)restored.tick();
            assertEquals(0,w.commits);assertTrue(disk.prepared.isEmpty());assertTrue(disk.committed.isEmpty());
            if(disposition==CityBatchJournal.Disposition.CANCELLED){assertEquals(CityAuthoringJob.State.CANCELLED,restored.inspect().state());assertFalse(restored.resume(OWNER));assertFalse(restored.apply(OWNER));}
            else{
                assertTrue(restored.resume(OWNER));restored.tick();assertEquals(CityAuthoringJob.Reason.CONFLICT,restored.inspect().reason());assertEquals(0,w.commits);
                for(var c:batch.changes())w.cells.put(c.position(),c.after());assertTrue(restored.resume(OWNER));restored.tick();assertEquals(List.of(0L),disk.committed);assertEquals(0,w.commits);
            }
        }
    }
}

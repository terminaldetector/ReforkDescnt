package com.terminaldetector.drmd.world.fixture.city;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Server-owned pure controller. Only explicit owner commands enable world work.
 * The future's completion thread never executes a world callback: tick polls it. */
public final class CityAuthoringJob {
    public enum State { PLANNED,RUNNING,PAUSED,COMPLETED,CANCELLED,QUARANTINED }
    public enum Reason { NONE,MANUAL,RECOVERED,UNAVAILABLE,CONFLICT,REJECTED,IO_FAILURE,WORLD_FAILURE,NOTIFICATION_FAILED,INCOMPLETE_JOURNAL }
    public enum Phase { IDLE,PREPARE,READY,COMMIT }
    public enum CommitResult { COMMITTED,NO_CHANGE,NOTIFICATION_FAILED,CONFLICT,UNAVAILABLE,REJECTED }
    public record Page(int x,int y,int z){
        public static Page of(CityBlueprint.Point point){return new Page(Math.floorDiv(point.x(),16),Math.floorDiv(point.y(),16),Math.floorDiv(point.z(),16));}
    }
    /** The future runtime adapter must exclude BE/unstable before-states, check
     * permissions and availability, and delegate atomic expected-state edits to
     * WorldAccess/ServerBlockEdits. These methods must never load or ticket cubes. */
    public interface Storage {
        String worldKey();
        boolean pageAndHaloAvailable(Page page);
        Optional<String> readLoaded(CityBlueprint.Point point);
        CommitResult compareAndSet(CityBatchJournal.Batch batch);
    }
    /** One bounded request at a time, serviced by the runtime's serial disk worker.
     * Successful future completion means force(true), not merely queued or written. */
    public interface Journal {
        CompletableFuture<Void> prepare(CityBatchJournal.Batch batch);
        CompletableFuture<Void> commit(long batchNumber);
    }
    public record Inspection(State state,Reason reason,int cursor,int total,long nextBatch,
                             Optional<CityBatchJournal.Batch> pending,Phase phase,boolean verifyAfterOnly){}

    private final CityBlueprint.Plan plan;
    private final CityBatchJournal.Identity identity;
    private final Storage storage;
    private final Journal journal;
    private final Thread ownerThread=Thread.currentThread();
    private State state;private Reason reason;private Phase phase;
    private int cursor;private long nextBatch;
    private CityBatchJournal.Batch pending;
    private CompletableFuture<Void> acknowledgment;
    private boolean verifyAfterOnly;

    public CityAuthoringJob(CityBlueprint.Plan plan,CityBatchJournal.Identity identity,
                            CityBatchJournal.Snapshot recovered,Storage storage,Journal journal){
        this.plan=Objects.requireNonNull(plan);this.identity=Objects.requireNonNull(identity);
        this.storage=Objects.requireNonNull(storage);this.journal=Objects.requireNonNull(journal);
        Objects.requireNonNull(recovered);
        if(!identity.equals(recovered.identity())||!identity.world().equals(storage.worldKey())||!identity.planHash().equals(plan.hash())
            ||recovered.cursor()<0||recovered.cursor()>plan.cells().size()||recovered.nextBatch()<0||recovered.nextBatch()>recovered.cursor()
            ||recovered.cursor()==0&&recovered.nextBatch()!=0)throw new IllegalArgumentException("foreign or invalid city recovery prefix");
        cursor=recovered.cursor();nextBatch=recovered.nextBatch();pending=recovered.pending().orElse(null);
        if(pending!=null)validatePending();
        phase=pending==null?Phase.IDLE:Phase.READY;
        boolean restored=cursor!=0||pending!=null;
        state=recovered.incompleteTail()?State.QUARANTINED:restored?State.PAUSED:State.PLANNED;
        reason=recovered.incompleteTail()?Reason.INCOMPLETE_JOURNAL:restored?Reason.RECOVERED:Reason.NONE;
    }

    private void validatePending(){
        if(pending.number()!=nextBatch||pending.startCursor()!=cursor||(long)cursor+pending.advance()>plan.cells().size())
            throw new IllegalArgumentException("pending city batch does not match plan prefix");
        Page page=Page.of(pending.changes().getFirst().position());
        for(int i=0;i<pending.advance();i++){
            var change=pending.changes().get(i);var planned=plan.cells().get(cursor+i);
            if(!change.position().equals(planned.position())||!change.after().equals(planned.state())||!page.equals(Page.of(change.position())))
                throw new IllegalArgumentException("pending city batch contains foreign or cross-page cells");
        }
    }
    private void serverThread(){if(Thread.currentThread()!=ownerThread)throw new IllegalStateException("city controller requires its owning server thread");}
    private void owner(UUID caller){serverThread();if(!identity.owner().equals(caller))throw new SecurityException("city job belongs to a different operator");}
    public Inspection inspect(){serverThread();return new Inspection(state,reason,cursor,plan.cells().size(),nextBatch,Optional.ofNullable(pending),phase,verifyAfterOnly);}
    public boolean apply(UUID caller){owner(caller);if(state!=State.PLANNED)return false;state=State.RUNNING;reason=Reason.NONE;return true;}
    public boolean pause(UUID caller){owner(caller);if(state!=State.RUNNING)return false;stop(Reason.MANUAL);return true;}
    public boolean resume(UUID caller){
        owner(caller);if(state!=State.PAUSED)return false;
        state=cursor==plan.cells().size()&&pending==null?State.COMPLETED:State.RUNNING;reason=Reason.NONE;return true;
    }
    /** Cancel stops future CAS, never rolls back a committed prefix or deletes a
     * prepared journal. Reopened sessions always require another explicit command. */
    public boolean cancel(UUID caller){
        owner(caller);if(state!=State.PLANNED&&state!=State.RUNNING&&state!=State.PAUSED)return false;
        state=State.CANCELLED;reason=Reason.MANUAL;return true;
    }
    private void stop(Reason why){state=State.PAUSED;reason=why;}
    private void quarantine(Reason why){state=State.QUARANTINED;reason=why;}

    /** At most one 64-cell page CAS. No loop across pages or blocking disk wait. */
    public void tick(){
        serverThread();
        if(acknowledgment!=null){
            if(!acknowledgment.isDone())return;
            try{acknowledgment.join();}catch(RuntimeException failure){acknowledgment=null;quarantine(Reason.IO_FAILURE);return;}
            acknowledgment=null;
            if(phase==Phase.COMMIT){
                cursor=pending.startCursor()+pending.advance();nextBatch++;pending=null;phase=Phase.IDLE;verifyAfterOnly=false;
                if(state==State.RUNNING&&cursor==plan.cells().size()){state=State.COMPLETED;reason=Reason.NONE;}
                return;
            }
            phase=Phase.READY;
        }
        if(state!=State.RUNNING)return;
        if(pending==null){prepareNext();return;}
        if(phase!=Phase.READY){quarantine(Reason.IO_FAILURE);return;}
        var page=Page.of(pending.changes().getFirst().position());
        CityBatchJournal.Recovery recovery;
        try{
            if(!storage.pageAndHaloAvailable(page)){stop(Reason.UNAVAILABLE);return;}
            recovery=CityBatchJournal.reconcile(pending,storage::readLoaded);
        }catch(RuntimeException rejected){stop(Reason.REJECTED);return;}
        if(recovery==CityBatchJournal.Recovery.UNAVAILABLE){stop(Reason.UNAVAILABLE);return;}
        if(recovery==CityBatchJournal.Recovery.CONFLICT){stop(Reason.CONFLICT);return;}
        if(recovery==CityBatchJournal.Recovery.ALREADY_COMMITTED){commitMarker();return;}
        if(verifyAfterOnly){stop(Reason.CONFLICT);return;}
        CommitResult result;
        try{result=Objects.requireNonNull(storage.compareAndSet(pending));}
        catch(RuntimeException ambiguous){quarantine(Reason.WORLD_FAILURE);return;}
        switch(result){
            case COMMITTED,NO_CHANGE->commitMarker();
            // State was physically committed, but publication is uncertain. Keep
            // the durable PREPARED after-image until explicit resume verifies it;
            // restart follows conservative pending-batch reconciliation. The known
            // publication failure in this session permits only an actual after-image.
            case NOTIFICATION_FAILED->{verifyAfterOnly=true;stop(Reason.NOTIFICATION_FAILED);}
            case CONFLICT->stop(Reason.CONFLICT);
            case UNAVAILABLE->stop(Reason.UNAVAILABLE);
            case REJECTED->stop(Reason.REJECTED);
        }
    }
    private void prepareNext(){
        if(cursor==plan.cells().size()){state=State.COMPLETED;reason=Reason.NONE;return;}
        Page page=Page.of(plan.cells().get(cursor).position());var changes=new ArrayList<CityBatchJournal.Change>();
        try{
            if(!storage.pageAndHaloAvailable(page)){stop(Reason.UNAVAILABLE);return;}
            for(int i=cursor;i<plan.cells().size()&&changes.size()<CityBatchJournal.MAX_CELLS;i++){
                var cell=plan.cells().get(i);if(!Page.of(cell.position()).equals(page))break;
                var actual=storage.readLoaded(cell.position());if(actual.isEmpty()){stop(Reason.UNAVAILABLE);return;}
                changes.add(new CityBatchJournal.Change(cell.position(),actual.get(),cell.state()));
            }
            pending=new CityBatchJournal.Batch(nextBatch,cursor,changes.size(),changes);
        }catch(RuntimeException rejected){stop(Reason.REJECTED);return;}
        phase=Phase.PREPARE;
        try{acknowledgment=Objects.requireNonNull(journal.prepare(pending));}
        catch(RuntimeException failure){quarantine(Reason.IO_FAILURE);}
    }
    private void commitMarker(){
        phase=Phase.COMMIT;
        try{acknowledgment=Objects.requireNonNull(journal.commit(pending.number()));}
        catch(RuntimeException failure){quarantine(Reason.IO_FAILURE);}
    }
}

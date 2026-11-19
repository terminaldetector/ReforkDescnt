package com.terminaldetector.drmd.world.fixture.city;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** One explicitly opened save lease and one serial I/O request. No Minecraft
 * imports, world references, automatic discovery or callbacks into game state. */
public final class CityIoSession implements CityAuthoringJob.Journal {
    public record Context(UUID owner,String dimension,long worldSeed,CityBlueprint.Limits bounds,CityManifest.Backend backend){
        public Context{Objects.requireNonNull(owner);Objects.requireNonNull(dimension);Objects.requireNonNull(bounds);Objects.requireNonNull(backend);}
    }
    public record Draft(Context context,long citySeed,CityBlueprint.Point anchor,CityManifest.Kind kind){
        public Draft(Context context,long citySeed,CityBlueprint.Point anchor){this(context,citySeed,anchor,CityManifest.Kind.CITY);}
        public Draft{Objects.requireNonNull(context);Objects.requireNonNull(anchor);Objects.requireNonNull(kind);}
    }
    public record Opened(CityManifest manifest,CityBlueprint.Plan plan,CityBatchJournal.Snapshot snapshot,boolean restored){}
    @FunctionalInterface interface Probe { void before(String operation)throws IOException; }
    private record Operation(String kind,CityBatchJournal.Batch batch,long number,CityBatchJournal.Disposition disposition,CompletableFuture<Void> result){}

    private final Object gate=new Object();
    private final Path root;private final Context context;private final Draft draft;private final Probe probe;
    private final CompletableFuture<Opened> opened=new CompletableFuture<>();
    private final CompletableFuture<Void> closed=new CompletableFuture<>();
    private Operation pending;private boolean outstanding=true,closing,finishing;
    private CityBatchJournal.Disposition closeIntent=CityBatchJournal.Disposition.PAUSED,finalDisposition;
    private Throwable failure;
    private volatile CityBatchJournal.Snapshot finalSnapshot;

    public static CityIoSession create(Path saveRoot,Draft draft){return new CityIoSession(saveRoot,draft.context(),draft,operation->{});}
    public static CityIoSession openExisting(Path saveRoot,Context context){return new CityIoSession(saveRoot,context,null,operation->{});}
    CityIoSession(Path saveRoot,Context context,Draft draft,Probe probe){
        root=Objects.requireNonNull(saveRoot);this.context=Objects.requireNonNull(context);this.draft=draft;this.probe=Objects.requireNonNull(probe);
        var worker=new Thread(this::run,"drmd-city-io");worker.setDaemon(false);worker.start();
    }
    public CompletableFuture<Opened> opened(){return opened;}
    public CompletableFuture<Void> closed(){return closed;}
    public Optional<CityBatchJournal.Snapshot> finalSnapshot(){return Optional.ofNullable(finalSnapshot);}
    @Override public CompletableFuture<Void> prepare(CityBatchJournal.Batch batch){return submit(new Operation("prepare",Objects.requireNonNull(batch),batch.number(),null,new CompletableFuture<>()));}
    @Override public CompletableFuture<Void> commit(long number){return submit(new Operation("commit",null,number,null,new CompletableFuture<>()));}
    @Override public CompletableFuture<Void> control(CityBatchJournal.Disposition disposition,long number){return submit(new Operation("control",null,number,Objects.requireNonNull(disposition),new CompletableFuture<>()));}
    private CompletableFuture<Void> submit(Operation operation){
        synchronized(gate){
            if(closing||finishing||outstanding||!opened.isDone()||opened.isCompletedExceptionally())
                return CompletableFuture.failedFuture(new IOException("city I/O is busy, closing or unavailable"));
            outstanding=true;pending=operation;gate.notifyAll();return operation.result();
        }
    }
    /** Stops admissions immediately. The worker drains the existing operation,
     * forces terminal intent and closes its own files; the caller never waits. */
    public CompletableFuture<Void> requestClose(CityBatchJournal.Disposition intent){
        Objects.requireNonNull(intent);
        if(intent==CityBatchJournal.Disposition.VERIFY_AFTER_ONLY)throw new IllegalArgumentException("close intent is pause or cancel; verification uses a batch-bound control");
        synchronized(gate){
            if(finishing){
                if(intent==CityBatchJournal.Disposition.CANCELLED&&finalDisposition!=CityBatchJournal.Disposition.CANCELLED)
                    return CompletableFuture.failedFuture(new IOException("already closing without durable cancellation"));
                return closed;
            }
            closing=true;if(intent==CityBatchJournal.Disposition.CANCELLED)closeIntent=intent;
            gate.notifyAll();return closed;
        }
    }
    private void run(){
        CitySaveIdentity.Lease lease=null;CityBatchJournal journal=null;
        try{
            probe.before("open");lease=draft==null?CitySaveIdentity.openExisting(root):CitySaveIdentity.create(root);
            CityManifest manifest;CityBlueprint.Plan plan;
            if(draft!=null){
                for(String name:List.of("current.manifest","current.journal","current.manifest.pending","current.journal.pending"))
                    if(Files.exists(lease.directory().resolve(name),LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("Fixed city slot already exists; inspect/resume/cancel its data. No archive or overwrite is performed.");
                plan=draft.kind().create(draft.citySeed(),draft.anchor(),context.bounds());
                var identity=new CityBatchJournal.Identity(lease.saveId(),UUID.randomUUID(),context.owner(),context.dimension(),plan.hash());
                manifest=CityManifest.fromPlan(identity,context.worldSeed(),context.backend(),plan,draft.kind());
                manifest.writeNew(lease.directory().resolve("current.manifest"));
                journal=CityBatchJournal.create(lease.directory().resolve("current.journal"),identity);
            }else{
                manifest=CityManifest.read(lease.directory().resolve("current.manifest"));
                manifest.requireContext(lease.saveId(),context.dimension(),context.worldSeed(),context.bounds(),context.backend());
                if(!manifest.identity().owner().equals(context.owner()))throw new IOException("city job belongs to another operator");
                plan=manifest.regenerate();journal=CityBatchJournal.openExisting(lease.directory().resolve("current.journal"),manifest.identity());
            }
            var ready=new Opened(manifest,plan,journal.snapshot(),draft==null);
            synchronized(gate){outstanding=false;}
            opened.complete(ready);
            while(true){
                Operation operation;
                synchronized(gate){
                    while(pending==null&&!closing)gate.wait();
                    if(pending==null)break;operation=pending;pending=null;
                }
                try{
                    probe.before(operation.kind());
                    switch(operation.kind()){
                        case "prepare"->journal.prepare(operation.batch());
                        case "commit"->journal.commit(operation.number());
                        case "control"->journal.control(operation.disposition(),operation.number());
                        default->throw new IOException("unknown city I/O operation");
                    }
                    synchronized(gate){outstanding=false;}
                    operation.result().complete(null);
                }catch(Throwable bad){
                    synchronized(gate){failure=bad;closing=true;outstanding=false;}
                    operation.result().completeExceptionally(bad);break;
                }
            }
        }catch(Throwable bad){
            if(bad instanceof InterruptedException)Thread.currentThread().interrupt();
            synchronized(gate){failure=bad;closing=true;outstanding=false;}
            opened.completeExceptionally(bad);
        }finally{
            if(journal!=null){
                try{
                    while(true){
                        CityBatchJournal.Disposition intent;
                        synchronized(gate){intent=closeIntent;}
                        if(journal.snapshot().disposition()!=CityBatchJournal.Disposition.CANCELLED||intent==CityBatchJournal.Disposition.CANCELLED)
                            journal.control(intent,-1);
                        synchronized(gate){
                            if(intent==closeIntent){finalSnapshot=journal.snapshot();finalDisposition=finalSnapshot.disposition();finishing=true;break;}
                        }
                    }
                }catch(Throwable bad){recordFailure(bad);}
                try{journal.close();}catch(IOException bad){recordFailure(bad);}
            }
            if(lease!=null)try{lease.close();}catch(IOException bad){recordFailure(bad);}
            Throwable bad;
            synchronized(gate){closing=true;finishing=true;outstanding=false;bad=failure;}
            if(bad==null)closed.complete(null);else closed.completeExceptionally(bad);
        }
    }
    private void recordFailure(Throwable bad){synchronized(gate){if(failure==null)failure=bad;else if(failure!=bad)failure.addSuppressed(bad);}}
}

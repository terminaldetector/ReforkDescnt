package com.terminaldetector.drmd.world.fixture.city;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Server-owned bridge. I/O inputs/results remain JDK-only; this object is never
 * captured by a worker callback. All acknowledgments are polled after owner guards. */
public final class CityRuntimeSession {
    public enum State { PREPARING,READY,RESTORED_PAUSED,RUNNING,PAUSED,CLOSING,CLOSED,FAILED }
    public record Inspection(State state,String detail,String sterileLimit,Optional<CityManifest> manifest,
                             Optional<CityAuthoringJob.Inspection> job,Optional<CityWorldStorage.Diagnostic> storage,
                             Optional<CityBatchJournal.Snapshot> closedSnapshot){}
    private final Thread serverThread=Thread.currentThread();private final UUID ownerId;private final CityIoSession io;
    private ServerPlayerEntity owner;private ServerWorld world;
    private com.terminaldetector.drmd.world.planet.AuthoredOrbitalCatalogue.Certification orbitalCertificate;
    private CityBlueprint.Plan orbitalPlan;
    private CityManifest manifest;private CityWorldStorage storage;private CityAuthoringJob job;
    private State state=State.PREPARING;private String detail="Preparing an inert manual plan; no world writes";
    private CompletableFuture<Void> closeAck;private CityBatchJournal.Disposition closeIntent=CityBatchJournal.Disposition.PAUSED;
    private boolean restoredOnly,pilotSurfacePublished;private long lastTick=Long.MIN_VALUE;
    private CityAuthoringJob.Inspection lastJob;private CityWorldStorage.Diagnostic lastStorage;

    public static CityRuntimeSession plan(ServerPlayerEntity owner,long citySeed,CityBlueprint.Point anchor){
        return plan(owner,citySeed,anchor,CityManifest.Kind.CITY);
    }
    public static CityRuntimeSession plan(ServerPlayerEntity owner,long citySeed,CityBlueprint.Point anchor,CityManifest.Kind kind){
        var context=CityWorldStorage.capture(owner);CityWorldStorage.requireHaloEnvelope(context,anchor);
        var root=owner.getServerWorld().getServer().getSavePath(WorldSavePath.ROOT);
        return new CityRuntimeSession(owner,CityIoSession.create(root,new CityIoSession.Draft(context,citySeed,anchor,kind)));
    }
    public static CityRuntimeSession openExisting(ServerPlayerEntity owner){
        var context=CityWorldStorage.capture(owner);var root=owner.getServerWorld().getServer().getSavePath(WorldSavePath.ROOT);
        return new CityRuntimeSession(owner,CityIoSession.openExisting(root,context));
    }
    /** Package-private injection uses the same actual runtime/storage on a test-owned
     * filesystem root; there is no production command to redirect the save root. */
    CityRuntimeSession(ServerPlayerEntity owner,CityIoSession io){
        this.owner=Objects.requireNonNull(owner);world=owner.getServerWorld();ownerId=owner.getUuid();this.io=Objects.requireNonNull(io);
    }
    private void mainThread(){if(Thread.currentThread()!=serverThread)throw new IllegalStateException("city runtime requires server thread");}
    public UUID ownerId(){return ownerId;}
    public boolean settled(){mainThread();return state==State.CLOSED||state==State.FAILED&&io.closed().isDone();}
    private boolean eligible(){return owner!=null&&owner.getWorld()==world&&CityWorldStorage.eligible(owner);}
    private void caller(UUID id){mainThread();if(!ownerId.equals(id))throw new SecurityException("city job belongs to another operator");}
    private boolean contextStillMatches(){
        if(!eligible())return false;
        if(manifest==null)return true;
        try{
            var actual=CityWorldStorage.capture(owner);manifest.requireContext(manifest.identity().saveId(),actual.dimension(),actual.worldSeed(),actual.bounds(),actual.backend());return true;
        }catch(IllegalArgumentException changed){detail=changed.getMessage();return false;}
    }
    public Inspection inspect(){
        mainThread();return new Inspection(state,detail,CityWorldStorage.STERILE_LIMIT,Optional.ofNullable(manifest),
            job==null?Optional.ofNullable(lastJob):Optional.of(job.inspect()),storage==null?Optional.ofNullable(lastStorage):Optional.of(storage.diagnostic()),io.finalSnapshot());
    }
    public void tick(){
        mainThread();
        // Stop comes before polling a completed PREPARED ack that could enable CAS.
        if(closeAck==null&&state!=State.CLOSED&&state!=State.FAILED&&!contextStillMatches())beginClose(CityBatchJournal.Disposition.PAUSED,"Operator or world context unavailable; explicit reopen/resume required");
        if(closeAck!=null){
            if(!closeAck.isDone())return;
            try{closeAck.join();state=State.CLOSED;detail="Closed with durable "+io.finalSnapshot().map(s->s.disposition().name()).orElse("no plan");}
            catch(RuntimeException failure){state=State.FAILED;detail="City I/O close failed; data retained for review: "+cause(failure);}
            detachPhysicalState();return;
        }
        if(state==State.CLOSED||state==State.FAILED)return;
        long tick=world.getServer().getTicks();if(lastTick==tick)return;lastTick=tick;
        if(job==null){
            if(!io.opened().isDone())return;
            try{
                var opened=io.opened().join();manifest=opened.manifest();restoredOnly=opened.restored();
                if(!contextStillMatches()){beginClose(CityBatchJournal.Disposition.PAUSED,"World context changed during plan preparation");return;}
                CityWorldStorage.requireHaloEnvelope(CityWorldStorage.capture(owner),opened.plan().anchor());
                if(manifest.kind()!=CityManifest.Kind.CITY)orbitalPlan=opened.plan();
                storage=new CityWorldStorage(owner,manifest,opened.plan());job=new CityAuthoringJob(opened.plan(),manifest.identity(),opened.snapshot(),storage,io);
                state=restoredOnly?State.RESTORED_PAUSED:State.READY;detail=restoredOnly?"Restored inert data; manual resume required":"Plan ready; inspect then apply explicitly";
            }catch(RuntimeException failure){beginClose(CityBatchJournal.Disposition.PAUSED,"City preparation/open failed; original data retained: "+cause(failure));}
            return;
        }
        job.tick();updateState();
        if(orbitalPlan!=null&&job.inspect().state()==CityAuthoringJob.State.COMPLETED){
            if(manifest.kind()==CityManifest.Kind.ORBITAL_FIELD){
                if(orbitalCertificate==null)orbitalCertificate=com.terminaldetector.drmd.world.planet.AuthoredOrbitalCatalogue.begin(world,manifest,orbitalPlan);
                orbitalCertificate.tick(world);detail+="; "+orbitalCertificate.detail();
            }else if(!pilotSurfacePublished){
                com.terminaldetector.drmd.world.store.SurfaceIngest.completedOrbitalPilot(world,orbitalPlan);
                pilotSurfacePublished=true;
            }
        }
    }
    private void updateState(){
        var snapshot=job.inspect();
        state=switch(snapshot.state()){
            case PLANNED->restoredOnly?State.RESTORED_PAUSED:State.READY;
            case RUNNING->State.RUNNING;
            case PAUSED->State.PAUSED;
            case CANCELLED->State.PAUSED;
            case COMPLETED->State.PAUSED;
            case QUARANTINED->State.FAILED;
        };
        detail="Authoring "+snapshot.state()+" "+snapshot.cursor()+"/"+snapshot.total()+"; "+snapshot.reason();
        var diagnostic=storage.diagnostic();if(diagnostic.reason()!=CityWorldStorage.Reason.NONE)detail+="; "+diagnostic.reason()+": "+diagnostic.detail();
        if(snapshot.state()==CityAuthoringJob.State.QUARANTINED)beginClose(CityBatchJournal.Disposition.PAUSED,detail+"; I/O quarantined");
    }
    public boolean apply(UUID id){caller(id);if(state!=State.READY||restoredOnly||!contextStillMatches()||job==null)return false;boolean accepted=job.apply(id);if(accepted)updateState();return accepted;}
    public boolean resume(UUID id){
        caller(id);if(closeAck!=null||!contextStillMatches()||job==null)return false;
        boolean accepted=restoredOnly&&job.inspect().state()==CityAuthoringJob.State.PLANNED?job.apply(id):job.resume(id);
        if(accepted){restoredOnly=false;updateState();}return accepted;
    }
    public boolean pause(UUID id){caller(id);if(closeAck!=null||job==null)return false;boolean accepted=job.pause(id);if(accepted)updateState();return accepted;}
    public boolean cancel(UUID id){caller(id);if(closeAck!=null||state==State.CLOSED)return false;if(job!=null)job.cancel(id);beginClose(CityBatchJournal.Disposition.CANCELLED,"Stopped; persisting terminal cancellation");return true;}
    /** Read-only guide after actual placement; only actual owner position and loaded air can advance. */
    public String route(UUID id){
        caller(id);
        if(!contextStillMatches()||manifest==null||manifest.kind()!=CityManifest.Kind.ORBITAL_PILOT||job==null||job.inspect().state()!=CityAuthoringJob.State.COMPLETED)
            throw new IllegalStateException("Complete an explicitly applied orbital pilot before starting its route");
        var memory=OrbitalRouteMemory.of(world);var routeContext=OrbitalRouteMemory.Context.of(manifest);var gates=OrbitalRouteBlueprint.gates(manifest.anchor());
        var route=memory.progress(routeContext,gates);
        var next=route.next();var at=next.position();var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);boolean clear=true;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++){
            var pos=new net.minecraft.util.math.BlockPos(at.x()+x,at.y()+y,at.z()+z);
            var cell=access.readIfLoaded(pos);if(cell.isEmpty()||!cell.get().isAir())clear=false;
        }
        var position=new OrbitalRouteProgress.Position(owner.getX(),owner.getY(),owner.getZ());
        var progress=memory.observe(routeContext,gates,position,world.getTime(),clear);route=memory.progress(routeContext,gates);
        if(progress.complete())return "Orbital route complete: "+progress.passed()+" gates, "+progress.elapsedTicks()+" actual world ticks. Saved for this owner and original job; no reward.";
        next=route.next();
        return "Orbital route "+progress.passed()+"/"+progress.total()+"; next "+next.id()+" "+next.position()+"; distance "+Math.round(route.distance(position))
            +"m; elapsed "+progress.elapsedTicks()+" ticks"+(clear?"":"; current gate blocked/unavailable")+". Fly/walk normally, then use route again.";
    }
    public void ownerDisconnected(UUID id){mainThread();if(ownerId.equals(id))beginClose(CityBatchJournal.Disposition.PAUSED,"Operator disconnected; explicit reopen/resume required");}
    public void stop(){mainThread();beginClose(CityBatchJournal.Disposition.PAUSED,"Server stopping; asynchronous durable drain");detachPhysicalState();}
    private void beginClose(CityBatchJournal.Disposition intent,String message){
        if(state==State.CLOSED)return;
        if(job!=null&&job.inspect().state()==CityAuthoringJob.State.RUNNING)job.pause(ownerId);
        if(intent==CityBatchJournal.Disposition.CANCELLED)closeIntent=intent;
        if(closeAck==null||intent==CityBatchJournal.Disposition.CANCELLED){closeAck=io.requestClose(closeIntent);state=State.CLOSING;detail=message;detachPhysicalState();}
    }
    private void detachPhysicalState(){
        if(orbitalCertificate!=null)orbitalCertificate.cancel("Author session closed during certification; no proxy published");
        orbitalPlan=null;
        if(job!=null)lastJob=job.inspect();if(storage!=null)lastStorage=storage.diagnostic();
        owner=null;world=null;storage=null;job=null;
    }
    private static String cause(RuntimeException failure){Throwable root=failure;while(root.getCause()!=null)root=root.getCause();return root.getClass().getSimpleName()+": "+root.getMessage();}
}

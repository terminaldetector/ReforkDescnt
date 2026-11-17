package com.terminaldetector.drmd.world.fixture.city;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.*;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import java.util.*;

/** Strict sterile fixture adapter. It owns no tickets and never asks for generation. */
public final class CityWorldStorage implements CityAuthoringJob.Storage {
    public static final String STERILE_LIMIT="Only loaded sterile air and inert full blocks are supported. Grass, falling/attached/reactive blocks and block entities pause authoring; no fallback replacement.";
    public enum Reason { NONE,OWNER_UNAVAILABLE,CONTEXT_CHANGED,UNAVAILABLE,UNSUPPORTED_STATE,ACTOR_BLOCKED,PERMISSION,BUDGET,CONFLICT,BACKEND_FAILED }
    public record Diagnostic(Reason reason,String detail,int lastSnapshots,int lastBatch,int casCalls){}
    private static final Comparator<CityBlueprint.Point> ORDER=Comparator.comparingInt((CityBlueprint.Point p)->Math.floorDiv(p.y(),16))
        .thenComparingInt(p->Math.floorDiv(p.z(),16)).thenComparingInt(p->Math.floorDiv(p.x(),16)).thenComparing(Comparator.naturalOrder());
    private final ServerWorld world;private final ServerPlayerEntity owner;private final CityManifest manifest;private final CityBlueprint.Plan plan;
    private final WorldAccess access;private final Map<BlockPos,Optional<ServerBlockEdits.Cell>> cache=new HashMap<>();
    private Reason reason=Reason.NONE;private String detail="";private int lastSnapshots,lastBatch,casCalls;private long phaseTick=Long.MIN_VALUE;
    private CityAuthoringJob.Page page;
    private long lastCasTick=Long.MIN_VALUE;

    public CityWorldStorage(ServerPlayerEntity owner,CityManifest manifest,CityBlueprint.Plan plan){
        this.owner=Objects.requireNonNull(owner);world=owner.getServerWorld();this.manifest=Objects.requireNonNull(manifest);this.plan=Objects.requireNonNull(plan);access=WorldAccess.of(world);
        serverThread();if(!owner.getUuid().equals(manifest.identity().owner())||!plan.hash().equals(manifest.identity().planHash()))throw new IllegalArgumentException("foreign city operator or plan");
        requireContext();
    }
    private void serverThread(){if(!world.getServer().isOnThread())throw new IllegalStateException("city storage requires server thread");}
    public static boolean eligible(ServerPlayerEntity player){
        return player!=null&&player.isAlive()&&!player.isRemoved()&&!player.isSpectator()&&player.isCreative()&&player.hasPermissionLevel(2)
            &&player.getAbilities().allowModifyWorld&&player.networkHandler!=null&&player.getServerWorld().getServer().getPlayerManager().getPlayer(player.getUuid())==player;
    }
    public static CityIoSession.Context capture(ServerPlayerEntity player){
        var world=player.getServerWorld();if(!world.getServer().isOnThread()||!eligible(player))throw new IllegalArgumentException("connected Creative operator permission2 required");
        var border=world.getWorldBorder();
        int minX=(int)Math.max(-29_999_984L,Math.ceil(border.getBoundWest())),maxX=(int)Math.min(29_999_983L,Math.floor(border.getBoundEast())-1);
        int minZ=(int)Math.max(-29_999_984L,Math.ceil(border.getBoundNorth())),maxZ=(int)Math.min(29_999_983L,Math.floor(border.getBoundSouth())-1);
        var bounds=new CityBlueprint.Limits(world.getBottomY(),world.getTopY(),minX,maxX,minZ,maxZ);
        var backend=switch(WorldAccess.of(world).backend()){
            case STOCK->CityManifest.Backend.STOCK;case NATIVE_CUBIC->CityManifest.Backend.NATIVE_CUBIC;
            case UNSUPPORTED->throw new IllegalArgumentException("unsupported cubic backend; no column fallback");
        };
        return new CityIoSession.Context(player.getUuid(),world.getRegistryKey().getValue().toString(),world.getSeed(),bounds,backend);
    }
    public static void requireHaloEnvelope(CityIoSession.Context context,CityBlueprint.Point anchor){
        var b=context.bounds();
        long x0=Math.floorDiv((long)anchor.x()-128,16)*16-16,x1=Math.floorDiv((long)anchor.x()+127,16)*16+31;
        long z0=Math.floorDiv((long)anchor.z()-128,16)*16-16,z1=Math.floorDiv((long)anchor.z()+127,16)*16+31;
        long y0=Math.floorDiv((long)anchor.y()-24,16)*16-16,y1=Math.floorDiv((long)anchor.y()+47,16)*16+31;
        if(x0<b.minX()||x1>b.maxX()||z0<b.minZ()||z1>b.maxZ()||y0<b.bottomY()||y1>=b.topY())
            throw new IllegalArgumentException("city page halo exceeds actual world/border bounds");
    }
    private void requireContext(){
        var actual=capture(owner);manifest.requireContext(manifest.identity().saveId(),actual.dimension(),actual.worldSeed(),actual.bounds(),actual.backend());
    }
    private boolean availableContext(){
        if(owner.getWorld()!=world||!eligible(owner)){fail(Reason.OWNER_UNAVAILABLE,"operator disconnected, changed mode/world or lost permission");return false;}
        try{requireContext();return true;}catch(IllegalArgumentException changed){fail(Reason.CONTEXT_CHANGED,changed.getMessage());return false;}
    }
    @Override public String worldKey(){serverThread();return world.getRegistryKey().getValue().toString();}
    public Diagnostic diagnostic(){serverThread();return new Diagnostic(reason,detail,lastSnapshots,lastBatch,casCalls);}
    private void fail(Reason why,String message){if(reason==Reason.NONE){reason=why;detail=message;}}
    @Override public boolean pageAndHaloAvailable(CityAuthoringJob.Page requested){
        serverThread();cache.clear();lastSnapshots=0;lastBatch=0;reason=Reason.NONE;detail="";phaseTick=Long.MIN_VALUE;page=requested;
        if(!availableContext())return false;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            var cube=new D6ChunkPos(requested.x()+dx,requested.y()+dy,requested.z()+dz);
            long x=(long)cube.x()*16,y=(long)cube.y()*16,z=(long)cube.z()*16;
            if(x<Integer.MIN_VALUE||x+15>Integer.MAX_VALUE||y<Integer.MIN_VALUE||y+15>Integer.MAX_VALUE||z<Integer.MIN_VALUE||z+15>Integer.MAX_VALUE
                ||!CubicAvailability.withinHeight(world,cube)||!world.getWorldBorder().contains(new BlockPos((int)x,(int)y,(int)z))
                ||!world.getWorldBorder().contains(new BlockPos((int)x+15,(int)y+15,(int)z+15))||!access.isVolumeAvailable(cube)){
                fail(Reason.UNAVAILABLE,"page or one-cube halo unavailable: "+cube);return false;
            }
        }
        phaseTick=world.getServer().getTicks();return true;
    }
    private Optional<ServerBlockEdits.Cell> snapshot(BlockPos position){
        var cached=cache.get(position);if(cached!=null)return cached;
        if(cache.size()>=448){fail(Reason.BUDGET,"cell plus face-neighbor snapshot cap448");return Optional.empty();}
        var state=access.readIfLoaded(position);Optional<ServerBlockEdits.Cell> result=Optional.empty();
        if(state.isEmpty())fail(Reason.UNAVAILABLE,"unavailable cell or neighbor: "+position);
        else if(!inert(state.get(),position))fail(Reason.UNSUPPORTED_STATE,Registries.BLOCK.getId(state.get().getBlock())+" at "+position+"; "+STERILE_LIMIT);
        else{
            result=access.snapshotIfLoaded(position);
            if(result.isEmpty()||result.get().entityData()!=null){result=Optional.empty();fail(Reason.UNAVAILABLE,"unsafe or missing snapshot at "+position);}
        }
        cache.put(position.toImmutable(),result);lastSnapshots=cache.size();return result;
    }
    private boolean inert(BlockState state,BlockPos at){
        var type=state.getBlock().getClass();
        return (type==AirBlock.class||type==Block.class)&&state.getEntries().isEmpty()&&state.getFluidState().isEmpty()&&!state.hasBlockEntity()
            &&(state.isAir()||state.isFullCube(world,at));
    }
    private Optional<ServerBlockEdits.Cell> guarded(CityBlueprint.Point point){
        if(phaseTick!=world.getServer().getTicks()||page==null||!page.equals(CityAuthoringJob.Page.of(point))){fail(Reason.UNAVAILABLE,"expired or foreign page preflight");return Optional.empty();}
        var at=new BlockPos(point.x(),point.y(),point.z());
        if(!world.getWorldBorder().contains(at)||!world.canPlayerModifyAt(owner,at)){fail(Reason.PERMISSION,"operator cannot modify "+at);return Optional.empty();}
        var cell=snapshot(at);if(cell.isEmpty())return Optional.empty();
        for(var face:Direction.values())if(snapshot(at.offset(face)).isEmpty())return Optional.empty();return cell;
    }
    @Override public Optional<String> readLoaded(CityBlueprint.Point point){
        serverThread();if(!availableContext())return Optional.empty();return guarded(point).map(c->Registries.BLOCK.getId(c.state().getBlock()).toString());
    }
    private Optional<String> planned(CityBlueprint.Point point){
        int lo=0,hi=plan.cells().size()-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;var cell=plan.cells().get(mid);int compare=ORDER.compare(cell.position(),point);if(compare==0)return Optional.of(cell.state());if(compare<0)lo=mid+1;else hi=mid-1;}
        return Optional.empty();
    }
    @Override public CityAuthoringJob.CommitResult compareAndSet(CityBatchJournal.Batch batch){
        serverThread();if(!availableContext())return CityAuthoringJob.CommitResult.REJECTED;
        if(lastCasTick==world.getServer().getTicks()){fail(Reason.BUDGET,"one city CAS per server tick");return CityAuthoringJob.CommitResult.REJECTED;}
        if(batch.changes().isEmpty()||batch.changes().size()>64){fail(Reason.BUDGET,"batch cap64");return CityAuthoringJob.CommitResult.REJECTED;}
        var edits=new ArrayList<ServerBlockEdits.Edit>();var actors=new ArrayList<Entity>(1);lastBatch=batch.changes().size();
        for(var change:batch.changes()){
            if(!planned(change.position()).filter(change.after()::equals).isPresent()){fail(Reason.CONFLICT,"cell/target is outside the immutable city plan");return CityAuthoringJob.CommitResult.REJECTED;}
            var before=guarded(change.position());if(before.isEmpty())return reason==Reason.UNAVAILABLE?CityAuthoringJob.CommitResult.UNAVAILABLE:CityAuthoringJob.CommitResult.REJECTED;
            if(!Registries.BLOCK.getId(before.get().state().getBlock()).toString().equals(change.before())){fail(Reason.CONFLICT,"expected state changed");return CityAuthoringJob.CommitResult.CONFLICT;}
            var at=new BlockPos(change.position().x(),change.position().y(),change.position().z());
            var id=Identifier.of(change.after());
            if(!CityBlueprint.PALETTE.contains(change.after())||!Registries.BLOCK.containsId(id)){fail(Reason.UNSUPPORTED_STATE,STERILE_LIMIT);return CityAuthoringJob.CommitResult.REJECTED;}
            var after=Registries.BLOCK.get(id).getDefaultState();if(!inert(after,at)){fail(Reason.UNSUPPORTED_STATE,STERILE_LIMIT);return CityAuthoringJob.CommitResult.REJECTED;}
            if(!after.isAir()){
                actors.clear();
                world.collectEntitiesByType(TypeFilter.instanceOf(Entity.class),new Box(at),entity->!entity.isRemoved()&&!entity.isSpectator(),actors,1);
                if(!actors.isEmpty()){
                    fail(Reason.ACTOR_BLOCKED,"new solid intersects an existing actor at "+at);return CityAuthoringJob.CommitResult.REJECTED;
                }
            }
            edits.add(new ServerBlockEdits.Edit(at,before.get(),new ServerBlockEdits.Cell(after,null)));
        }
        lastCasTick=world.getServer().getTicks();var result=access.modifyServerAuthoritative(edits);casCalls++;cache.clear();phaseTick=Long.MIN_VALUE;
        return switch(result.status()){
            case COMMITTED->CityAuthoringJob.CommitResult.COMMITTED;
            case NO_CHANGE->CityAuthoringJob.CommitResult.NO_CHANGE;
            case NOTIFICATION_FAILED->CityAuthoringJob.CommitResult.NOTIFICATION_FAILED;
            case CONFLICT->{fail(Reason.CONFLICT,result.detail());yield CityAuthoringJob.CommitResult.CONFLICT;}
            case UNAVAILABLE->{fail(Reason.UNAVAILABLE,result.detail());yield CityAuthoringJob.CommitResult.UNAVAILABLE;}
            case ROLLBACK_FAILED->{fail(Reason.BACKEND_FAILED,result.detail());throw new IllegalStateException("city backend rollback failed; quarantine");}
            default->{fail(Reason.BACKEND_FAILED,result.status()+": "+result.detail());yield CityAuthoringJob.CommitResult.REJECTED;}
        };
    }
}

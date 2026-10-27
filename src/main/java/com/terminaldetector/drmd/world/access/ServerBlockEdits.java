package com.terminaldetector.drmd.world.access;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.build.PlacementTransaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;

/** Internal, drop-free ownership edits. Ordinary break/place gameplay keeps its vanilla path. */
public final class ServerBlockEdits {
    public static final int MAX_CELLS=256,MAX_ENTITY_BYTES=262144,MAX_BATCH_BYTES=1048576;
    public record Cell(BlockState state,NbtCompound entityData) {
        public Cell {Objects.requireNonNull(state);entityData=entityData==null?null:entityData.copy();}
        @Override public NbtCompound entityData(){return entityData==null?null:entityData.copy();}
    }
    public record Edit(BlockPos position,Cell expected,Cell replacement) {
        public Edit {position=Objects.requireNonNull(position).toImmutable();Objects.requireNonNull(expected);Objects.requireNonNull(replacement);}
    }
    public enum Status { COMMITTED,NO_CHANGE,REJECTED,UNAVAILABLE,CONFLICT,UNSUPPORTED_BLOCK,INVALID_NBT,IN_USE,ROLLED_BACK,ROLLBACK_FAILED,NOTIFICATION_FAILED }
    public record Result(Status status,int changed,String detail) {
        public boolean committed(){return status==Status.COMMITTED || status==Status.NO_CHANGE || status==Status.NOTIFICATION_FAILED;}
    }
    private ServerBlockEdits() {}

    public static Optional<Cell> snapshot(World world,BlockPos pos) {
        if(WorldAccess.of(world).readIfLoaded(pos).isEmpty())return Optional.empty();
        var chunk=chunk(world,pos);if(chunk==null)return Optional.empty();
        var state=chunk.getBlockState(pos);
        if(!state.hasBlockEntity())return Optional.of(new Cell(state,null));
        var entity=chunk.getBlockEntity(pos,WorldChunk.CreationType.CHECK);
        if(entity==null || entity.isRemoved() || !entity.getPos().equals(pos) || !entity.getType().supports(state))return Optional.empty();
        return Optional.of(new Cell(state,entity.createNbtWithIdentifyingData(world.getRegistryManager())));
    }

    public static Result commit(World world,List<Edit> edits) {
        if(!(world instanceof ServerWorld server) || !CubicAvailability.onWorldThread(world))return result(Status.REJECTED,"server thread required");
        if(edits==null || edits.isEmpty() || edits.size()>MAX_CELLS)return result(Status.REJECTED,"batch bounds");
        var positions=new HashSet<BlockPos>();var changes=new ArrayList<PlacementTransaction.Change<BlockPos,Cell>>();
        long bytes=0;
        for(var edit:edits) {
            if(edit==null || !positions.add(edit.position()))return result(Status.REJECTED,"duplicate/null edit");
            var at=edit.position();
            if(!world.getWorldBorder().contains(at))return result(Status.REJECTED,"outside border");
            var before=snapshot(world,at);
            if(before.isEmpty())return result(Status.UNAVAILABLE,"cell or valid block entity unavailable");
            if(!before.get().equals(edit.expected()))return result(Status.CONFLICT,"expected state/NBT changed");
            if(!stable(before.get().state()) || !stable(edit.replacement().state()))return result(Status.UNSUPPORTED_BLOCK,"block callbacks require a dedicated adapter");
            if(before.get().state().hasBlockEntity() && server.getPlayers().stream().anyMatch(player->
                    player.currentScreenHandler!=player.playerScreenHandler && player.squaredDistanceTo(at.toCenterPos())<64))
                return result(Status.IN_USE,"nearby open container screen");
            Cell after;
            try {after=normalize(server,at,edit.replacement());}
            catch(RuntimeException invalid){return result(Status.INVALID_NBT,invalid.getMessage());}
            int oldBytes=before.get().entityData==null?0:before.get().entityData.getSizeInBytes();
            int newBytes=after.entityData==null?0:after.entityData.getSizeInBytes();
            bytes+=(long)oldBytes+newBytes;
            if(oldBytes<0 || newBytes<0 || oldBytes>MAX_ENTITY_BYTES || newBytes>MAX_ENTITY_BYTES || bytes>MAX_BATCH_BYTES)
                return result(Status.REJECTED,"NBT budget");
            if(!before.get().equals(after))changes.add(new PlacementTransaction.Change<>(at,before.get(),after));
        }
        if(changes.isEmpty())return result(Status.NO_CHANGE,"");
        var transaction=PlacementTransaction.commit(changes,new PlacementTransaction.Storage<BlockPos,Cell>() {
            public Cell read(BlockPos pos){return snapshot(world,pos).orElse(null);}
            public boolean write(BlockPos pos,Cell cell){return quietWrite(server,pos,cell);}
        },()->changes.stream().allMatch(change->WorldAccess.of(world).readIfLoaded(change.position()).isPresent()));
        if(!transaction.committed()) {
            Status status=transaction.status()==PlacementTransaction.Status.ROLLBACK_FAILED?Status.ROLLBACK_FAILED:
                transaction.status()==PlacementTransaction.Status.ROLLED_BACK?Status.ROLLED_BACK:Status.CONFLICT;
            if(status==Status.ROLLBACK_FAILED)DescentMod.LOGGER.error("[drmd world access] state/NBT rollback failed",transaction.failure());
            return result(status,"native batch did not commit");
        }
        // Publication follows successful state/NBT readback. It cannot be rolled back after packets.
        boolean notified=true;
        for(var change:changes) {
            try {
                var full=chunk(world,change.position());
                if(full!=null) {
                    var entity=full.getBlockEntity(change.position(),WorldChunk.CreationType.CHECK);
                    if(entity!=null)entity.markDirty();
                }
                world.updateListeners(change.position(),change.before().state(),change.after().state(),Block.NOTIFY_ALL);
                world.updateNeighbors(change.position(),change.after().state().getBlock());
                server.getChunkManager().markForUpdate(change.position());
            }catch(RuntimeException failure){notified=false;DescentMod.LOGGER.error("[drmd world access] committed batch notification failed at {}",change.position(),failure);}
        }
        return new Result(notified?Status.COMMITTED:Status.NOTIFICATION_FAILED,changes.size(),notified?"":"state committed; publication failed");
    }

    private static boolean quietWrite(ServerWorld world,BlockPos at,Cell cell) {
        if(WorldAccess.of(world).readIfLoaded(at).isEmpty())return false;
        var full=chunk(world,at);if(full==null)return false;
        // Detach the inventory before replacement so vanilla cannot scatter it as a second owner.
        full.removeBlockEntity(at);
        if(!world.setBlockState(at,cell.state(),Block.FORCE_STATE) && !full.getBlockState(at).equals(cell.state()))return false;
        if(cell.entityData!=null) {
            full.removeBlockEntity(at);
            var entity=BlockEntity.createFromNbt(at,cell.state(),cell.entityData(),world.getRegistryManager());
            if(entity==null || !entity.getType().supports(cell.state()))return false;
            full.setBlockEntity(entity);
        }
        return true;
    }
    private static Cell normalize(ServerWorld world,BlockPos at,Cell cell) {
        if(!cell.state().hasBlockEntity()) {
            if(cell.entityData!=null)throw new IllegalArgumentException("NBT on a state without a block entity");
            return cell;
        }
        if(!(cell.state().getBlock() instanceof BlockEntityProvider provider))throw new IllegalArgumentException("no block entity factory");
        var entity=provider.createBlockEntity(at,cell.state());
        if(entity==null || !entity.getType().supports(cell.state()))throw new IllegalArgumentException("invalid block entity type/state");
        var defaults=entity.createNbtWithIdentifyingData(world.getRegistryManager());
        if(cell.entityData==null)return new Cell(cell.state(),defaults);
        var input=cell.entityData();
        if(input.getInt("x")!=at.getX() || input.getInt("y")!=at.getY() || input.getInt("z")!=at.getZ()
            || !input.getString("id").equals(defaults.getString("id")))throw new IllegalArgumentException("NBT type/position mismatch");
        entity.read(input,world.getRegistryManager());
        var canonical=entity.createNbtWithIdentifyingData(world.getRegistryManager());
        if(!canonical.equals(input))throw new IllegalArgumentException("NBT does not round trip without loss");
        return new Cell(cell.state(),canonical);
    }
    private static boolean stable(BlockState state) {
        var type=state.getBlock().getClass();
        return type==Block.class || type==AirBlock.class || type==PillarBlock.class || type==GrassBlock.class
            || type==ChestBlock.class || type==BarrelBlock.class;
    }
    private static WorldChunk chunk(World world,BlockPos pos) {
        var found=world.getChunkManager().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        return found instanceof WorldChunk full?full:null;
    }
    private static Result result(Status status,String detail){return new Result(status,0,detail==null?"":detail);}
}

package com.terminaldetector.drmd.world.access;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** This facade owns no blocks, snapshots, files or tickets. */
public final class WorldAccess implements CubicWorldAccess {
    private final World world;
    private WorldAccess(World world){this.world=Objects.requireNonNull(world);}
    public static WorldAccess of(World world){return new WorldAccess(world);}
    @Override public CubicAvailability.Backend backend(){return CubicAvailability.backend(world);}
    @Override public boolean isVolumeAvailable(D6ChunkPos cube){return CubicAvailability.available(world,cube);}
    @Override public Optional<BlockState> readIfLoaded(BlockPos pos) {
        if(world.isOutOfHeightLimit(pos) || !isVolumeAvailable(D6ChunkPos.ofBlock(pos.getX(),pos.getY(),pos.getZ())))return Optional.empty();
        var chunk=world.getChunkManager().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        return chunk instanceof WorldChunk full?Optional.of(full.getBlockState(pos)):Optional.empty();
    }
    @Override public Optional<GravityFields.Sample> getGravityAt(Vec3d point) {
        if(!CubicAvailability.onWorldThread(world) || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z))return Optional.empty();
        return Optional.ofNullable(GravityFields.sample(world,point));
    }
    @Override public Optional<ServerBlockEdits.Cell> snapshotIfLoaded(BlockPos pos){return ServerBlockEdits.snapshot(world,pos);}
    @Override public ServerBlockEdits.Result modifyServerAuthoritative(List<ServerBlockEdits.Edit> edits){return ServerBlockEdits.commit(world,edits);}
}

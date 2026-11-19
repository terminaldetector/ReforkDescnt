package com.terminaldetector.drmd.world.access;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Live three-axis authority. Queries never acquire tickets or substitute cached/generated air. */
public interface VolumetricWorldAccess {
    /** FULL is storage availability, not an assertion of entity/block ticking eligibility. */
    enum CubeState { FULL, UNAVAILABLE, OUTSIDE_HEIGHT, UNREPRESENTABLE, UNSUPPORTED_BACKEND, WRONG_THREAD }

    /** Registry height only: neither this range nor a cube address promises native packing support. */
    record Bounds(int bottomY, int topYExclusive) {
        public Bounds {
            if (bottomY >= topYExclusive) throw new IllegalArgumentException("empty world height");
        }
        public boolean containsBlock(int y) { return y >= bottomY && y < topYExclusive; }
        public boolean containsCube(D6ChunkPos cube) {
            long minimum = (long) cube.y() * D6ChunkPos.CHUNK_SIZE;
            return minimum >= bottomY && minimum + D6ChunkPos.CHUNK_SIZE <= topYExclusive;
        }
    }

    CubicAvailability.Backend backend();
    /** Owner-thread metadata; callers on other threads must retain their own immutable bounds. */
    Bounds bounds();
    /** Pure signed floor addressing, independent of BlockPos's runtime long packing. */
    default D6ChunkPos cubeAt(BlockPos block) {
        return D6ChunkPos.ofBlock(block.getX(), block.getY(), block.getZ());
    }
    CubeState cubeState(D6ChunkPos cube);
    boolean isVolumeAvailable(D6ChunkPos cube);
    Optional<BlockState> readIfLoaded(BlockPos pos);
    Optional<ServerBlockEdits.Cell> snapshotIfLoaded(BlockPos pos);
    /** Bounded authoritative batch, including existing commit/readback and native notification path. */
    ServerBlockEdits.Result modifyServerAuthoritative(List<ServerBlockEdits.Edit> edits);
    Optional<GravityFields.Sample> getGravityAt(Vec3d point);
    /** Observations only; a subscription never makes an unavailable cube available. */
    Optional<CubeLifecycle.Subscription> watchCube(D6ChunkPos cube, Consumer<CubeLifecycle.Event> listener);
}

package com.terminaldetector.drmd.world.access;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.Optional;

/** Read boundary for a real backend. Unavailable volume is distinct from available air. */
public interface CubicWorldAccess {
    CubicAvailability.Backend backend();
    boolean isVolumeAvailable(D6ChunkPos cube);
    Optional<BlockState> readIfLoaded(BlockPos pos);
    Optional<GravityFields.Sample> getGravityAt(Vec3d point);
}

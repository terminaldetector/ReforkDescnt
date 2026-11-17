package com.terminaldetector.drmd.world.gravity;

import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import java.util.Set;

/** Preserve native destination metadata when a framed request precedes MobNavigation's world-Y coercion. */
public interface LocalSurfaceRequestAccess {
    Path drmd$surfaceRequest(Set<BlockPos> targets,int distance,float followRange);
}

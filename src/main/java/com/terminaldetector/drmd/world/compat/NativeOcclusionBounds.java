package com.terminaldetector.drmd.world.compat;

/** Conservative source height: an unloaded cube below the available range is still opaque. */
public final class NativeOcclusionBounds {
    private NativeOcclusionBounds() {}

    public static int target(int cachedHeight, int loadedSourceHeight, int lowestFullCubeY) {
        return Math.min(cachedHeight, Math.max(loadedSourceHeight, Math.multiplyExact(lowestFullCubeY, 16)));
    }
}

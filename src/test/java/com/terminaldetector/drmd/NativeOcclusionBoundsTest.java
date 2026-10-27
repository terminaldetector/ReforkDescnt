package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.compat.NativeOcclusionBounds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeOcclusionBoundsTest {
    @Test void anUnknownLowerCubeRemainsAnOccluder() {
        assertEquals(2160, NativeOcclusionBounds.target(2162, 87, 135));
        assertEquals(2144, NativeOcclusionBounds.target(2160, 87, 134));
    }
    @Test void negativeCubeFloorDoesNotTruncateTowardZero() {
        assertEquals(-528, NativeOcclusionBounds.target(-510, -600, -33));
        assertEquals(-512, NativeOcclusionBounds.target(-510, -600, -32));
    }
    @Test void aRealLoadedSourceIsNeverLoweredOrRaisedPastTheCachedHeight() {
        assertEquals(2155, NativeOcclusionBounds.target(2162, 2155, 134));
        assertEquals(2162, NativeOcclusionBounds.target(2162, 2170, 134));
        assertEquals(87, NativeOcclusionBounds.target(2162, 87, 0));
    }
}

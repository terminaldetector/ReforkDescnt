package com.terminaldetector.drmd.world.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistantHorizonsCompatTest {
    @Test
    void cubicRendererCoverageBlendsOverTheLowestGroundAndLeavesTheOrbitBackdropVisible() {
        assertEquals(0.0, DistantHorizonsCompat.uncoveredGround(191, 63, 128));
        assertEquals(0.5, DistantHorizonsCompat.uncoveredGround(207, 63, 128));
        assertEquals(1.0, DistantHorizonsCompat.uncoveredGround(223, 63, 128));
        assertEquals(1.0, DistantHorizonsCompat.uncoveredGround(1580, 63, 128));
        assertEquals(0.0, DistantHorizonsCompat.uncoveredGround(320, 63, 320));
    }

    @Test
    void unusableCoverageKeepsTheFallback() {
        assertEquals(1.0, DistantHorizonsCompat.uncoveredGround(330, 63, 0));
        assertEquals(1.0, DistantHorizonsCompat.uncoveredGround(Double.NaN, 63, 128));
    }
}

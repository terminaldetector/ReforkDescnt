package com.terminaldetector.drmd.client.planet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroundHorizonCoverageTest {
    @Test
    void loadedFootprintKeepsBackdropAtTheNativeDrawHandoff() {
        double drop = 174.80839390839716 - 69;
        assertEquals(.23713378, GroundHorizonCoverage.bridgeOpacity(0, drop, 1), 1e-6);
    }

    @Test
    void depthOverlapStartsSmoothlyAndKeepsTheMissingTerrainFallback() {
        assertEquals(0, GroundHorizonCoverage.bridgeOpacity(0, 16, 1));
        assertEquals(0, GroundHorizonCoverage.bridgeOpacity(0, 16.001, 1), 1e-8);
        assertEquals(.12, GroundHorizonCoverage.bridgeOpacity(0, 64, 1), 1e-6);
        assertEquals(.34, GroundHorizonCoverage.bridgeOpacity(0, 64, 0), 1e-6);
        assertEquals(.24, GroundHorizonCoverage.bridgeOpacity(0, 112, 1), 1e-6);
        assertEquals(.46, GroundHorizonCoverage.bridgeOpacity(0, 112, .5), 1e-6);
        assertEquals(.68, GroundHorizonCoverage.bridgeOpacity(0, 112, 0), 1e-6);
    }

    @Test
    void existingAltitudeOpacityRetainsAuthority() {
        assertEquals(1, GroundHorizonCoverage.bridgeOpacity(1, 112, 1));
        assertEquals(.62, GroundHorizonCoverage.bridgeOpacity(.5f, 112, 1), 1e-6);
        assertEquals(.5, GroundHorizonCoverage.bridgeOpacity(.5f, 8, 1), 1e-6);
    }
}

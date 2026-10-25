package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.level.VerticalBounds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerticalBoundsTest {
    @Test void actualColumnHasExplicitOutsideStates() {
        assertEquals(VerticalBounds.Position.BELOW,VerticalBounds.classify(-785,-784,1888));
        assertEquals(VerticalBounds.Position.INSIDE,VerticalBounds.classify(-784,-784,1888));
        assertEquals(VerticalBounds.Position.INSIDE,VerticalBounds.classify(1887.99,-784,1888));
        assertEquals(VerticalBounds.Position.ABOVE,VerticalBounds.classify(1888,-784,1888));
        assertEquals(VerticalBounds.Position.ABOVE,VerticalBounds.classify(2150,-784,1888));
    }
    @Test void historicalAndVanillaHeightsUseTheirOwnRuntimeBounds() {
        for(int y:new int[]{-513,-512,-420,-240,-64,40,320,640,880,1024,1025,2150})
            assertEquals(y>=-512&&y<1024,VerticalBounds.contains(y,-512,1024));
        assertFalse(VerticalBounds.contains(320,-64,320));
        assertTrue(VerticalBounds.contains(-64,-64,320));
    }
    @Test void recoveryStartsBeforeTheVoidRatherThanAtDeathAltitude() {
        assertFalse(VerticalBounds.needsLowerRecovery(-779,-784));
        assertTrue(VerticalBounds.needsLowerRecovery(-781,-784));
        assertTrue(VerticalBounds.needsLowerRecovery(-860,-784));
        assertFalse(VerticalBounds.needsLowerRecovery(2150,-784));
    }
    @Test void invalidHeightIsNotSilentlyClassifiedAsABiome() {
        assertEquals(VerticalBounds.Position.INVALID,VerticalBounds.classify(Double.NaN,-784,1888));
        assertEquals(VerticalBounds.Position.INVALID,VerticalBounds.classify(Double.POSITIVE_INFINITY,-784,1888));
        assertEquals(VerticalBounds.Position.INVALID,VerticalBounds.classify(40,320,-64));
    }
}

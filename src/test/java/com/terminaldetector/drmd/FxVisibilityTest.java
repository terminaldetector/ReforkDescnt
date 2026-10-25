package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.render.FxVisibility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FxVisibilityTest {
    @Test void cameraInsideAFlashNeverGetsAnOpaqueFullScreenQuad() {
        assertEquals(0,FxVisibility.radius(0,4)); assertEquals(0,FxVisibility.radius(.1,4));
        assertEquals(0,FxVisibility.radius(Double.NaN,4));
        assertEquals(0,FxVisibility.radius(2,Float.POSITIVE_INFINITY));
    }
    @Test void closeFlashesStayInsideTheAngularBudget() {
        for(double distance:new double[]{.12,.2,1,2,16,64,128})
            assertTrue(FxVisibility.radius(distance,4)/distance<=.180001);
    }
    @Test void distantFlashesKeepTheirRequestedWorldSize() {
        assertEquals(.48f,FxVisibility.radius(16,.48f));
        assertEquals(.24f,FxVisibility.radius(128,.24f));
    }
}

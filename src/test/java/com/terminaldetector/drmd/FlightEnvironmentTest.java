package com.terminaldetector.drmd;

import com.terminaldetector.drmd.flight.FlightEnvironment;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightEnvironmentTest {
    @Test void classicLeavesTheExistingCommandsUnchanged() {
        var value=FlightEnvironment.resolve(1.58f,1.548f,2.1f,1,1);
        assertEquals(1.58f,value.accelerationMultiplier());
        assertEquals(1.548f,value.speedMultiplier());
        assertEquals(2.1f,value.dragCoefficient());
    }
    @Test void deepAndSpaceCannotSilentlyUseTheClassicPredictor() {
        var deep=FlightEnvironment.resolve(1,1,2.1f,.55f,1.35f);
        var space=FlightEnvironment.resolve(1,1,2.1f,1.8f,.05f);
        assertEquals(.55f,deep.accelerationMultiplier());
        assertEquals(2.835f,deep.dragCoefficient(),1e-6f);
        assertEquals(.9475f,deep.speedMultiplier(),1e-6f);
        assertEquals(1.8f,space.accelerationMultiplier());
        assertEquals(.105f,space.dragCoefficient(),1e-6f);
        assertEquals(1.1425f,space.speedMultiplier(),1e-6f);
        assertTrue(deep.accelerationMultiplier()<space.accelerationMultiplier()&&deep.dragCoefficient()>space.dragCoefficient());
    }
    @Test void environmentalTransitionPreservesAfterburnerAndEngineTuning() {
        var ordinary=FlightEnvironment.resolve(1,1,2.1f,1.35f,.45f);
        var burn=FlightEnvironment.resolve(1.58f,1.548f,2.1f,1.35f,.45f);
        assertEquals(1.58f,burn.accelerationMultiplier()/ordinary.accelerationMultiplier(),1e-6f);
        assertEquals(1.548f,burn.speedMultiplier()/ordinary.speedMultiplier(),1e-6f);
        assertEquals(ordinary.dragCoefficient(),burn.dragCoefficient());
    }
}

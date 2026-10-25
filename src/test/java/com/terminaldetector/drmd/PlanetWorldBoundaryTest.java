package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.planet.PlanetClientState;
import com.terminaldetector.drmd.world.store.SectionKey;
import com.terminaldetector.drmd.world.store.SurfaceSection;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlanetWorldBoundaryTest {
    @Test void worldReplacementCannotReuseObservedTerrainFromThePreviousSeed() {
        var state=PlanetClientState.INSTANCE;
        state.clear();
        try {
            state.apply(42,List.of(),List.of());
            var section=new SurfaceSection();section.set(0,0,84,0x123456);
            state.acceptSection(SectionKey.of(0,0,0),section.toBytes());
            assertEquals(84,state.observedHeight(0,0,0));
            state.apply(42,List.of(),List.of());
            assertEquals(84,state.observedHeight(0,0,0),"ordinary same-world sync retains observations");
            state.apply(43,List.of(),List.of());
            assertEquals(SurfaceSection.NO_HEIGHT,state.observedHeight(0,0,0),"replacement world starts with no old observations");
        } finally {state.clear();}
    }
}

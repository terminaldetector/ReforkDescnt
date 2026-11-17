package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.planet.PlanetClientState;
import com.terminaldetector.drmd.world.store.SectionKey;
import com.terminaldetector.drmd.world.store.SurfaceSection;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlanetWorldBoundaryTest {
    @Test void initialPlanetSnapshotKeepsSectionsAlreadyAcceptedForThisSession() {
        var state=PlanetClientState.INSTANCE;
        state.clear();
        try {
            var section=new SurfaceSection();section.set(0,0,2301,0);
            state.acceptSection(SectionKey.of(0,0,0),section.toBytes());
            assertEquals(2301,state.observedHeight(0,0,0));
            state.apply(42,List.of(),List.of());
            assertEquals(2301,state.observedHeight(0,0,0),"the first seed snapshot must retain an already accepted surface packet");
            assertEquals(0,state.observedColour(0,0,0),"a real black observation is not missing terrain");
        } finally {state.clear();}
    }

    @Test void disconnectDropsOldTerrainBeforeEarlyPacketsFromAnotherSameSeedSession() {
        var state=PlanetClientState.INSTANCE;
        state.clear();
        try {
            state.apply(42,List.of(),List.of());
            var old=new SurfaceSection();old.set(1,0,88,0x123456);
            state.acceptSection(SectionKey.of(0,0,0),old.toBytes());
            assertEquals(88,state.observedHeight(SectionKey.cellSize(0),0,0));
            state.clear();
            var fresh=new SurfaceSection();fresh.set(0,0,105,0xabcdef);
            state.acceptSection(SectionKey.of(0,0,0),fresh.toBytes());
            state.apply(42,List.of(),List.of());
            assertEquals(105,state.observedHeight(0,0,0),"new-session data may precede its first seed snapshot");
            assertEquals(0xabcdef,state.observedColour(0,0,0));
            assertEquals(SurfaceSection.NO_HEIGHT,state.observedHeight(SectionKey.cellSize(0),0,0),"old-session terrain remains cleared even when seeds match");
        } finally {state.clear();}
    }

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

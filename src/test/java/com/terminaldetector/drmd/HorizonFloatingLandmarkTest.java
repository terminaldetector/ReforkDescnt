package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.planet.*;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HorizonFloatingLandmarkTest {
    @Test void floatingEntriesUseTheirRealCatalogueAltitudeWhileGroundedKindsKeepTheTerrainAnchor() {
        for(var kind:new MacroEntry.Kind[]{MacroEntry.Kind.KLONDIKE_ISLAND,MacroEntry.Kind.END_ISLAND,
                MacroEntry.Kind.ARCH,MacroEntry.Kind.RING,MacroEntry.Kind.STATION}) {
            var mark=new HorizonLandmark(1000,1700,2000,48,40,48,0xffffff,kind);
            assertEquals(1680,mark.baseY(70));assertEquals(1700,mark.baseY(70)+mark.height()*.5);
            assertEquals(mark.baseY(70),mark.baseY(-600),"floating objects do not follow a different terrain sample");
        }
        var tower=new HorizonLandmark(1000,150,2000,24,40,24,0xffffff,MacroEntry.Kind.LOCATOR);
        assertEquals(73,tower.baseY(73));
    }
    @Test void theActualUploadedSourceMeshKeepsTwoFloatingObjectsAtDifferentWorldAltitudes() {
        var low=new HorizonLandmark(800,900,1200,48,32,48,0x33aa55,MacroEntry.Kind.KLONDIKE_ISLAND);
        var high=new HorizonLandmark(-800,1740,1200,48,48,48,0xaa88cc,MacroEntry.Kind.END_ISLAND);
        var ground=PlanetSurfaceMesh.build(714,0,1800,0,0,400,Set.of(),List.of());
        var actual=PlanetSurfaceMesh.build(714,0,1800,0,0,400,Set.of(),List.of(low,high));
        assertEquals(ground.quads+10,actual.quads,"both normal catalogue silhouettes must actually be emitted");
        for(int object=0;object<2;object++) {
            var mark=object==0?low:high;
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int q=ground.quads+object*5;q<ground.quads+(object+1)*5;q++)for(int i=0;i<4;i++) {
                double y=actual.originY+actual.positions[q*12+i*3+1];min=Math.min(min,y);max=Math.max(max,y);
            }
            assertEquals(mark.baseY(0),min,1e-5);assertEquals(mark.baseY(0)+mark.height(),max,1e-5);
            assertTrue(min>800,"no invented sea-level column under a floating object");
        }
    }
    @Test void holesAndMovingActorsStillDoNotBecomeRandomStaticSkyBoxes() {
        for(var kind:new MacroEntry.Kind[]{MacroEntry.Kind.RIFT,MacroEntry.Kind.CANYON,MacroEntry.Kind.UFO,
                MacroEntry.Kind.SWARM,MacroEntry.Kind.KEEPER,MacroEntry.Kind.WORM})
            assertFalse(new HorizonLandmark(800,1700,1200,48,32,48,0xffffff,kind).drawable());
    }
}

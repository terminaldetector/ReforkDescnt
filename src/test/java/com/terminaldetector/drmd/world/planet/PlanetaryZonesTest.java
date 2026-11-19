package com.terminaldetector.drmd.world.planet;

import com.terminaldetector.drmd.world.layer.WorldLayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetaryZonesTest {
    @Test void nativeSignedBoundsAreCompletelyCoveredWithoutLegacyColumnLimits(){
        var layout=PlanetaryZones.fit(-1_000_000,1_000_000,63).orElseThrow();
        assertEquals(-1_000_000,layout.bands().getFirst().bottom());
        assertEquals(1_000_000,layout.bands().getLast().top());
        for(var zone:PlanetaryZones.Zone.values())assertEquals(1,layout.bands().stream().filter(b->b.zone()==zone).count());
        assertEquals(PlanetaryZones.Zone.CORE,layout.at(-999_999).orElseThrow());
        assertEquals(PlanetaryZones.Zone.ORBIT,layout.at(999_999).orElseThrow());
    }
    @Test void everyBoundaryBelongsToExactlyOneBand(){
        var layout=PlanetaryZones.fit(-64,320,63).orElseThrow();
        for(int i=0;i<layout.bands().size();i++){
            var band=layout.bands().get(i);assertEquals(band.zone(),layout.at(band.bottom()).orElseThrow());
            assertEquals(band.zone(),layout.at(Math.nextDown((double)band.top())).orElseThrow());
            if(i+1<layout.bands().size())assertEquals(layout.bands().get(i+1).zone(),layout.at(band.top()).orElseThrow());
        }
        assertTrue(layout.at(320).isEmpty());assertTrue(layout.at(-64.0001).isEmpty());
    }
    @Test void fullIntegerBoundsDoNotOverflowOrPackSignedY(){
        var layout=PlanetaryZones.fit(Integer.MIN_VALUE,Integer.MAX_VALUE,63).orElseThrow();
        assertEquals(PlanetaryZones.Zone.CORE,layout.at(Integer.MIN_VALUE).orElseThrow());
        assertEquals(PlanetaryZones.Zone.ORBIT,layout.at((double)Integer.MAX_VALUE-1).orElseThrow());
        assertTrue(layout.at(Integer.MAX_VALUE).isEmpty());
    }
    @Test void translatingActualBoundsAndSurfaceTranslatesTheWholeLayout(){
        var original=PlanetaryZones.fit(-1000,4000,80).orElseThrow();
        var moved=PlanetaryZones.fit(1000,6000,2080).orElseThrow();
        for(int i=0;i<original.bands().size();i++){
            assertEquals(original.bands().get(i).bottom()+2000,moved.bands().get(i).bottom());
            assertEquals(original.bands().get(i).top()+2000,moved.bands().get(i).top());
        }
    }
    @Test void impossibleOrUnanchoredDimensionsStayUnmapped(){
        assertTrue(PlanetaryZones.fit(10,10,10).isEmpty());assertTrue(PlanetaryZones.fit(10,9,10).isEmpty());
        assertTrue(PlanetaryZones.fit(-64,320,320).isEmpty());assertTrue(PlanetaryZones.fit(0,16,63).isEmpty());
        assertTrue(PlanetaryZones.fit(0,7,3).isEmpty());
    }
    @Test void nonfinitePositionsAreNeverPlayableZones(){
        var layout=PlanetaryZones.fit(-64,320,63).orElseThrow();
        for(double y:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY})assertTrue(layout.at(y).isEmpty());
    }
    @Test void deterministicLayoutsCannotBeMutatedByCallers(){
        var a=PlanetaryZones.fit(-4096,8192,63).orElseThrow();assertEquals(a,PlanetaryZones.fit(-4096,8192,63).orElseThrow());
        assertThrows(UnsupportedOperationException.class,()->a.bands().clear());
        assertThrows(IllegalArgumentException.class,()->new PlanetaryZones.Layout(-4096,8192,63,a.bands().subList(1,7)));
    }
    @Test void liveHudResolverUsesActualBoundsAndNeverClaimsVoidIsAZone(){
        assertEquals("Core",WorldLayer.displayOverworld(-1_000_000,1_000_000,63,-5000).label());
        assertEquals("Surface",WorldLayer.displayOverworld(-64,320,63,70).label());
        assertEquals("Upper atmosphere",WorldLayer.displayOverworld(-4096,8192,63,600).label());
        assertFalse(WorldLayer.displayOverworld(-64,320,63,320).insideWorld());
        assertEquals("Unmapped world",WorldLayer.displayOverworld(0,16,63,8).label());
    }
}

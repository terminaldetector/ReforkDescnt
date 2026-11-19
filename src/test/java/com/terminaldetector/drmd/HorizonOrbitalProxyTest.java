package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.planet.*;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HorizonOrbitalProxyTest {
    private static HorizonLandmark station(int y){return new HorizonLandmark(100,y,-80,24,16,20,0xabcdef,MacroEntry.Kind.ORBITAL_STATION);}
    @Test void orbitalMeshRetainsExactCertifiedWorldHeightAndActualCellMaterials(){
        var mark=station(3000);var boxes=HorizonOrbitalProxy.boxes(mark,100,3000,-80);assertFalse(boxes.isEmpty());
        var local=OrbitalObjectGeometry.boxes(OrbitalObjectGeometry.Shape.STATION,24,16,20,OrbitalObjectGeometry.Tier.MID);
        assertEquals(local.size(),boxes.size());
        for(int i=0;i<boxes.size();i++){var a=local.get(i);var b=boxes.get(i);assertEquals(88+a.minX(),b.minX());assertEquals(2992+a.minY(),b.minY());assertEquals(-90+a.minZ(),b.minZ());assertEquals(a.colorRgb(),b.colour());}
        assertEquals(2992,mark.baseY(-500));assertEquals(mark.baseY(-500),mark.baseY(100));
    }
    @Test void tierDependsOnTrueVerticalDistanceAsWellAsHorizontalDistance(){
        assertEquals(OrbitalObjectGeometry.Tier.MID,HorizonOrbitalProxy.tier(2048));assertEquals(OrbitalObjectGeometry.Tier.FAR,HorizonOrbitalProxy.tier(2048.001));
        var far=HorizonOrbitalProxy.boxes(station(3000),100,0,-80);var expected=OrbitalObjectGeometry.boxes(OrbitalObjectGeometry.Shape.STATION,24,16,20,OrbitalObjectGeometry.Tier.FAR);assertEquals(expected.size(),far.size());
        assertThrows(IllegalArgumentException.class,()->HorizonOrbitalProxy.tier(Double.NaN));
    }
    @Test void legacyStationAndUnsupportedBoundsNeverInventAnAuthoredShape(){
        var old=new HorizonLandmark(0,3000,0,24,16,20,0,MacroEntry.Kind.STATION);assertFalse(HorizonOrbitalProxy.authored(old.kind()));assertTrue(HorizonOrbitalProxy.boxes(old,0,0,0).isEmpty());
        var invalid=new HorizonLandmark(0,3000,0,25,16,20,0,MacroEntry.Kind.ORBITAL_STATION);assertTrue(HorizonOrbitalProxy.boxes(invalid,0,0,0).isEmpty());
    }
    @Test void cachedAuthoredTierInvalidatesAcrossTheCutoffWithinNormalOriginSlack(){
        var mark=new HorizonLandmark(0,3000,0,24,16,20,0,MacroEntry.Kind.ORBITAL_STATION);
        for(boolean vertical:new boolean[]{false,true}){
            double x=vertical?0:2047.99,y=vertical?5047.99:3000;
            var mesh=PlanetSurfaceMesh.build(714,x,y,0,64,400,java.util.Set.of(),java.util.List.of(mark));
            assertFalse(mesh.orbitalKeys.isEmpty());
            assertFalse(mesh.stale(x,y,0,64,400));
            assertTrue(mesh.stale(vertical?0:2048.01,vertical?5048.01:3000,0,64,400),
                "a genuine3D tier crossing must not wait for64m horizontal/48m altitude drift");
            assertFalse(mesh.stale(vertical?0:2047.5,vertical?5047.5:3000,0,64,400),
                "ordinary movement inside the same tier keeps its cached source");
        }
    }
    @Test void legacyGroundLandmarksDoNotAcquireAuthoredOrbitalTierInvalidation(){
        var mark=new HorizonLandmark(0,3000,0,24,16,20,0,MacroEntry.Kind.STATION);
        var mesh=PlanetSurfaceMesh.build(714,2047.99,3000,0,64,400,java.util.Set.of(),java.util.List.of(mark));
        assertTrue(mesh.orbitalKeys.isEmpty());
        assertFalse(mesh.stale(2048.01,3000,0,64,400));
    }

}

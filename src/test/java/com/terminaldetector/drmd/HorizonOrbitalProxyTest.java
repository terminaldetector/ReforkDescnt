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
}

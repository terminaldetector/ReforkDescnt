package com.terminaldetector.drmd.world.planet;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalObjectGeometryTest {
    private static String key(int x,int y,int z){return x+":"+y+":"+z;}
    private static Set<String> expand(List<OrbitalObjectGeometry.Box> boxes){
        var set=new HashSet<String>();for(var box:boxes)for(int x=box.minX();x<box.maxX();x++)for(int y=box.minY();y<box.maxY();y++)for(int z=box.minZ();z<box.maxZ();z++)assertTrue(set.add(key(x,y,z)),"proxy boxes overlap");return set;
    }
    @Test void asteroidNearAndMergedMidAreTheSameRealVolumetricCells(){
        var cells=OrbitalObjectGeometry.cells(OrbitalObjectGeometry.Shape.ASTEROID,16,12,12);var expected=new HashSet<String>();
        for(var cell:cells){assertEquals("minecraft:stone",cell.state());expected.add(key(cell.x(),cell.y(),cell.z()));}
        assertTrue(cells.size()>500&&cells.size()<16*12*12);assertTrue(cells.stream().map(OrbitalObjectGeometry.Voxel::y).distinct().count()>8);
        var near=OrbitalObjectGeometry.boxes(OrbitalObjectGeometry.Shape.ASTEROID,16,12,12,OrbitalObjectGeometry.Tier.NEAR);
        var mid=OrbitalObjectGeometry.boxes(OrbitalObjectGeometry.Shape.ASTEROID,16,12,12,OrbitalObjectGeometry.Tier.MID);
        assertEquals(expected,expand(near));assertEquals(expected,expand(mid));assertTrue(mid.size()<near.size());assertTrue(mid.size()<=1024);
        assertFalse(expected.contains(key(0,0,0)));assertTrue(expected.contains(key(8,6,6)));
    }
    @Test void stationMidPreservesBothDecksAndClearCentralFlightShaft(){
        var cells=OrbitalObjectGeometry.cells(OrbitalObjectGeometry.Shape.STATION,24,16,20);var expected=new HashSet<String>();for(var cell:cells)expected.add(key(cell.x(),cell.y(),cell.z()));
        assertTrue(expected.contains(key(3,4,3))&&expected.contains(key(3,12,3)));
        for(int y=0;y<16;y++)assertFalse(expected.contains(key(12,y,10)),"central flight shaft must remain actual air");
        assertEquals(expected,expand(OrbitalObjectGeometry.boxes(OrbitalObjectGeometry.Shape.STATION,24,16,20,OrbitalObjectGeometry.Tier.MID)));
        assertTrue(cells.stream().allMatch(v->v.state().equals("minecraft:iron_block")));
    }
    @Test void farBucketsAreBoundedApproximationsBackedByActualOccupiedCells(){
        var cells=OrbitalObjectGeometry.cells(OrbitalObjectGeometry.Shape.ASTEROID,24,24,24);var far=OrbitalObjectGeometry.boxes(OrbitalObjectGeometry.Shape.ASTEROID,24,24,24,OrbitalObjectGeometry.Tier.FAR);
        assertTrue(far.size()<=216);for(var b:far){assertTrue(b.volume()<=64);assertTrue(cells.stream().anyMatch(v->inside(b,v)));}
        for(var cell:cells)assertTrue(far.stream().anyMatch(b->inside(b,cell)),"far approximation lost a real occupied cell");
        assertTrue(expand(far).size()>=cells.size());assertTrue(cells.size()<=OrbitalObjectGeometry.MAX_VOXELS);
    }
    private static boolean inside(OrbitalObjectGeometry.Box b,OrbitalObjectGeometry.Voxel v){return v.x()>=b.minX()&&v.x()<b.maxX()&&v.y()>=b.minY()&&v.y()<b.maxY()&&v.z()>=b.minZ()&&v.z()<b.maxZ();}
    @Test void unsupportedLegacyExtentsNeverInventAnAuthoredShape(){
        assertFalse(OrbitalObjectGeometry.supports(null,16,16,16));assertFalse(OrbitalObjectGeometry.supports(OrbitalObjectGeometry.Shape.STATION,15,16,20));assertFalse(OrbitalObjectGeometry.supports(OrbitalObjectGeometry.Shape.ASTEROID,32,32,32));
        assertThrows(IllegalArgumentException.class,()->OrbitalObjectGeometry.cells(OrbitalObjectGeometry.Shape.STATION,8,8,8));assertThrows(NullPointerException.class,()->OrbitalObjectGeometry.boxes(OrbitalObjectGeometry.Shape.ASTEROID,12,12,12,null));
    }
}

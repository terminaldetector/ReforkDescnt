package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HorizonOrbitalHandoffTest {
    private static HorizonLandmark station(int y) {
        return new HorizonLandmark(0,y,0,24,16,20,0xb0b0b0,MacroEntry.Kind.ORBITAL_STATION);
    }
    private static double volume(HorizonOrbitalProxy.Box b) {
        return (b.maxX()-b.minX())*(b.maxY()-b.minY())*(b.maxZ()-b.minZ());
    }
    @Test void halfOpenSignedClippingPreservesVolumeAndNeverAddsAdjacentCube() {
        var original=new HorizonOrbitalProxy.Box(-16,-1,-17,16,16,0,123);
        var pieces=HorizonOrbitalHandoff.split(original);
        assertEquals(8,pieces.size());
        assertEquals(volume(original),pieces.stream().mapToDouble(p->volume(p.box())).sum());
        assertTrue(pieces.stream().allMatch(p->p.cube().x()>=-1&&p.cube().x()<=0
            &&p.cube().y()>=-1&&p.cube().y()<=0&&p.cube().z()>=-2&&p.cube().z()<=-1));
        assertEquals(1,HorizonOrbitalHandoff.split(new HorizonOrbitalProxy.Box(0,0,0,16,16,16,0)).size());
        assertThrows(UnsupportedOperationException.class,()->pieces.clear());
    }
    @Test void highAltitudeFullCubeUsesActualYAndNoProceduralGroundRadius() {
        var high=new HorizonOrbitalHandoff.Cube(0,100,0);
        assertEquals(0,HorizonOrbitalHandoff.opacity(high,0,1608,0,4,true));
        assertEquals(1,HorizonOrbitalHandoff.opacity(high,0,1608,0,4,false));
        assertEquals(1,HorizonOrbitalHandoff.opacity(new HorizonOrbitalHandoff.Cube(0,0,0),0,1608,0,4,true));
    }
    @Test void fullAndMissingSlicesOfSameColumnHaveIndependentOwnership() {
        var mark=station(1600);
        var low=new HorizonOrbitalHandoff.Cube(0,99,0);var high=new HorizonOrbitalHandoff.Cube(0,100,0);
        var queries=new AtomicInteger();
        var values=HorizonOrbitalHandoff.mask(List.of(new HorizonOrbitalHandoff.Key(mark,low),
            new HorizonOrbitalHandoff.Key(mark,high)),Set.of(mark),0,1600,0,4,
            c->{queries.incrementAndGet();return c.equals(low);});
        assertEquals(0,values[0]);assertEquals(1,values[1]);assertEquals(2,queries.get());
        assertEquals(432,values.length);
    }
    @Test void staleMeshCannotKeepRemovedOrReplacedCertifiedObjectAlive() {
        var mark=station(1600);var calls=new AtomicInteger();
        var keys=List.of(new HorizonOrbitalHandoff.Key(mark,new HorizonOrbitalHandoff.Cube(20,100,0)));
        var removed=HorizonOrbitalHandoff.mask(keys,Set.of(),0,1600,0,4,c->{calls.incrementAndGet();return false;});
        assertEquals(0,removed[0]);assertEquals(0,calls.get());
        var moved=HorizonOrbitalHandoff.mask(keys,Set.of(station(1601)),0,1600,0,4,c->false);
        assertEquals(0,moved[0]);
    }
    @Test void residentViewEdgeHasSpatialRampWithoutCameraCentredInnerHole() {
        var edge=new HorizonOrbitalHandoff.Cube(5,100,0);
        float far=HorizonOrbitalHandoff.opacity(edge,0,1608,8,4,true);
        float middle=HorizonOrbitalHandoff.opacity(edge,8,1608,8,4,true);
        float near=HorizonOrbitalHandoff.opacity(edge,15,1608,8,4,true);
        assertEquals(1,far);assertEquals(.5f,middle);assertTrue(near>0&&near<middle);
        assertEquals(0,HorizonOrbitalHandoff.opacity(edge,16,1608,8,4,true));
        assertEquals(1,HorizonOrbitalHandoff.opacity(edge,15,1608,8,4,false));
    }
    @Test void boundedLargestAlignedShapeFitsTwentySevenAndRejectsOversizedSources() {
        var pieces=HorizonOrbitalHandoff.split(new HorizonOrbitalProxy.Box(1,1,1,33,33,33,0));
        assertEquals(27,pieces.size());assertEquals(32768,pieces.stream().mapToDouble(p->volume(p.box())).sum());
        assertThrows(IllegalArgumentException.class,()->HorizonOrbitalHandoff.split(new HorizonOrbitalProxy.Box(0,0,0,33,1,1,0)));
        assertThrows(IllegalArgumentException.class,()->HorizonOrbitalHandoff.split(new HorizonOrbitalProxy.Box(0,0,0,Double.NaN,1,1,0)));
        var key=new HorizonOrbitalHandoff.Key(station(1600),new HorizonOrbitalHandoff.Cube(0,100,0));
        assertThrows(IllegalArgumentException.class,()->HorizonOrbitalHandoff.mask(java.util.Collections.nCopies(433,key),Set.of(key.mark()),0,1600,0,4,c->false));
    }
    @Test void clippedStationRetainsSameOccupiedGeometryAtRealAltitude() {
        var mark=station(1600);var boxes=HorizonOrbitalProxy.boxes(mark,0,1600,0);
        assertFalse(boxes.isEmpty());
        var pieces=boxes.stream().flatMap(b->HorizonOrbitalHandoff.split(b).stream()).toList();
        assertEquals(boxes.stream().mapToDouble(HorizonOrbitalHandoffTest::volume).sum(),
            pieces.stream().mapToDouble(p->volume(p.box())).sum());
        assertTrue(pieces.stream().allMatch(p->p.box().minY()>=1592&&p.box().maxY()<=1608));
        assertTrue(pieces.stream().map(HorizonOrbitalHandoff.Piece::cube).distinct().count()<=27);
    }
    @Test void actualSourceQuadsCarryCubeSlotsAndLegacyGroundStaysUnmasked() {
        var mark=station(1600);
        var mesh=PlanetSurfaceMesh.build(714,0,1600,0,64,400,Set.of(),List.of(mark));
        assertFalse(mesh.orbitalKeys.isEmpty());assertTrue(mesh.orbitalKeys.size()<=27);
        assertEquals(mesh.quads,mesh.orbitalMasks.length);
        int tagged=0,ground=0;
        for(int q=0;q<mesh.quads;q++) {
            int slot=mesh.orbitalMasks[q];
            if(slot<0){assertEquals(-1,slot);ground++;continue;}
            tagged++;assertTrue(slot<mesh.orbitalKeys.size());
            var key=mesh.orbitalKeys.get(slot);assertEquals(mark,key.mark());
            for(int v=0;v<4;v++) {
                int offset=q*12+v*3;
                double x=mesh.originX+mesh.positions[offset],y=mesh.originY+mesh.positions[offset+1],z=mesh.originZ+mesh.positions[offset+2];
                assertTrue(x>=key.cube().x()*16.0&&x<=(key.cube().x()+1)*16.0);
                assertTrue(y>=key.cube().y()*16.0&&y<=(key.cube().y()+1)*16.0);
                assertTrue(z>=key.cube().z()*16.0&&z<=(key.cube().z()+1)*16.0);
            }
        }
        assertTrue(tagged>0);assertTrue(ground>0);
    }
}

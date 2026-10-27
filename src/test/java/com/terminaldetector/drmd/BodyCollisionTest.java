package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyCollisionTest {
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static OrientedBox box(Vec3 c,Vec3 h){return new OrientedBox(c,h,Quat.IDENTITY);}
    private static void same(Vec3 expected,Vec3 actual){assertTrue(expected.minus(actual).length()<1e-12,expected+" != "+actual);}
    @Test void diagonalHullDoesNotCollideWithEmptyCornersOfItsWorldBounds() {
        var rod=new OrientedBox(v(0,0,0),v(2,.1,.1),new Quat(0,0,Math.sin(Math.PI/8),Math.cos(Math.PI/8)));
        var corner=box(v(1.35,-1.35,0),v(.1,.1,.1));
        assertTrue(rod.bounds().intersects(corner.bounds()));assertTrue(rod.contact(corner,0).isEmpty());
        assertTrue(rod.contact(box(v(1,1,0),v(.1,.1,.1)),0).isPresent());
    }
    @Test void contactNormalAndDepthArePhysicalAndSwapWithTheBodies() {
        var a=box(v(0,0,0),v(1,1,1));var b=box(v(1.5,0,0),v(1,1,1));
        var ab=a.contact(b,0).orElseThrow();var ba=b.contact(a,0).orElseThrow();
        assertEquals(.5,ab.penetration(),1e-12);same(v(-1,0,0),ab.normal());
        same(v(1,0,0),ba.normal());assertEquals(ab.penetration(),ba.penetration(),1e-12);
    }
    @Test void thinObstacleIsCaughtEvenWhenBothEndpointsAreClear() {
        var a=box(v(-10,0,0),v(.25,.25,.25));var wall=box(v(0,0,0),v(.01,5,5));
        var hit=a.sweep(wall,v(20,0,0),v(0,0,0)).orElseThrow();
        assertEquals(9.74/20,hit.fraction(),1e-12);same(v(-1,0,0),hit.normal());
    }
    @Test void ccdUsesMotionOfBothSidesAndDoesNotBlockSlidingOrOpening() {
        var a=box(v(0,0,0),v(.5,.5,.5));var b=box(v(4,0,0),v(.5,.5,.5));
        assertEquals(.75,a.sweep(b,v(2,0,0),v(-2,0,0)).orElseThrow().fraction(),1e-12);
        var touch=box(v(1,0,0),v(.5,.5,.5));
        assertTrue(a.sweep(touch,v(-2,0,0),v(0,0,0)).isEmpty());
        assertTrue(a.sweep(touch,v(0,2,0),v(0,0,0)).isEmpty());
        assertEquals(0,a.sweep(touch,v(2,0,0),v(0,0,0)).orElseThrow().fraction(),1e-12);
    }
    @Test void touchingCornerStillStopsAnInwardMoveOnItsOtherAxis() {
        var a=box(v(0,0,0),v(.5,.5,.5));var b=box(v(1,1,0),v(.5,.5,.5));
        var hit=a.sweep(b,v(0,1,0),v(0,0,0)).orElseThrow();
        assertEquals(0,hit.fraction(),1e-12);same(v(0,-1,0),hit.normal());
    }
    @Test void mergedCompoundIsOneRigidShapeAndRetainsAnInteriorHole() {
        var cells=new ArrayList<CompoundCollider.Cell>();
        for(int x=0;x<8;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++)cells.add(new CompoundCollider.Cell(x,y,z));
        var solid=CompoundCollider.cells(cells);assertEquals(1,solid.boxes().size());
        cells.remove(new CompoundCollider.Cell(3,2,2));var hollow=CompoundCollider.cells(cells);
        assertTrue(hollow.boxes().size()<cells.size());
        assertTrue(hollow.query(new BodyBounds(v(3.1,2.1,2.1),v(3.9,2.9,2.9))).isEmpty());
        assertEquals(127,hollow.boxes().stream().mapToDouble(b->(b.max().x()-b.min().x())*(b.max().y()-b.min().y())*(b.max().z()-b.min().z())).sum(),1e-12);
    }
    @Test void localBvhRayPreservesDoorwaysAndReportsActualFace() {
        var frame=CompoundCollider.cells(List.of(new CompoundCollider.Cell(0,0,0),new CompoundCollider.Cell(2,0,0)));
        assertTrue(frame.raycast(v(1.5,.5,-2),v(1.5,.5,2)).isEmpty());
        var hit=frame.raycast(v(2.5,.5,-2),v(2.5,.5,2)).orElseThrow();
        assertEquals(.5,hit.fraction(),1e-12);same(v(0,0,-1),hit.normal());assertFalse(hit.inside());
        assertTrue(frame.raycast(v(.5,.5,.5),v(.5,.5,3)).orElseThrow().inside());
    }
    @Test void localGridTransformUsesActualCentreOfMassAtLargeWorldCoordinates() {
        var bounds=new BodyBounds(v(4,2,0),v(6,4,2));var q=new Quat(0,Math.sqrt(.5),0,Math.sqrt(.5));
        var posed=OrientedBox.local(bounds,v(20000000,2156,-20000000),v(2,1,1),q);
        assertTrue(posed.centre().minus(v(20000000,2158,-20000003)).length()<1e-8);
        var scaled=OrientedBox.local(bounds,v(0,0,0),v(2,1,1),new Quat(0,Math.sqrt(2),0,Math.sqrt(2)));
        assertTrue(scaled.centre().minus(v(0,2,-3)).length()<1e-8);
    }
    @Test void skewRodsNeedAnEdgeCrossAxisEvenWhenTheirWorldBoundsOverlap() {
        // Skew centre lines are separated along (1,-1,1), perpendicular to both long axes.
        var a=new OrientedBox(v(0,0,0),v(2,.1,.1),new Quat(0,Math.sin(Math.PI/8),0,Math.cos(Math.PI/8)));
        var b=new OrientedBox(v(.3,-.3,.3),v(.1,2,.1),new Quat(Math.sin(Math.PI/8),0,0,Math.cos(Math.PI/8)));
        assertTrue(a.bounds().intersects(b.bounds()));assertTrue(a.contact(b,0).isEmpty());
        assertTrue(.3*Math.sqrt(3)>2*Math.sqrt(2)*.1);
    }
    @Test void negativeGridCellsAndRandomHolesRetainExactlyTheOccupiedVolume() {
        var random=new Random(261027);
        for(int trial=0;trial<20;trial++) {
            var cells=new HashSet<CompoundCollider.Cell>();
            for(int x=-2;x<3;x++)for(int y=-2;y<3;y++)for(int z=-2;z<3;z++)
                if(random.nextBoolean())cells.add(new CompoundCollider.Cell(x,y,z));
            var shape=CompoundCollider.cells(cells);
            for(int x=-2;x<3;x++)for(int y=-2;y<3;y++)for(int z=-2;z<3;z++)
                assertEquals(cells.contains(new CompoundCollider.Cell(x,y,z)),
                    !shape.query(new BodyBounds(v(x+.1,y+.1,z+.1),v(x+.9,y+.9,z+.9))).isEmpty());
        }
    }
    @Test void invalidGeometryIsRejectedBeforeBuildingOrQuerying() {
        assertThrows(IllegalArgumentException.class,()->new BodyBounds(v(0,0,0),v(Double.NaN,1,1)));
        assertThrows(IllegalArgumentException.class,()->box(v(0,0,0),v(0,1,1)));
        assertThrows(IllegalArgumentException.class,()->new OrientedBox(v(0,0,0),v(1,1,1),new Quat(0,0,0,0)));
        assertThrows(IllegalArgumentException.class,()->CompoundCollider.cells(List.of(new CompoundCollider.Cell(1,2,3),new CompoundCollider.Cell(1,2,3))));
        assertThrows(IllegalArgumentException.class,()->CompoundCollider.cells(List.of(new CompoundCollider.Cell(Integer.MIN_VALUE,0,0))));
    }
}

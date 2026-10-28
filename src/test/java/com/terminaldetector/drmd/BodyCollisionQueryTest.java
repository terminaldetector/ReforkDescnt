package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyCollisionQueryTest {
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static OrientedBox box(Vec3 p,Vec3 h){return new OrientedBox(p,h,Quat.IDENTITY);}
    private static CompoundCollider floor(){return CompoundCollider.cells(List.of(new CompoundCollider.Cell(0,0,0),new CompoundCollider.Cell(1,0,0)));}
    @Test void restingSkinFindsActualOccupiedFaceWithoutRebuildingGeometry() {
        var collider=floor();var q=new BodyCollisionQuery(collider,v(0,0,0),v(0,0,0),Quat.IDENTITY);
        var actor=box(v(1.6,1.9+.001,.5),v(.2,.9,.2));
        assertTrue(q.contacts(actor,0).isEmpty());var c=q.contacts(actor,.002).getFirst();
        assertEquals(0,c.penetration());assertEquals(1,c.normal().y(),1e-12);
        assertEquals(1.6,c.point().x(),1e-12,"contact must lie under the actor, not at the merged hull centre");
        assertEquals(1.0005,c.point().y(),1e-12);assertSame(collider.boxes().getFirst(),c.localShape());
    }
    @Test void transformedBvhPreservesDoorwaysAndEmptyAabbCorners() {
        var shape=CompoundCollider.cells(List.of(new CompoundCollider.Cell(0,0,0),new CompoundCollider.Cell(2,0,0)));
        var r=new Quat(0,Math.sin(.4),0,Math.cos(.4));var pos=v(20,30,40);var com=v(1.5,.5,.5);
        var query=new BodyCollisionQuery(shape,pos,com,r);
        assertTrue(query.contacts(new OrientedBox(pos,v(.2,.2,.2),r),0).isEmpty());
        var occupied=pos.plus(r.rotate(v(1,0,0)));assertFalse(query.contacts(new OrientedBox(occupied,v(.2,.2,.2),r),0).isEmpty());
        var rod=new CompoundCollider(List.of(new BodyBounds(v(-2,-.1,-.1),v(2,.1,.1))));
        var diagonal=new BodyCollisionQuery(rod,v(0,0,0),v(0,0,0),new Quat(0,0,Math.sin(Math.PI/8),Math.cos(Math.PI/8)));
        assertTrue(diagonal.contacts(box(v(1.35,-1.35,0),v(.1,.1,.1)),0).isEmpty());
    }
    @Test void relativeCcdIncludesMovingHullInTheBvhBroadphase() {
        var query=new BodyCollisionQuery(floor(),v(0,0,0),v(0,0,0),Quat.IDENTITY);
        var actor=box(v(1,4,.5),v(.2,.5,.2));
        var hit=query.sweep(actor,v(0,-1,0),v(0,4,0)).orElseThrow();
        assertEquals(.5,hit.fraction(),1e-12);assertEquals(1,hit.normal().y(),1e-12);
        assertTrue(query.sweep(actor,v(0,-1,0),v(0,-2,0)).isEmpty());
    }
    @Test void rotatedThinHullCatchesFastCrossingWithClearEndpoints() {
        var r=new Quat(0,0,Math.sin(.3),Math.cos(.3));var pos=v(20000000,1400,-20000000);
        var shape=new CompoundCollider(List.of(new BodyBounds(v(-4,-.01,-4),v(4,.01,4))));
        var query=new BodyCollisionQuery(shape,pos,v(0,0,0),r);
        var start=pos.plus(r.rotate(v(0,10,0)));var movement=r.rotate(v(0,-20,0));
        var hit=query.sweep(new OrientedBox(start,v(.2,.2,.2),r),movement,v(0,0,0)).orElseThrow();
        assertEquals(9.79/20,hit.fraction(),1e-8);assertTrue(hit.normal().minus(r.rotate(v(0,1,0))).length()<1e-8);
    }
    @Test void normalsAndContactPointsFollowAllThreeBodyAxes() {
        var r=new Quat(.2,.3,.4,.7).normalized();var com=v(1,.5,.5);var pos=v(50,-200,37);
        var query=new BodyCollisionQuery(floor(),pos,com,r);
        var actor=new OrientedBox(pos.plus(r.rotate(v(.5,1.39,0))),v(.2,.9,.2),r);
        var c=query.contacts(actor,0).getFirst();assertEquals(.01,c.penetration(),1e-9);
        assertTrue(c.normal().minus(r.rotate(v(0,1,0))).length()<1e-9);
        var local=r.inverse().rotate(c.point().minus(pos)).plus(com);
        assertEquals(1.5,local.x(),1e-9);assertEquals(.995,local.y(),1e-9);
    }
}

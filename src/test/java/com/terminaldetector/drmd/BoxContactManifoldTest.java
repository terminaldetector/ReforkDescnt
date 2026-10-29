package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoxContactManifoldTest {
    @Test void aPartialFacePatchRetainsItsActualFourCorners() {
        var a=new OrientedBox(new Vec3(0,0,0),new Vec3(.5,1,.5),Quat.IDENTITY);
        var b=new OrientedBox(new Vec3(1,.1,0),new Vec3(.5,.5,.5),Quat.IDENTITY);
        var hits=BoxContactManifold.contacts(a,b,0);assertEquals(4,hits.size());
        for(var c:hits) {
            assertEquals(.5,c.point().x(),1e-12);assertEquals(.5,Math.abs(c.point().z()),1e-12);
            assertTrue(Math.abs(c.point().y()+.4)<1e-12||Math.abs(c.point().y()-.6)<1e-12);
            assertEquals(-1,c.normal().x(),1e-12);assertEquals(0,c.penetration(),1e-12);
        }
    }
    @Test void threeAxisRotationAndWorldBorderCoordinatesKeepTheSameContactPatch() {
        var q=new Quat(.2,.3,.4,.7).normalized();var origin=new Vec3(20000000,2100,-20000000);
        var a=new OrientedBox(origin,new Vec3(.5,1,.5),q);
        var b=new OrientedBox(origin.plus(q.rotate(new Vec3(1,.1,0))),new Vec3(.5,.5,.5),q);
        var hits=BoxContactManifold.contacts(a,b,1e-8);assertEquals(4,hits.size());
        for(var c:hits) {
            var local=q.inverse().rotate(c.point().minus(origin));assertEquals(.5,local.x(),1e-8);
            assertEquals(.5,Math.abs(local.z()),1e-8);assertTrue(c.normal().minus(q.rotate(new Vec3(-1,0,0))).length()<1e-8);
        }
        var reversed=BoxContactManifold.contacts(b,a,1e-8);assertEquals(hits.size(),reversed.size());
        for(var c:hits)assertTrue(reversed.stream().anyMatch(r->r.point().minus(c.point()).length()<1e-8&&r.normal().plus(c.normal()).length()<1e-8));
    }
    @Test void skinContactUsesTheMidplaneWithoutInventingPenetration() {
        var a=new OrientedBox(new Vec3(0,0,0),new Vec3(.5,.5,.5),Quat.IDENTITY);
        var b=new OrientedBox(new Vec3(1.001,0,0),new Vec3(.5,.5,.5),Quat.IDENTITY);
        assertTrue(BoxContactManifold.contacts(a,b,0).isEmpty());var hits=BoxContactManifold.contacts(a,b,.002);
        assertEquals(4,hits.size());for(var c:hits){assertEquals(.5005,c.point().x(),1e-12);assertEquals(0,c.penetration());}
    }
}

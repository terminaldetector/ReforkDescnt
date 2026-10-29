package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BodyPairQueryTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private BodyPairQuery.Motion motion(CompoundCollider c,Vec3 start,Vec3 end){return new BodyPairQuery.Motion(c,ZERO,start,Quat.IDENTITY,end,Quat.IDENTITY);}
    private CompoundCollider cube(){return new CompoundCollider(List.of(new BodyBounds(new Vec3(-.5,-.5,-.5),new Vec3(.5,.5,.5))));}
    @Test void opposedFastHullsCannotSwapSidesThroughClearEndpoints() {
        var a=motion(cube(),new Vec3(-3,0,0),new Vec3(3,0,0));var b=motion(cube(),new Vec3(3,0,0),new Vec3(-3,0,0));
        var hit=BodyPairQuery.impact(a,b).orElseThrow();assertEquals(5./12,hit.fraction(),1e-6);
        assertTrue(hit.contacts().stream().allMatch(c->c.normal().x()<-.99));
    }
    @Test void translatingHullsCanSeparateOrSlideWithoutBeingRewoundToAnOldContact() {
        var a=motion(cube(),ZERO,new Vec3(-2,0,0));var b=motion(cube(),new Vec3(1,0,0),new Vec3(1,0,0));
        assertTrue(BodyPairQuery.impact(a,b).isEmpty());
        assertTrue(BodyPairQuery.impact(motion(cube(),ZERO,new Vec3(0,2,0)),b).isEmpty());
    }
    @Test void aMovingHullPassesThroughTheRealHoleOfAnotherCompound() {
        var frame=CompoundCollider.cells(List.of(new CompoundCollider.Cell(0,0,0),new CompoundCollider.Cell(3,0,0)));
        var a=motion(cube(),new Vec3(1.8,.5,-3),new Vec3(1.8,.5,3));var b=motion(frame,ZERO,ZERO);
        assertTrue(BodyPairQuery.impact(a,b).isEmpty());
        assertTrue(BodyPairQuery.impact(motion(cube(),new Vec3(.5,.5,-3),new Vec3(.5,.5,3)),b).isPresent());
    }
    @Test void rotatingHullContactsUseItsActualQuaternionAndNormal() {
        var rod=new CompoundCollider(List.of(new BodyBounds(new Vec3(-3,-.2,-.2),new Vec3(3,.2,.2))));
        var q=ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),Math.PI/2);
        var a=new BodyPairQuery.Motion(rod,ZERO,ZERO,Quat.IDENTITY,ZERO,q);
        var b=motion(cube(),new Vec3(0,2.5,0),new Vec3(0,2.5,0));
        var hit=BodyPairQuery.impact(a,b).orElseThrow();assertTrue(hit.fraction()>0&&hit.fraction()<1);
        assertFalse(BodyPairQuery.contacts(a,b,hit.fraction()).isEmpty());
    }
    @Test void densePlanarContactsAreReducedByGeometryWithoutDroppingTheCollision() {
        var cells=new ArrayList<CompoundCollider.Cell>();for(int x=0;x<32;x++)for(int z=0;z<32;z++)cells.add(new CompoundCollider.Cell(x,0,z));
        var c=CompoundCollider.cells(cells);var a=motion(c,ZERO,new Vec3(0,-1,0));var b=motion(c,new Vec3(0,-1,0),new Vec3(0,-1,0));
        assertFalse(BodyPairQuery.impact(a,b).orElseThrow().contacts().isEmpty());
    }
    @Test void fullThreeAxisOrientationAndRebasedCentrePreservePhysicalTimeOfImpact() {
        var q=new Quat(.2,.3,.4,.7).normalized();var centre=new Vec3(-7,3,4);var origin=new Vec3(1200,-900,50);
        var a=new BodyPairQuery.Motion(cube(),centre,origin.plus(q.rotate(centre.plus(new Vec3(-3,0,0)))),q,origin.plus(q.rotate(centre.plus(new Vec3(3,0,0)))),q);
        var b=new BodyPairQuery.Motion(cube(),centre,origin.plus(q.rotate(centre.plus(new Vec3(3,0,0)))),q,origin.plus(q.rotate(centre.plus(new Vec3(-3,0,0)))),q);
        var hit=BodyPairQuery.impact(a,b).orElseThrow();assertEquals(5./12,hit.fraction(),1e-6);
        assertTrue(hit.contacts().stream().allMatch(c->c.normal().minus(q.rotate(new Vec3(-1,0,0))).length()<1e-8));
        var reversed=BodyPairQuery.impact(b,a).orElseThrow();assertEquals(hit.fraction(),reversed.fraction(),1e-6);
        assertTrue(reversed.contacts().stream().allMatch(c->c.normal().minus(q.rotate(new Vec3(1,0,0))).length()<1e-8));
    }
    @Test void restingContactsJoinAnIslandWithoutManufacturingAnIndependentImpact() {
        var a=motion(cube(),ZERO,ZERO);var b=motion(cube(),new Vec3(1,0,0),new Vec3(1,0,0));
        assertTrue(BodyPairQuery.impact(a,b).isEmpty());assertFalse(BodyPairQuery.contacts(a,b,0,true).isEmpty());
    }
    @Test void materialPointVelocityDoesNotSubtractNearlyEqualWorldBorderCoordinates() {
        var from=new Vec3(20000000,0,-20000000);var q=ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),.004);
        var m=new BodyPairQuery.Motion(cube(),ZERO,from,Quat.IDENTITY,from,q);
        var near=new BodyPairQuery.Motion(cube(),ZERO,ZERO,Quat.IDENTITY,ZERO,q);
        for(double t:new double[]{0,.5,1}) {
            var arm=m.rotation(t).rotate(new Vec3(.5,.2,0));
            assertTrue(m.velocity(from.plus(arm),t).minus(near.velocity(arm,t)).length()<1e-10);
        }
    }
    @Test void manifoldReductionCannotLoseTheFourthClosingCornerOfAContactPatch() {
        var cells=new ArrayList<CompoundCollider.Cell>();for(int y=0;y<4;y++)for(int z=0;z<4;z++)cells.add(new CompoundCollider.Cell(0,y*2,z*2));
        var shape=CompoundCollider.cells(cells);var a=motion(shape,ZERO,new Vec3(.1,0,0));var b=motion(shape,new Vec3(1,0,0),new Vec3(1,0,0));
        var points=BodyPairQuery.contacts(a,b,0,true);
        for(double y:new double[]{0,7})for(double z:new double[]{0,7}) {
            var corner=new Vec3(1,y,z);assertTrue(points.stream().anyMatch(c->c.point().minus(corner).length()<1e-9),"every convex corner must constrain angular closing motion: "+corner);
        }
        assertTrue(points.size()<=5,"coplanar interior points may be reduced only after retaining the entire contact hull");
    }
}

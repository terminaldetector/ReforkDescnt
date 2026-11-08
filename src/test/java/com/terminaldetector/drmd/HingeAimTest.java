package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.weapon.core.HingeAim;
import com.terminaldetector.drmd.client.portal.PortalTransform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HingeAimTest {
    private final Vec3 front=new Vec3(0,0,1),up=new Vec3(0,1,0);
    @Test void positiveQuarterTurn(){assertEquals(.6,HingeAim.velocity(front,new Vec3(1,0,0),up,.6),1e-12);}
    @Test void negativeQuarterTurn(){assertEquals(-.6,HingeAim.velocity(front,new Vec3(-1,0,0),up,.6),1e-12);}
    @Test void alignedMotorBrakes(){assertEquals(0,HingeAim.velocity(front,front,up,.6),1e-12);}
    @Test void shortestTurnCrossesThePiSeam(){var a=ImmPtlQuaternions.rotationByDegrees(up,179).rotate(front);var b=ImmPtlQuaternions.rotationByDegrees(up,-179).rotate(front);assertEquals(Math.toRadians(4),HingeAim.velocity(a,b,up,.6),1e-10);}
    @Test void bankAndPitchPreserveThePhysicalCommand(){var q=ImmPtlQuaternions.rotationByDegrees(new Vec3(1,2,3).normalized(),127);var goal=new Vec3(.1,.2,1);assertEquals(HingeAim.velocity(front,goal,up,.6),HingeAim.velocity(q.rotate(front),q.rotate(goal),q.rotate(up),.6),1e-12);}
    @Test void anOverheadTargetCannotInventAnotherAxis(){assertEquals(0,HingeAim.velocity(front,up,up,.6),1e-12);assertEquals(0,HingeAim.velocity(front,new Vec3(1,0,0),up,0),1e-12);}
    @Test void invalidInputsCannotReachTheJoint(){assertThrows(IllegalArgumentException.class,()->HingeAim.velocity(front,front,new Vec3(0,0,0),.6));assertThrows(IllegalArgumentException.class,()->HingeAim.velocity(front,new Vec3(Double.NaN,0,1),up,.6));assertThrows(IllegalArgumentException.class,()->HingeAim.velocity(front,front,up,-1));}
    @Test void carrierSwingBringsTheTargetIntoTheHingePlane(){var goal=new Vec3(.8,.6,0);var from=HingeAim.swingReference(goal,up,front);var q=PortalTransform.rotationBetween(from,goal);assertEquals(0,q.rotate(up).dot(goal),1e-12);assertEquals(0,new Vec3(q.x(),q.y(),q.z()).dot(up),1e-12);}
    @Test void carrierSwingIsCovariantUnderBank(){var q=ImmPtlQuaternions.rotationByDegrees(new Vec3(1,2,3).normalized(),129);var goal=new Vec3(.4,.2,.7).normalized();assertTrue(q.rotate(HingeAim.swingReference(goal,up,front)).minus(HingeAim.swingReference(q.rotate(goal),q.rotate(up),q.rotate(front))).length()<1e-12);}
    @Test void aPerpendicularTargetUsesTheRealBarrelAsFiniteSwingReference(){assertEquals(front,HingeAim.swingReference(up,up,front));}
    @Test void materialBearingRateHasPhysicalUnitsAndSign(){assertEquals(.1,HingeAim.bearingRate(new Vec3(0,0,20),new Vec3(2,0,0),up),1e-12);assertEquals(-.1,HingeAim.bearingRate(new Vec3(20,0,0),new Vec3(0,0,2),up),1e-12);}
    @Test void angularFeedForwardTracksMotionWithoutIncreasingTheMotorLimit(){assertEquals(.1,HingeAim.velocity(front,front,up,.1,.6),1e-12);assertEquals(.6,HingeAim.velocity(front,new Vec3(1,0,0),up,.1,.6),1e-12);}
    @Test void bearingRateSurvivesBankAndHasNoOverheadSingularity(){var q=ImmPtlQuaternions.rotationByDegrees(new Vec3(1,2,3).normalized(),129);var r=new Vec3(5,2,30);var v=new Vec3(2,-1,.2);assertEquals(HingeAim.bearingRate(r,v,up),HingeAim.bearingRate(q.rotate(r),q.rotate(v),q.rotate(up)),1e-12);assertEquals(0,HingeAim.bearingRate(up,new Vec3(2,0,0),up),1e-12);}
}

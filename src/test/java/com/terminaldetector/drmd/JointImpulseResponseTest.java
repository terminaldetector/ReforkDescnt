package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JointImpulseResponseTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private static final Quat AXIS=ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),90);
    private BodyJointConstraint.Config config(double load){return new BodyJointConstraint.Config(BodyJointConstraint.Kind.MOTOR,new Vec3(0,1,0),new Vec3(0,-1,0),AXIS,AXIS,-Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,.6,40,load,load);}
    private D6PhysicsBody body(double m,Vec3 at){return new D6PhysicsBody().withMass(m).withInertia(D6Mat3.diagonal(m*2,m*3,m*4)).withPosition(at);}
    private Vec3 p(D6PhysicsBody b){return b.linearVelocity().scaled(b.mass());}
    private Vec3 l(D6PhysicsBody b){return b.angularMomentum().plus(b.position().cross(p(b)));}
    @Test void lateGunRecoilReachesTheCarrierWithoutMovingEitherPoseOrLosingMomentum(){
        var a=body(1000,ZERO);var b=body(5,new Vec3(0,2,0));b.applyImpulse(new Vec3(0,0,-160),new Vec3(0,1,1));var p=p(a).plus(p(b));var l=l(a).plus(l(b));
        var c=BodyJointConstraint.velocityOnly(a,b,config(50000),.05);for(int i=0;i<32;i++)c.solve();
        assertTrue(p.minus(p(a).plus(p(b))).length()<1e-8);assertTrue(l.minus(l(a).plus(l(b))).length()<1e-8);
        assertTrue(a.linearVelocity().length()>.01);assertTrue(a.velocityAtPoint(new Vec3(0,1,0)).minus(b.velocityAtPoint(new Vec3(0,-1,0))).length()<1e-7);
        assertEquals(ZERO,a.position());assertEquals(new Vec3(0,2,0),b.position());assertFalse(c.broken());
    }
    @Test void passiveResponseDoesNotSpendTheMotorAgainOrCorrectAnAnchorPose(){
        var a=body(1000,ZERO);var b=body(5,new Vec3(.1,2.1,0));var c=BodyJointConstraint.velocityOnly(a,b,config(50000),.05);for(int i=0;i<32;i++)c.solve();
        assertEquals(ZERO,a.linearVelocity());assertEquals(ZERO,b.linearVelocity());assertEquals(ZERO,b.angularMomentum());assertEquals(new Vec3(.1,2.1,0),b.position());
    }
    @Test void aRealOverloadBreaksThePassiveConnectionWithoutInventingMomentum(){
        var a=body(1000,ZERO);var b=body(5,new Vec3(0,2,0));b.applyImpulse(new Vec3(0,0,-160),ZERO);var p=p(a).plus(p(b));
        var c=BodyJointConstraint.velocityOnly(a,b,config(1),.05);for(int i=0;i<32;i++)c.solve();assertTrue(c.broken());assertTrue(p.minus(p(a).plus(p(b))).length()<1e-8);
    }
    @Test void projectedContactMotionIsNotClampedAgainDuringPoseIntegration(){
        var a=new D6PhysicsBody().withMass(1).withInertia(D6Mat3.diagonal(1,1,1)).withLimits(1000,1);
        var b=new D6PhysicsBody().withMass(1).withInertia(D6Mat3.diagonal(1,1,1)).withLimits(1000,1).withPosition(new Vec3(2,0,0));
        b.applyImpulse(new Vec3(0,0,20),new Vec3(0,1,0));a.limitMotion();b.limitMotion();
        var config=new BodyJointConstraint.Config(BodyJointConstraint.Kind.FIXED,new Vec3(1,0,0),new Vec3(-1,0,0),Quat.IDENTITY,Quat.IDENTITY,0,0,0,0,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        var constraint=new BodyJointConstraint(a,b,config,.001);for(int i=0;i<64;i++)constraint.solve();
        assertTrue(a.angularVelocity().length()>1||b.angularVelocity().length()>1,"the contact solution legitimately contains orbital angular momentum above a free-motion cap");
        var pa=p(a).plus(p(b));var la=a.angularMomentum();var lb=b.angularMomentum();
        a.integrateSolvedPose(.001);b.integrateSolvedPose(.001);
        assertTrue(p(a).plus(p(b)).minus(pa).length()<1e-9&&a.angularMomentum().minus(la).length()<1e-9&&b.angularMomentum().minus(lb).length()<1e-9,"pose advancement must retain the already solved impulses");
        assertTrue(a.position().plus(a.rotation().rotate(new Vec3(1,0,0))).minus(b.position().plus(b.rotation().rotate(new Vec3(-1,0,0)))).length()<.001,"material anchors continue with the projected contact velocities");
    }
    @Test void aTinyCcdRemainderDoesNotTurnAnOrdinaryImpactIntoAnInfiniteBreakingLoad(){
        var a=body(1000,ZERO);var b=body(5,new Vec3(0,2,0));b.applyImpulse(new Vec3(0,0,-160),ZERO);var before=p(a).plus(p(b));
        var contact=BodyJointConstraint.passiveContinuation(a,b,config(50000),1e-7,.05);for(int i=0;i<32;i++)contact.solve();
        assertFalse(contact.broken(),"the impact strength belongs to the enclosing physics frame, not its CCD remainder");
        assertTrue(p(a).plus(p(b)).minus(before).length()<1e-8,"the passive continuation preserves real linear momentum");
        var weakA=body(1000,ZERO);var weakB=body(5,new Vec3(0,2,0));weakB.applyImpulse(new Vec3(0,0,-160),ZERO);
        var overload=BodyJointConstraint.passiveContinuation(weakA,weakB,config(1),1e-7,.05);for(int i=0;i<32;i++)overload.solve();assertTrue(overload.broken(),"actual overload still breaks the finite joint");
    }
}

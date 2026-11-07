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
}

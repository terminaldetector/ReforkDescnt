package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.ragdoll.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D6WorldInverseCacheTest {
    private static final Vec3 ZERO=new Vec3(0,0,0),OFFSET=new Vec3(.7,-.4,.2),NORMAL=new Vec3(0,1,0);
    private static D6PhysicsBody body(){return new D6PhysicsBody().withMass(3).withInertia(D6Mat3.diagonal(2,3,5)).withRotation(new Quat(.2,.3,-.1,.9)).withAngularMomentum(new Vec3(2,-3,4)).withLinearVelocity(new Vec3(.2,.4,-.3));}
    /** Original uncached operation order, including the authored body-space inverse tensor. */
    private static D6Mat3 original(D6PhysicsBody body){var r=D6Mat3.rotationOf(body.rotation());return r.multiply(body.inertia().inverse()).multiply(r.transposed());}
    private static void bits(Vec3 a,Vec3 b){assertEquals(Double.doubleToRawLongBits(a.x()),Double.doubleToRawLongBits(b.x()));assertEquals(Double.doubleToRawLongBits(a.y()),Double.doubleToRawLongBits(b.y()));assertEquals(Double.doubleToRawLongBits(a.z()),Double.doubleToRawLongBits(b.z()));}
    private static void exact(D6PhysicsBody body){var expected=original(body);var actual=body.worldInverseInertia();bits(expected.row0(),actual.row0());bits(expected.row1(),actual.row1());bits(expected.row2(),actual.row2());var angular=expected.transform(body.angularMomentum());bits(angular,body.angularVelocity());bits(angular.cross(OFFSET).plus(body.linearVelocity()),body.velocityAtPoint(OFFSET));double denominator=1/body.mass()+NORMAL.dot(expected.transform(OFFSET.cross(NORMAL)).cross(OFFSET));assertEquals(Double.doubleToRawLongBits(denominator),Double.doubleToRawLongBits(body.pointImpulseDenominator(OFFSET,NORMAL)));}
    @Test void repeatedPointConstraintsKeepExactTensorWhenOnlyMomentumAndVelocityChange(){
        var body=body();var matrix=body.worldInverseInertia();
        for(int i=0;i<64;i++){body.applyImpulse(new Vec3(.1,-.2,.05),OFFSET);exact(body);assertSame(matrix,body.worldInverseInertia(),"unchanged immutable frame/tensor reuses its read value");}
    }
    @Test void everyRealAnisotropicIntegrationFrameUsesItsOwnExactWorldTensor(){
        var body=body();
        for(int i=0;i<200;i++){body.applyTorque(new Vec3(.1,-.2,.3));body.applyForce(new Vec3(.2,-1,.4));body.step(.0125);var snapshot=body.snapshot();exact(body);assertTrue(body.matches(snapshot),"tensor/point queries cannot alter any actual physical state");}
    }
    @Test void sameOrientationCannotReuseAChangedOrSingularInertia(){
        var body=body();body.worldInverseInertia();body.withInertia(D6Mat3.diagonal(7,2,11));exact(body);body.withInertia(D6Mat3.ZERO);assertNull(body.worldInverseInertia());bits(ZERO,body.angularVelocity());body.withInertia(D6Mat3.diagonal(.002,.003,.005));exact(body);
    }
    @Test void portalTurnsAndTransactionRestoreCannotLeaveAStaleFrameOrConsumeInputs(){
        var body=body();body.applyForce(new Vec3(1,2,3));body.applyTorque(new Vec3(-2,3,1));var saved=body.snapshot();var matrix=body.worldInverseInertia();
        D6PortalTransform.of(ZERO,new Vec3(1,0,0),new Vec3(7,9,-4),new Vec3(0,-1,0)).apply(body,null);exact(body);body.restore(saved);exact(body);assertTrue(body.matches(saved));assertEquals(matrix,body.worldInverseInertia());
    }
    @Test void actualArticulatedJointSolverExposesBitExactCurrentPointMassAtEveryStep(){
        var rig=new RagdollSimulation(RagdollRig.scoutDrone(),new Vec3(2,5,-3),new Quat(.2,-.3,.4,.8),new Vec3(1,-.5,.4));rig.applyImpulse(1,new Vec3(2,3,-1),new Vec3(.04,.02,-.08));
        for(int t=0;t<120;t++){rig.step(.05,new Vec3(0,-9.81,0));for(var p:rig.parts())exact(p.body());}
    }
}

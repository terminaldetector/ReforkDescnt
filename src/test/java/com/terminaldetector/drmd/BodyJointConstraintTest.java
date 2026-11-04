package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyJointConstraintTest {
    static final Vec3 ZERO=new Vec3(0,0,0);static final double INF=Double.POSITIVE_INFINITY;
    private D6PhysicsBody body(double mass){return new D6PhysicsBody().withMass(mass).withInertia(D6Mat3.diagonal(2,3,4));}
    private BodyJointConstraint.Config config(BodyJointConstraint.Kind kind,double speed,double force){return new BodyJointConstraint.Config(kind,ZERO,ZERO,Quat.IDENTITY,Quat.IDENTITY,-INF,INF,speed,force,INF,INF);}
    private void solve(BodyJointConstraint c){for(int i=0;i<32;i++)c.solve();}
    private void same(Vec3 a,Vec3 b){assertTrue(a.minus(b).length()<1e-9,a+" != "+b);}
    private Vec3 momentum(D6PhysicsBody b){return b.linearVelocity().scaled(b.mass());}
    private Vec3 angular(D6PhysicsBody b){return b.angularMomentum().plus(b.position().cross(momentum(b)));}
    @Test void fixedJointSharesLinearAndAngularMotionWithoutResettingAWorldPose() {
        var a=body(2);var b=body(3).withLinearVelocity(new Vec3(3,2,1)).withAngularVelocity(new Vec3(1,2,3));
        Vec3 p=momentum(a).plus(momentum(b)),l=angular(a).plus(angular(b));var c=new BodyJointConstraint(a,b,config(BodyJointConstraint.Kind.FIXED,0,0),.05);solve(c);
        same(a.linearVelocity(),b.linearVelocity());same(a.angularVelocity(),b.angularVelocity());same(p,momentum(a).plus(momentum(b)));same(l,angular(a).plus(angular(b)));same(a.position(),ZERO);same(b.position(),ZERO);
    }
    @Test void hingeAllowsOnlyItsActualRotatedAxisToTurn() {
        var q=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.rotationByRadians(new Vec3(0,1,0),.7);
        var a=body(0).withRotation(q);var b=body(2).withRotation(q).withAngularVelocity(q.rotate(new Vec3(2,3,4)));
        var c=new BodyJointConstraint(a,b,config(BodyJointConstraint.Kind.HINGE,0,0),.05);solve(c);same(b.angularVelocity(),q.rotate(new Vec3(2,0,0)));same(a.angularMomentum(),ZERO);
    }
    @Test void sliderRetainsAxialTranslationAndLocksLateralAndAngularMotion() {
        var a=body(0);var b=body(2).withLinearVelocity(new Vec3(2,3,4)).withAngularVelocity(new Vec3(1,2,3));
        var c=new BodyJointConstraint(a,b,config(BodyJointConstraint.Kind.SLIDER,0,0),.05);solve(c);same(b.linearVelocity(),new Vec3(2,0,0));same(b.angularVelocity(),ZERO);
    }
    @Test void motorForceAllowanceIsSharedAcrossAllIterationsOfTheSameStep() {
        var a=body(2);var b=body(2);var c=new BodyJointConstraint(a,b,config(BodyJointConstraint.Kind.MOTOR,1.5,5),.05);solve(c);
        assertEquals(.25,c.angularImpulse().x(),1e-9);assertEquals(.25,b.angularVelocity().x()-a.angularVelocity().x(),1e-9);same(angular(a).plus(angular(b)),ZERO);
    }
    @Test void aPredictedSliderLimitStopsOnlyTheVelocityThatWouldCrossIt() {
        var a=body(0);var b=body(2).withPosition(new Vec3(.9,0,0)).withLinearVelocity(new Vec3(10,0,0));
        var cfg=new BodyJointConstraint.Config(BodyJointConstraint.Kind.SLIDER,ZERO,ZERO,Quat.IDENTITY,Quat.IDENTITY,-1,1,0,0,INF,INF);
        solve(new BodyJointConstraint(a,b,cfg,.05));assertEquals(2,b.linearVelocity().x(),1e-9);b.step(.05);assertEquals(1,b.position().x(),1e-9);
    }
    @Test void aBrokenJointCannotResetOrKeepConstrainingEitherBody() {
        var a=body(2);var b=body(3).withLinearVelocity(new Vec3(20,0,0));var cfg=new BodyJointConstraint.Config(BodyJointConstraint.Kind.FIXED,ZERO,ZERO,Quat.IDENTITY,Quat.IDENTITY,-INF,INF,0,0,1,INF);
        var c=new BodyJointConstraint(a,b,cfg,.05);solve(c);assertTrue(c.broken());same(a.linearVelocity(),ZERO);same(b.linearVelocity(),new Vec3(20,0,0));var p=b.position();b.step(.05);assertEquals(p.x()+1,b.position().x(),1e-9);
    }
    @Test void positionalRepairUsesEqualOppositeLoadsAtOneWorldPoint() {
        var a=body(2).withPosition(new Vec3(-1,0,0));var b=body(3).withPosition(new Vec3(1,.1,0));
        Vec3 p=momentum(a).plus(momentum(b)),l=angular(a).plus(angular(b));solve(new BodyJointConstraint(a,b,config(BodyJointConstraint.Kind.FIXED,0,0),.05));same(p,momentum(a).plus(momentum(b)));same(l,angular(a).plus(angular(b)));
    }
    @Test void aStaticPairAndInvalidLimitsCannotProduceANonFiniteImpulse() {
        var a=body(0);var b=body(0);var c=new BodyJointConstraint(a,b,config(BodyJointConstraint.Kind.FIXED,0,0),.05);solve(c);same(c.linearImpulse(),ZERO);same(c.angularImpulse(),ZERO);
        assertThrows(IllegalArgumentException.class,()->new BodyJointConstraint(a,b,config(BodyJointConstraint.Kind.FIXED,0,0),1));
        assertThrows(IllegalArgumentException.class,()->new BodyJointConstraint.Config(BodyJointConstraint.Kind.MOTOR,ZERO,ZERO,Quat.IDENTITY,Quat.IDENTITY,-INF,INF,0,Double.NaN,INF,INF));
    }
    @Test void offsetFixedAnchorsStayTogetherDuringLongForcedSixAxisMotion() {
        var a=body(2).withPosition(new Vec3(-1,0,0));var b=body(3).withPosition(new Vec3(1,0,0));
        var cfg=new BodyJointConstraint.Config(BodyJointConstraint.Kind.FIXED,new Vec3(1,0,0),new Vec3(-1,0,0),Quat.IDENTITY,Quat.IDENTITY,-INF,INF,0,0,INF,INF);
        double error=0;
        for(int i=0;i<2000;i++) {
            b.applyForce(new Vec3(2,-3,1));b.applyTorque(new Vec3(.3,.2,-.4));a.step(.005);b.step(.005);solve(new BodyJointConstraint(a,b,cfg,.005));
            Vec3 pa=a.position().plus(a.rotation().rotate(cfg.anchorA())),pb=b.position().plus(b.rotation().rotate(cfg.anchorB()));error=Math.max(error,pa.minus(pb).length());
            assertTrue(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(a.rotation(),b.rotation()))>.999,"fixed relative orientation drifted");
        }
        assertTrue(error<.005,"anchor drift under combined translation/spin: "+error);
    }
    @Test void aFreeHingeCanOrbitItsAnchorWithoutWeldingOrAddingEnergy() {
        var a=body(0);var b=body(2).withPosition(new Vec3(1,0,0)).withLinearVelocity(new Vec3(0,1,0)).withAngularVelocity(new Vec3(0,0,1));
        var frame=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.rotationByRadians(new Vec3(0,1,0),-Math.PI/2);
        var cfg=new BodyJointConstraint.Config(BodyJointConstraint.Kind.HINGE,ZERO,new Vec3(-1,0,0),frame,frame,-INF,INF,0,0,INF,INF);
        double initial=.5*(b.mass()*b.linearVelocity().lengthSquared()+b.angularVelocity().dot(b.angularMomentum())),maxError=0,minX=1;
        for(int i=0;i<4000;i++) {
            b.step(.005);solve(new BodyJointConstraint(a,b,cfg,.005));
            maxError=Math.max(maxError,b.position().plus(b.rotation().rotate(cfg.anchorB())).length());minX=Math.min(minX,b.position().x());
            double energy=.5*(b.mass()*b.linearVelocity().lengthSquared()+b.angularVelocity().dot(b.angularMomentum()));assertTrue(energy<initial*1.05,"passive hinge injected energy: "+energy);
        }
        assertTrue(maxError<.005&&minX<-.9,"hinge must remain anchored and complete real rotations: error="+maxError+" minX="+minX);
    }
    @Test void aPoweredSliderRemainsInsideItsStopsOverThousandsOfSteps() {
        var a=body(0);var b=body(2);var cfg=new BodyJointConstraint.Config(BodyJointConstraint.Kind.SLIDER,ZERO,ZERO,Quat.IDENTITY,Quat.IDENTITY,-1,1,5,10,INF,INF);
        for(int i=0;i<2000;i++){solve(new BodyJointConstraint(a,b,cfg,.01));b.step(.01);assertTrue(b.position().x()<=1+1e-8&&b.position().x()>=-1-1e-8);}
        assertEquals(1,b.position().x(),1e-8);assertEquals(0,b.linearVelocity().length(),1e-8);
    }

    @Test void splittingForceAndPoseIntegrationPreservesTheExistingStepExactly() {
        var a=body(2).withLinearVelocity(new Vec3(1,2,3)).withAngularVelocity(new Vec3(.2,.3,.4));
        var b=body(2).withLinearVelocity(new Vec3(1,2,3)).withAngularVelocity(new Vec3(.2,.3,.4));
        for(int i=0;i<3000;i++) {
            var force=new Vec3(Math.sin(i*.1),-2,.3);var torque=new Vec3(.2,Math.cos(i*.03),-.1);
            a.applyForce(force);b.applyForce(force);a.applyTorque(torque);b.applyTorque(torque);
            a.step(.01);b.integrateForces(.01);b.integratePose(.01);
            assertEquals(a.position(),b.position());assertEquals(a.rotation(),b.rotation());assertEquals(a.angularMomentum(),b.angularMomentum());assertEquals(a.linearVelocity(),b.linearVelocity());
        }
    }
    @Test void poseIntegrationCannotReplayAForceOrMoveBeforeConstraints() {
        var a=body(2);a.applyForce(new Vec3(20,0,0));a.integrateForces(.05);same(a.position(),ZERO);same(a.linearVelocity(),new Vec3(.5,0,0));
        a.integratePose(.05);a.integratePose(.05);same(a.position(),new Vec3(.05,0,0));same(a.linearVelocity(),new Vec3(.5,0,0));
        a.integrateForces(.05);same(a.linearVelocity(),new Vec3(.5,0,0));
    }

}

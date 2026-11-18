package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class D6ControlFrameTest {
    private static final Vec3 ZERO = new Vec3(0,0,0), COM = new Vec3(.5,.5,.5);
    private static final UUID ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final D6ControlFrame.Key KEY = new D6ControlFrame.Key(ID,90,4,13,2,7);
    private static final List<Vec3> MOUNTS = List.of(new Vec3(-2,.5,-2), new Vec3(-2,.5,3),
            new Vec3(3,.5,-2), new Vec3(3,.5,3));
    private static void vector(Vec3 expected, Vec3 actual) {
        assertTrue(expected.minus(actual).length() < 1e-10, () -> expected + " != " + actual);
    }
    private static D6PhysicsBody body() {
        return new D6PhysicsBody().withMass(10).withInertia(D6Mat3.diagonal(2,3,4))
                .withLinearVelocity(new Vec3(1,2,3)).withAngularMomentum(new Vec3(4,5,6));
    }
    private static D6ControlFrame frame(Vec3 force, Vec3 torque) {
        return new D6ControlFrame(KEY,COM,new D6ThrusterMixer(MOUNTS,1000).mix(COM,force,torque,i -> true));
    }

    @Test void accumulatingACommandIsNotAnImmediateImpulseOrPoseUpdate() {
        var b=body(); var v=b.linearVelocity();var l=b.angularMomentum();var p=b.position();var q=b.rotation();
        assertTrue(frame(new Vec3(20,0,0),ZERO).accumulate(b,KEY));
        vector(v,b.linearVelocity());vector(l,b.angularMomentum());vector(p,b.position());assertEquals(q,b.rotation());
        b.integrateForces(.05);vector(v.plus(new Vec3(.1,0,0)),b.linearVelocity());
    }

    @Test void realPointForcesRotateAboutTheMaterialComWithoutSyntheticTorque() {
        var b=body().withRotation(ImmPtlQuaternions.rotationByDegrees(new Vec3(1,2,3).normalized(),123));
        var q=b.rotation();var v=b.linearVelocity();var l=b.angularMomentum();
        var force=new Vec3(12,-8,4);var torque=new Vec3(7,3,-5);
        assertTrue(frame(force,torque).accumulate(b,KEY));b.integrateForces(.05);
        vector(v.plus(q.rotate(force).scaled(.005)),b.linearVelocity());
        vector(l.plus(q.rotate(torque).scaled(.05)),b.angularMomentum());
    }

    @Test void theSameFrameReappliesForcesAcrossSubstepsWithoutChangingTotalImpulse() {
        var force=new Vec3(12,-8,4);var torque=new Vec3(7,3,-5);var f=frame(force,torque);
        for(int steps:new int[]{1,2,16,128}) {
            var b=body();var v=b.linearVelocity();var l=b.angularMomentum();
            for(int i=0;i<steps;i++){assertTrue(f.accumulate(b,KEY));b.integrateForces(.05/steps);}
            vector(v.plus(force.scaled(.005)),b.linearVelocity());vector(l.plus(torque.scaled(.05)),b.angularMomentum());
        }
    }

    @Test void anUnknownRollbackRestoresBeforeThrustAndOldFramesCannotPowerResume() {
        var b=body();var v=b.linearVelocity();var l=b.angularMomentum();var p=b.position();
        var before=b.snapshot();var f=frame(new Vec3(200,0,0),new Vec3(1,2,3));
        assertTrue(f.accumulate(b,KEY));b.step(.05);b.restore(before); // Same pre-force ordering as the required adapter hook.
        var resumed=new D6ControlFrame.Key(ID,91,4,13,2,7);
        assertFalse(f.accumulate(b,resumed));b.integrateForces(.05);
        vector(v,b.linearVelocity());vector(l,b.angularMomentum());vector(p,b.position());
    }

    @Test void aJointRetryRestoresForcesBeforeApplyingTheSmallerActualDt() {
        var b=body();var v=b.linearVelocity();var l=b.angularMomentum();var before=b.snapshot();
        var f=frame(new Vec3(20,10,0),new Vec3(2,3,4));
        f.accumulate(b,KEY);b.integrateForces(.05);b.restore(before);
        f.accumulate(b,KEY);b.integrateForces(.025);
        vector(v.plus(new Vec3(.05,.025,0)),b.linearVelocity());
        vector(l.plus(new Vec3(.05,.075,.1)),b.angularMomentum());
    }

    @Test void staleTickLeaseCoreCellGenerationBodyOrMaterialCannotAccumulate() {
        var f=frame(new Vec3(200,0,0),new Vec3(1,2,3));
        var keys=List.of(new D6ControlFrame.Key(ID,89,4,13,2,7),new D6ControlFrame.Key(ID,91,4,13,2,7),
                new D6ControlFrame.Key(ID,90,5,13,2,7),new D6ControlFrame.Key(ID,90,4,14,2,7),
                new D6ControlFrame.Key(ID,90,4,13,3,7),new D6ControlFrame.Key(ID,90,4,13,2,8),
                new D6ControlFrame.Key(new UUID(0,1),90,4,13,2,7));
        for(var key:keys){var b=body();var v=b.linearVelocity();var l=b.angularMomentum();
            assertFalse(f.accumulate(b,key));b.integrateForces(.05);vector(v,b.linearVelocity());vector(l,b.angularMomentum());}
        var b=body();assertFalse(f.accumulate(b,null));
    }

    @Test void outputIdentityBoundsFinitenessAndDefensiveCopyAreCheckedBeforeUse() {
        var out=new D6ThrusterMixer.Output(0,COM,new Vec3(20,0,0));var mutable=new ArrayList<>(List.of(out));
        var f=new D6ControlFrame(KEY,COM,mutable);mutable.clear();var b=body();f.accumulate(b,KEY);b.integrateForces(.05);
        vector(new Vec3(1.1,2,3),b.linearVelocity());
        assertThrows(IllegalArgumentException.class,()->new D6ControlFrame(KEY,COM,List.of(out,out)));
        assertThrows(IllegalArgumentException.class,()->new D6ControlFrame(KEY,COM,List.of(new D6ThrusterMixer.Output(16,COM,ZERO))));
        assertThrows(IllegalArgumentException.class,()->new D6ControlFrame(KEY,COM,List.of(new D6ThrusterMixer.Output(0,COM,new Vec3(Double.NaN,0,0)))));
        assertThrows(IllegalArgumentException.class,()->new D6ControlFrame(KEY,new Vec3(Double.POSITIVE_INFINITY,0,0),List.of()));
        assertThrows(IllegalArgumentException.class,()->new D6ControlFrame(KEY,COM,Collections.nCopies(17,out)));
    }

    @Test void damagedFixedRatedEnginesLoseForceAndArmorCannotManufactureCapacity() {
        var mixer=new D6ThrusterMixer(MOUNTS,100);
        var f=new D6ControlFrame(KEY,COM,mixer.mix(COM,new Vec3(0,100000,0),ZERO,i -> i!=0));
        var light=new D6PhysicsBody().withMass(100).withInertia(D6Mat3.IDENTITY);
        var armored=new D6PhysicsBody().withMass(1000).withInertia(D6Mat3.IDENTITY);
        f.accumulate(light,KEY);f.accumulate(armored,KEY);light.integrateForces(.05);armored.integrateForces(.05);
        assertEquals(.15,light.linearVelocity().y(),1e-10);assertEquals(.015,armored.linearVelocity().y(),1e-10);
        assertTrue(light.angularMomentum().length()>0,"the missing real corner motor leaves physical torque");
        assertThrows(IllegalArgumentException.class,()->new D6ThrusterMixer(List.of(ZERO,new Vec3(1,0,0),new Vec3(2,0,0)),100));
    }
}

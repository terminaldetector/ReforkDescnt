package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D6ThrusterMixerTest {
    private static final Vec3 ZERO=new Vec3(0,0,0),CENTRE=new Vec3(.5,-1.5,.5);
    private static final List<Vec3> MOUNTS=List.of(new Vec3(-4.5,-1.5,-4.5),new Vec3(-4.5,-1.5,5.5),
        new Vec3(5.5,-1.5,-4.5),new Vec3(5.5,-1.5,5.5));
    private static void vector(Vec3 expected,Vec3 actual){assertTrue(expected.minus(actual).length()<1e-9,()->expected+" != "+actual);}
    private static Vec3 force(List<D6ThrusterMixer.Output> out){return out.stream().map(D6ThrusterMixer.Output::force).reduce(ZERO,Vec3::plus);}
    private static Vec3 torque(List<D6ThrusterMixer.Output> out,Vec3 centre){return out.stream().map(o->o.localPoint().minus(centre).cross(o.force())).reduce(ZERO,Vec3::plus);}

    @Test void completeBankProducesRequestedWrenchAboutAnOffsetMaterialCentre() {
        var centre=new Vec3(2,-.1,-3);var desiredForce=new Vec3(12,3,-9);var desiredTorque=new Vec3(4,-5,6);
        var out=new D6ThrusterMixer(MOUNTS,1000).mix(centre,desiredForce,desiredTorque,i->true);
        assertEquals(4,out.size());vector(desiredForce,force(out));vector(desiredTorque,torque(out,centre));
    }
    @Test void saturationCapsEachRealMotorAndKeepsTheCompleteWrenchDirection() {
        var centre=new Vec3(2,-.1,-3);var f=new Vec3(12000,3000,-9000);var t=new Vec3(4000,-5000,6000);
        var out=new D6ThrusterMixer(MOUNTS,10).mix(centre,f,t,i->true);
        assertTrue(out.stream().allMatch(o->o.force().length()<=10.000000001));
        assertEquals(10,out.stream().mapToDouble(o->o.force().length()).max().orElseThrow(),1e-9);
        double scale=force(out).dot(f)/f.lengthSquared();assertTrue(scale>0&&scale<1);
        vector(f.scaled(scale),force(out));vector(t.scaled(scale),torque(out,centre));
    }
    @Test void lostMotorLosesItsForceAndLeavesThePhysicalOffCentreTorque() {
        var bank=new D6ThrusterMixer(MOUNTS,100);var full=bank.mix(CENTRE,new Vec3(0,40,0),ZERO,i->true);
        var damaged=bank.mix(CENTRE,new Vec3(0,40,0),ZERO,i->i!=0);
        assertEquals(3,damaged.size());vector(new Vec3(0,30,0),force(damaged));vector(new Vec3(-50,0,50),torque(damaged,CENTRE));
        for(var o:damaged)vector(full.get(o.mount()).force(),o.force());
    }
    @Test void rotatedPointImpulsesChangeActualWorldMomentumWithoutAnExtraTorqueChannel() {
        var q=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByDegrees(new Vec3(1,0,0),67),ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),123));
        var v=new Vec3(2,-3,4);var l=new Vec3(4,5,6);var f=new Vec3(4,5,6);var t=new Vec3(-3,2,1);
        var body=new D6PhysicsBody().withMass(10).withInertia(D6Mat3.diagonal(2,3,4)).withRotation(q).withLinearVelocity(v).withAngularMomentum(l);
        for(var o:new D6ThrusterMixer(MOUNTS,1000).mix(CENTRE,f,t,i->true))body.applyImpulse(q.rotate(o.force()).scaled(.05),q.rotate(o.localPoint().minus(CENTRE)));
        vector(v.plus(q.rotate(f).scaled(.05/10)),body.linearVelocity());vector(l.plus(q.rotate(t).scaled(.05)),body.angularMomentum());
    }
    @Test void noMotorsMeansNoImpulseAndMassDoesNotIncreaseRatedMotorPower() {
        var bank=new D6ThrusterMixer(MOUNTS,100);assertTrue(bank.mix(CENTRE,new Vec3(1,2,3),new Vec3(4,5,6),i->false).isEmpty());
        var small=new D6PhysicsBody().withMass(100).withInertia(D6Mat3.IDENTITY);var heavy=new D6PhysicsBody().withMass(1000).withInertia(D6Mat3.IDENTITY);
        for(var o:bank.mix(CENTRE,new Vec3(0,100000,0),ZERO,i->true)){
            small.applyImpulse(o.force().scaled(.05),o.localPoint().minus(CENTRE));heavy.applyImpulse(o.force().scaled(.05),o.localPoint().minus(CENTRE));
        }
        assertEquals(.2,small.linearVelocity().y(),1e-9);assertEquals(.02,heavy.linearVelocity().y(),1e-9);
    }
    @Test void singularOrNonfiniteLayoutsAndCommandsCannotEnterTheMotorSolver() {
        assertThrows(IllegalArgumentException.class,()->new D6ThrusterMixer(List.of(ZERO,new Vec3(1,0,0),new Vec3(2,0,0)),100));
        assertThrows(IllegalArgumentException.class,()->new D6ThrusterMixer(MOUNTS,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new D6ThrusterMixer(MOUNTS,100).mix(CENTRE,new Vec3(Double.NaN,0,0),ZERO,i->true));
    }
}

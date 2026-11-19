package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollRestSeparationTest {
    private static final Vec3 ZERO=new Vec3(0,0,0),G=new Vec3(0,-9.81,0);
    private static RagdollSimulation box(Vec3 p,Vec3 h,Quat q,Vec3 v){return new RagdollSimulation(new RagdollRig(List.of(new RagdollRig.Segment("actual",2,h,ZERO)),List.of()),p,q,v);}
    private static boolean separate(RagdollSimulation a,RagdollSimulation b){return RagdollRestSeparation.separated(RagdollRestSeparation.sweptParts(a,G).orElseThrow(),RagdollRestSeparation.sweptParts(b,G).orElseThrow());}
    @Test void aBroadEightMetreNeighbourCanHaveDisjointActualMovingHulls(){
        var a=box(ZERO,new Vec3(.3,.3,.3),Quat.IDENTITY,ZERO);var b=box(new Vec3(3,0,0),new Vec3(.3,.3,.3),Quat.IDENTITY,new Vec3(-.2,0,0));
        assertTrue(separate(a,b));assertEquals(new Vec3(-.2,0,0),b.parts().getFirst().body().linearVelocity());
    }
    @Test void aFastClosingHullCannotSkipAcrossAClearEndpointInThePrediction(){
        var a=box(ZERO,new Vec3(.3,.3,.3),Quat.IDENTITY,ZERO);var b=box(new Vec3(3,0,0),new Vec3(.3,.3,.3),Quat.IDENTITY,new Vec3(-120,0,0));
        assertFalse(separate(a,b),"swept world bounds cross the resting collider even though the final centre is -3");
    }
    @Test void authoredRotatedLongPartsUseAllThreeWorldAxes(){
        var a=box(ZERO,new Vec3(.2,.2,.2),Quat.IDENTITY,ZERO);var b=box(new Vec3(1,0,0),new Vec3(2,.1,.1),Quat.IDENTITY,ZERO);
        assertFalse(separate(a,b));b.parts().getFirst().body().withRotation(new Quat(0,0,Math.sqrt(.5),Math.sqrt(.5)));assertTrue(separate(a,b),"90deg long axis runs along world Y; centre distance alone is insufficient");
    }
    @Test void spinAndPendingWorldWrenchExpandTheEnvelopeWithoutConsumingState(){
        var a=box(ZERO,new Vec3(.2,.2,.2),Quat.IDENTITY,ZERO);var b=box(new Vec3(2,0,0),new Vec3(.3,.3,.3),Quat.IDENTITY,ZERO);var body=b.parts().getFirst().body();assertTrue(separate(a,b));
        body.applyForce(new Vec3(-2000,0,0));var saved=body.snapshot();assertFalse(separate(a,b));assertTrue(body.matches(saved),"query must not integrate/clear pending force");
        var spin=box(new Vec3(.9,0,0),new Vec3(.3,.3,.3),Quat.IDENTITY,ZERO);var rotating=spin.parts().getFirst().body();rotating.withInertia(D6Mat3.diagonal(.01,2,5)).withAngularMomentum(new Vec3(0,.2,0));saved=rotating.snapshot();assertFalse(separate(a,spin),"anisotropic inertia uses a lower eigenvalue bound rather than only the instantaneous omega axis");assertTrue(rotating.matches(saved));
        var c=box(new Vec3(.9,0,0),new Vec3(.3,.3,.3),Quat.IDENTITY,ZERO);c.parts().getFirst().body().applyTorque(new Vec3(0,10000,0));saved=c.parts().getFirst().body().snapshot();assertFalse(separate(a,c));assertTrue(c.parts().getFirst().body().matches(saved));
    }
    @Test void unknownNonFiniteAndDegeneratePhysicalStateCannotExcludeANeighbour(){
        var a=box(ZERO,new Vec3(.3,.3,.3),Quat.IDENTITY,ZERO);assertTrue(RagdollRestSeparation.sweptParts(a,new Vec3(Double.NaN,0,0)).isEmpty());
        a.parts().getFirst().body().withInertia(D6Mat3.ZERO);assertTrue(RagdollRestSeparation.sweptParts(a,G).isEmpty());assertFalse(RagdollRestSeparation.separated(List.of(),List.of()));
        assertFalse(RagdollRestSeparation.separated(List.of(new RagdollRestSeparation.Bounds(0,0,0,1,1,1)),List.of(new RagdollRestSeparation.Bounds(Double.NaN,0,0,1,1,1))));
    }
}

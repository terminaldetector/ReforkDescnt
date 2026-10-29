package com.terminaldetector.drmd;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.BodyStepBudget;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BodyStepBudgetTest {
    private final Vec3 zero=new Vec3(0,0,0);
    private D6PhysicsBody body(){return new D6PhysicsBody().withMass(32768).withInertia(D6Mat3.diagonal(1e6,4e7,4e7)).withLimits(50,2);}
    @Test void stoppedLargeHullNeedsOneStep(){assertEquals(1,BodyStepBudget.steps(body(),zero,140,false));}
    @Test void tinyStabilizerDoesNotPretendTheHullIsSpinningAtFullSpeed(){
        var b=body().withLinearVelocity(new Vec3(2,0,0));
        assertEquals(1,BodyStepBudget.steps(b,new Vec3(.01,.01,.01),140,false));
    }
    @Test void fastTranslationAndRotationKeepCornerTravelBelowTheSamePoint15Budget(){
        var b=body().withLinearVelocity(new Vec3(50,0,0)).withAngularVelocity(new Vec3(2,0,0));
        int n=BodyStepBudget.steps(b,zero,200,false);assertTrue(n>100&&n<=256);assertTrue((50+400)*.05/n<=.15);
    }
    @Test void torqueAndGravityAreIncludedBeforeTheFirstIntegrationStep(){
        assertTrue(BodyStepBudget.steps(body(),new Vec3(4e7,0,0),100,true)>50);
        assertEquals(1,BodyStepBudget.steps(body(),zero,100,true));
    }
    @Test void expandedTranslationStillBoundsEveryLargeHullCornerWithoutASlowHullPenalty() {
        var b=body().withLimits(512,2).withLinearVelocity(new Vec3(512,0,0)).withAngularVelocity(new Vec3(2,0,0));
        int n=BodyStepBudget.steps(b,zero,277,false);assertTrue(n>256&&n<=512);
        assertTrue((512+554)*.05/n<=.15,"the larger speed limit must also expand the real collision step budget");
        assertEquals(1,BodyStepBudget.steps(body().withLimits(512,2),zero,277,false),"stopped hulls do not pay for the expanded limit");
    }
}

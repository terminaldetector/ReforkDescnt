package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.boarding.BodyMotion;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MovingProjectileTraceTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private CompoundCollider hull(){return new CompoundCollider(List.of(new BodyBounds(new Vec3(-1,-1,-1),new Vec3(1,1,0))));}
    private BodyMotion motion(Vec3 start,Vec3 end){return new BodyMotion(1,hull(),ZERO,start,Quat.IDENTITY,end,Quat.IDENTITY,new D6PhysicsBody().withMass(10));}
    @Test void outgoingRoundDoesNotHitAFalseOverlapWithTheEndPose() {
        var start=new Vec3(0,0,.15);var end=new Vec3(0,0,1.95);var movement=new Vec3(0,0,.4);
        assertTrue(hull().raycast(start.minus(movement),end.minus(movement)).isPresent(),"old end-pose-only trace reports a source hit");
        assertTrue(motion(ZERO,movement).traceProjectile(start,end).isEmpty());
    }
    @Test void movingMaterialCanReallyInterceptAStationaryPoint() {
        var hit=motion(new Vec3(0,0,-2),new Vec3(0,0,2)).traceProjectile(ZERO,ZERO).orElseThrow();
        assertEquals(.5,hit.fraction(),1e-10);assertTrue(hit.worldPoint().length()<1e-10);assertEquals(0,hit.localPoint().z(),1e-10);
    }
    @Test void aReturningRoundStillHitsTheActualMaterialAtItsTimeOfImpact() {
        var hit=motion(ZERO,new Vec3(0,0,.4)).traceProjectile(new Vec3(0,0,2),new Vec3(0,0,-2)).orElseThrow();
        assertEquals(2/4.4,hit.fraction(),1e-10);assertEquals(.4*hit.fraction(),hit.worldPoint().z(),1e-10);assertEquals(0,hit.localPoint().z(),1e-10);
    }
    @Test void aFullyOrientedHullKeepsOutgoingClearanceAndIncomingContacts() {
        var before=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByDegrees(new Vec3(1,0,0),67),ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),123));
        var after=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByRadians(new Vec3(0,1,0),.1),before);
        var motion=new BodyMotion(1,hull(),ZERO,ZERO,before,new Vec3(.4,.1,.2),after,new D6PhysicsBody().withMass(10));
        assertTrue(motion.traceProjectile(motion.point(new Vec3(0,0,.15),0),motion.point(new Vec3(0,0,2),1)).isEmpty());
        assertTrue(motion.traceProjectile(motion.point(new Vec3(0,0,2),0),motion.point(new Vec3(0,0,-2),1)).isPresent());
    }
}

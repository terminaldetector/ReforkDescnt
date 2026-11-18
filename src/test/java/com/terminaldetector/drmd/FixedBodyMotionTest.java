package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FixedBodyMotionTest {
    @Test void anOffsetAnisotropicMemberUsesTheSameThreeAxisCarrierTimeForAllPointVelocities(){
        var zero=new Vec3(0,0,0);var shape=new CompoundCollider(List.of(new BodyBounds(new Vec3(-.5,-.5,-.5),new Vec3(.5,.5,.5))));
        var before=ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,-3).normalized(),.7);
        var after=ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByRadians(new Vec3(2,-1,3).normalized(),.09),before).normalized();
        var source=new D6PhysicsBody().withMass(12).withInertia(D6Mat3.diagonal(2,9,15)).withPosition(new Vec3(4,5,6)).withRotation(after)
            .withLinearVelocity(new Vec3(3,-.4,2)).withAngularVelocity(new Vec3(.7,-.4,.9));
        var owner=new BodyMotion(1,shape,zero,new Vec3(3.85,5.02,5.9),before,source.position(),after,source,BodyGravityField.NONE);
        var offset=new Vec3(2,1,-3);var relative=ImmPtlQuaternions.rotationByRadians(new Vec3(-2,4,1).normalized(),.6);
        var material=new D6PhysicsBody().withMass(3).withInertia(D6Mat3.diagonal(1,3,8));
        var child=BodyMotion.fixed(2,shape,zero,material,BodyGravityField.NONE,false,owner,offset,relative);
        for(double t:new double[]{0,.2,.5,.8,1}){
            var root=owner.proxy(t);var expected=owner.position(t).plus(owner.rotation(t).rotate(offset));var actual=child.proxy(t);
            assertTrue(child.position(t).minus(expected).length()<1e-12,"constrained COM follows the curved common trajectory");
            assertTrue(Math.abs(ImmPtlQuaternions.dot(child.rotation(t),ImmPtlQuaternions.hamiltonProduct(owner.rotation(t),relative).normalized()))>1-1e-12);
            assertTrue(actual.angularVelocity().minus(root.angularVelocity()).length()<1e-12,"child inertia must not invent a different substep omega");
            var point=expected.plus(child.rotation(t).rotate(new Vec3(.2,-.3,.4)));
            assertTrue(child.surfaceVelocity(point,t).minus(owner.surfaceVelocity(point,t)).length()<1e-11,"all material point velocities belong to the same rigid frame at t="+t);
        }
        assertTrue(child.travelBound()>owner.position(1).minus(owner.position(0)).length(),"offset arc is included in the bounded contact step budget");
    }
}

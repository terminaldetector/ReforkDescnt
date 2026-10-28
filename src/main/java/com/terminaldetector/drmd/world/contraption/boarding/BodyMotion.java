package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;

/** One tick's immutable geometry trajectory, with contact reaction accumulated on the existing body. */
public final class BodyMotion {
    public final int id;
    private final CompoundCollider shape;
    private final Vec3 localCom,from,to;
    private final Quat before,after;
    private final D6PhysicsBody dynamics;
    private final Vec3 velocity,momentum;
    private Vec3 reactionVelocity=new Vec3(0,0,0),reactionMomentum=new Vec3(0,0,0);

    public BodyMotion(int id,CompoundCollider shape,Vec3 localCom,Vec3 from,Quat before,Vec3 to,Quat after,D6PhysicsBody dynamics) {
        this.id=id;this.shape=shape;this.localCom=localCom;this.from=from;this.to=to;
        this.before=before;this.after=after;this.dynamics=dynamics;
        velocity=dynamics.linearVelocity();momentum=dynamics.angularMomentum();
    }
    public Vec3 position(double t){return from.plus(to.minus(from).scaled(t));}
    public Quat rotation(double t){return ImmPtlQuaternions.interpolate(before,after,t).normalized();}
    public BodyCollisionQuery query(double t){return new BodyCollisionQuery(shape,position(t),localCom,rotation(t));}
    public D6PhysicsBody proxy(double t) {
        return new D6PhysicsBody().withMass(dynamics.mass()).withInertia(dynamics.inertia()).withPosition(position(t))
            .withRotation(rotation(t)).withLinearVelocity(velocity.plus(reactionVelocity))
            .withAngularMomentum(momentum.plus(reactionMomentum));
    }
    public Vec3 surfaceVelocity(Vec3 point,double t) {var p=proxy(t);return p.velocityAtPoint(point.minus(p.position()));}
    public void react(Vec3 impulse,Vec3 offset) {
        if(dynamics.mass()<=1e-9)return;
        dynamics.applyImpulse(impulse,offset);
        reactionVelocity=reactionVelocity.plus(impulse.scaled(1/dynamics.mass()));
        reactionMomentum=reactionMomentum.plus(offset.cross(impulse));
    }
    public double travelBound() {
        double dot=Math.min(1,Math.abs(ImmPtlQuaternions.dot(before,after)));
        double radius=shape.bounds().map(b->b.half().length()+b.centre().minus(localCom).length()).orElse(0.);
        return to.minus(from).length()+2*Math.acos(dot)*radius;
    }
}

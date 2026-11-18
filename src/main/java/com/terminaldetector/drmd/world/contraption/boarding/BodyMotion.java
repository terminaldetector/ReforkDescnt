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
    private final BodyGravityField gravity;
    private final Vec3 velocity,momentum;
    private final boolean paused;
    private final BodyMotion fixedCarrier;private final Vec3 fixedOffset;private final Quat fixedFrame;
    private Vec3 reactionVelocity=new Vec3(0,0,0),reactionMomentum=new Vec3(0,0,0);

    public BodyMotion(int id,CompoundCollider shape,Vec3 localCom,Vec3 from,Quat before,Vec3 to,Quat after,D6PhysicsBody dynamics) {
        this(id,shape,localCom,from,before,to,after,dynamics,BodyGravityField.NONE);
    }
    public BodyMotion(int id,CompoundCollider shape,Vec3 localCom,Vec3 from,Quat before,Vec3 to,Quat after,D6PhysicsBody dynamics,BodyGravityField gravity) {
        this(id,shape,localCom,from,before,to,after,dynamics,gravity,false);
    }
    public BodyMotion(int id,CompoundCollider shape,Vec3 localCom,Vec3 from,Quat before,Vec3 to,Quat after,D6PhysicsBody dynamics,BodyGravityField gravity,boolean paused) {
        this(id,shape,localCom,from,before,to,after,dynamics,gravity,paused,null,null,null);
    }
    private BodyMotion(int id,CompoundCollider shape,Vec3 localCom,Vec3 from,Quat before,Vec3 to,Quat after,D6PhysicsBody dynamics,BodyGravityField gravity,boolean paused,BodyMotion carrier,Vec3 offset,Quat frame) {
        fixedCarrier=carrier;fixedOffset=offset;fixedFrame=frame;
        this.paused=paused;this.gravity=java.util.Objects.requireNonNull(gravity);
        this.id=id;this.shape=shape;this.localCom=localCom;this.from=from;this.to=to;
        this.before=before;this.after=after;this.dynamics=dynamics;
        velocity=paused?new Vec3(0,0,0):dynamics.linearVelocity();momentum=paused?new Vec3(0,0,0):dynamics.angularMomentum();
    }
    /** Constrained FIXED member: every pose and point velocity uses the SAME carrier time. */
    public static BodyMotion fixed(int id,CompoundCollider shape,Vec3 localCom,D6PhysicsBody dynamics,BodyGravityField gravity,boolean paused,BodyMotion carrier,Vec3 offset,Quat frame) {
        java.util.Objects.requireNonNull(carrier);BodyBounds.finite(offset);new OrientedBox(new Vec3(0,0,0),new Vec3(1,1,1),frame);
        if(carrier.fixedCarrier!=null)throw new IllegalArgumentException("fixed motion must use the component anchor");
        Vec3 from=carrier.position(0).plus(carrier.rotation(0).rotate(offset)),to=carrier.position(1).plus(carrier.rotation(1).rotate(offset));
        Quat before=ImmPtlQuaternions.hamiltonProduct(carrier.rotation(0),frame).normalized(),after=ImmPtlQuaternions.hamiltonProduct(carrier.rotation(1),frame).normalized();
        return new BodyMotion(id,shape,localCom,from,before,to,after,dynamics,gravity,paused,carrier,offset,frame);
    }
    public java.util.Optional<Vec3> gravity(Vec3 point,double t) {return gravity.at(point,position(t),localCom,rotation(t));}
    public boolean hasGravity(){return gravity.enabled();}
    public Vec3 gravityUp(double t){return rotation(t).rotate(gravity.localAcceleration()).scaled(-1).normalized();}
    public Vec3 acceleration(double t){return rotation(t).rotate(gravity.localAcceleration());}
    public Vec3 point(Vec3 localOffset,double t){return position(t).plus(rotation(t).rotate(localOffset));}
    public Vec3 localOffset(Vec3 point,double t){return rotation(t).inverse().rotate(point.minus(position(t)));}
    public Vec3 position(double t){return fixedCarrier==null?from.plus(to.minus(from).scaled(t)):fixedCarrier.position(t).plus(fixedCarrier.rotation(t).rotate(fixedOffset));}
    public Quat rotation(double t){return fixedCarrier==null?ImmPtlQuaternions.interpolate(before,after,t).normalized():ImmPtlQuaternions.hamiltonProduct(fixedCarrier.rotation(t),fixedFrame).normalized();}
    public BodyCollisionQuery query(double t){return new BodyCollisionQuery(shape,position(t),localCom,rotation(t));}
    public D6PhysicsBody proxy(double t) {
        if(fixedCarrier!=null){
            var owner=fixedCarrier.proxy(t);var at=position(t);var body=new D6PhysicsBody().withMass(paused?0:dynamics.mass()).withInertia(dynamics.inertia()).withPosition(at).withRotation(rotation(t));
            if(!paused){body.withLinearVelocity(owner.velocityAtPoint(at.minus(owner.position())).plus(reactionVelocity)).withAngularVelocity(owner.angularVelocity());body.withAngularMomentum(body.angularMomentum().plus(reactionMomentum));}
            return body;
        }
        return new D6PhysicsBody().withMass(paused?0:dynamics.mass()).withInertia(dynamics.inertia()).withPosition(position(t))
            .withRotation(rotation(t)).withLinearVelocity(velocity.plus(reactionVelocity))
            .withAngularMomentum(momentum.plus(reactionMomentum));
    }
    public Vec3 surfaceVelocity(Vec3 point,double t) {var p=proxy(t);return p.velocityAtPoint(point.minus(p.position()));}
    public void react(Vec3 impulse,Vec3 offset) {
        if(paused||dynamics.mass()<=1e-9)return;
        dynamics.applyImpulse(impulse,offset);
        reactionVelocity=reactionVelocity.plus(impulse.scaled(1/dynamics.mass()));
        reactionMomentum=reactionMomentum.plus(offset.cross(impulse));
    }
    /** Point trace against this tick's actual material trajectory. Translation is exact;
     * rotation uses at most32 short local-space chords, retaining the existing immutable BVH. */
    public record RayHit(double fraction,Vec3 localPoint,Vec3 worldPoint) {}
    public java.util.Optional<RayHit> traceProjectile(Vec3 start,Vec3 end) {
        BodyBounds.finite(start);BodyBounds.finite(end);
        double angle=2*Math.acos(Math.min(1,Math.abs(ImmPtlQuaternions.dot(before,after))));
        int steps=Math.max(1,Math.min(32,(int)Math.ceil(angle/.005)));var delta=end.minus(start);
        for(int i=0;i<steps;i++) {
            double a=(double)i/steps,b=(double)(i+1)/steps;
            var from=localOffset(start.plus(delta.scaled(a)),a).plus(localCom);
            var to=localOffset(start.plus(delta.scaled(b)),b).plus(localCom);
            var hit=shape.raycast(from,to);
            if(hit.isPresent()) {double fraction=a+(b-a)*hit.get().fraction();return java.util.Optional.of(new RayHit(fraction,hit.get().point(),start.plus(delta.scaled(fraction))));}
        }
        return java.util.Optional.empty();
    }
    public double travelBound() {
        double dot=Math.min(1,Math.abs(ImmPtlQuaternions.dot(before,after)));
        double radius=shape.bounds().map(b->b.half().length()+b.centre().minus(localCom).length()).orElse(0.);
        return fixedCarrier==null?to.minus(from).length()+2*Math.acos(dot)*radius:fixedCarrier.position(1).minus(fixedCarrier.position(0)).length()+2*Math.acos(dot)*(fixedOffset.length()+radius);
    }
}

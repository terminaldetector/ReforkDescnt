package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import java.util.*;

/** Bilateral joints on existing D6 bodies. One instance is one bounded solver step.
 * No entity, block, renderer, teleport, integration or separate physics engine belongs here. */
public final class BodyJointConstraint {
    public enum Kind { FIXED,HINGE,SLIDER,MOTOR }
    /** Anchors are offsets from each COM in body axes. Frame X is the hinge/slider axis.
     * Slider bounds are metres; motor speed is m/s for a slider and rad/s for MOTOR.
     * Motor force/torque is per second, never a fresh allowance on every solver iteration. */
    public record Config(Kind kind,Vec3 anchorA,Vec3 anchorB,Quat frameA,Quat frameB,
                         double lower,double upper,double speed,double motorForce,double breakForce,double breakTorque) {
        public Config {
            Objects.requireNonNull(kind);BodyBounds.finite(anchorA);BodyBounds.finite(anchorB);
            frameA=valid(frameA);frameB=valid(frameB);
            if(Double.isNaN(lower)||Double.isNaN(upper)||lower==Double.POSITIVE_INFINITY||upper==Double.NEGATIVE_INFINITY||lower>upper
                ||!Double.isFinite(speed)||!Double.isFinite(motorForce)||motorForce<0
                ||Double.isNaN(breakForce)||breakForce<0||Double.isNaN(breakTorque)||breakTorque<0)throw new IllegalArgumentException("invalid joint limits");
        }
        private static Quat valid(Quat q) {
            Objects.requireNonNull(q);double norm=q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w();
            if(!Double.isFinite(norm)||norm<1e-16)throw new IllegalArgumentException("invalid joint frame");return q.normalized();
        }
    }
    private static final Vec3 ZERO=new Vec3(0,0,0),X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    private final D6PhysicsBody a,b;private final Vec3 ra,rb;private final Config config;private final double seconds;
    private final List<Row> rows=new ArrayList<>();private Vec3 linearImpulse=ZERO,angularImpulse=ZERO;private boolean broken;
    private final class Row {
        final Vec3 axis;final boolean angular;final double bias,target,lower,upper,denominator;double impulse;
        Row(Vec3 axis,boolean angular,double bias,double target,double lower,double upper) {
            this.axis=axis;this.angular=angular;this.bias=bias;this.target=target;this.lower=lower;this.upper=upper;
            denominator=angular?angularDenominator(a,axis)+angularDenominator(b,axis):a.pointImpulseDenominator(ra,axis)+b.pointImpulseDenominator(rb,axis);
        }
        void solve() {
            if(denominator<=1e-12||broken)return;
            Vec3 relative=angular?b.angularVelocity().minus(a.angularVelocity()):b.velocityAtPoint(rb).minus(a.velocityAtPoint(ra));
            double next=Math.max(lower,Math.min(upper,impulse-(relative.dot(axis)-target+bias)/denominator));
            Vec3 delta=axis.scaled(next-impulse),candidate=(angular?angularImpulse:linearImpulse).plus(delta);
            if(candidate.length()>(angular?config.breakTorque:config.breakForce)*seconds+1e-12){broken=true;return;}
            impulse=next;
            if(angular){angularImpulse=candidate;angular(a,delta.scaled(-1));angular(b,delta);}
            else {linearImpulse=candidate;if(a.mass()>1e-9)a.applyImpulse(delta.scaled(-1),ra);if(b.mass()>1e-9)b.applyImpulse(delta,rb);}
        }
    }
    public BodyJointConstraint(D6PhysicsBody a,D6PhysicsBody b,Config config,double seconds) {
        if(a==null||b==null||a==b||!Double.isFinite(seconds)||seconds<=0||seconds>.05)throw new IllegalArgumentException("distinct bodies and bounded step required");
        this.a=a;this.b=b;this.config=Objects.requireNonNull(config);this.seconds=seconds;
        Vec3 wa=a.position().plus(a.rotation().rotate(config.anchorA)),wb=b.position().plus(b.rotation().rotate(config.anchorB));
        BodyBounds.finite(wa);BodyBounds.finite(wb);
        // Equal/opposite loads meet at the joint, retaining total angular momentum even while
        // a bounded positional error is being repaired. Never pull entities to an anchor pose.
        Vec3 point=wa.plus(wb).scaled(.5);ra=point.minus(a.position());rb=point.minus(b.position());Vec3 error=wb.minus(wa);
        Quat qa=multiply(a.rotation(),config.frameA),qb=multiply(b.rotation(),config.frameB);
        Vec3 x=qa.rotate(X),y=qa.rotate(Y),z=qa.rotate(Z);
        if(config.kind==Kind.SLIDER) {
            linear(y,error.dot(y));linear(z,error.dot(z));double offset=error.dot(x);
            if(config.motorForce>0)row(x,false,0,config.speed,-config.motorForce*seconds,config.motorForce*seconds);
            // Predict approaching limits, and repair existing small violations with bounded bias.
            if(Double.isFinite(config.lower))row(x,false,limitBias(offset-config.lower,offset<config.lower),0,0,Double.POSITIVE_INFINITY);
            if(Double.isFinite(config.upper))row(x,false,limitBias(offset-config.upper,offset>config.upper),0,Double.NEGATIVE_INFINITY,0);
        }else {linear(x,error.dot(x));linear(y,error.dot(y));linear(z,error.dot(z));}
        if(config.kind==Kind.HINGE||config.kind==Kind.MOTOR) {
            Vec3 axisB=qb.rotate(X),swing=x.cross(axisB);double sine=swing.length(),cosine=Math.max(-1,Math.min(1,x.dot(axisB)));
            // Antiparallel axes need a deterministic correction rather than a zero cross product.
            Vec3 turn=sine>1e-10?swing.scaled(Math.atan2(sine,cosine)/sine):cosine<0?y.scaled(Math.PI):ZERO;
            angular(y,turn.dot(y));angular(z,turn.dot(z));
            if(config.kind==Kind.MOTOR&&config.motorForce>0)row(x,true,0,config.speed,-config.motorForce*seconds,config.motorForce*seconds);
        }else {
            Quat q=multiply(qb,qa.inverse());if(q.w()<0)q=new Quat(-q.x(),-q.y(),-q.z(),-q.w());
            Vec3 axis=new Vec3(q.x(),q.y(),q.z());double sine=axis.length();Vec3 turn=sine<1e-10?ZERO:axis.scaled(2*Math.atan2(sine,q.w())/sine);
            angular(x,turn.dot(x));angular(y,turn.dot(y));angular(z,turn.dot(z));
        }
    }
    private static Quat multiply(Quat a,Quat b){return ImmPtlQuaternions.hamiltonProduct(a,b).normalized();}
    private void linear(Vec3 axis,double error){row(axis,false,clamp(.2*error/seconds,20),0,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY);}
    private void angular(Vec3 axis,double error){row(axis,true,clamp(.2*error/seconds,4),0,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY);}
    private double limitBias(double error,boolean violated){return clamp((violated?.2:1)*error/seconds,20);}
    private static double clamp(double value,double limit){return Math.max(-limit,Math.min(limit,value));}
    private void row(Vec3 axis,boolean angular,double bias,double target,double lower,double upper){rows.add(new Row(axis,angular,bias,target,lower,upper));}
    private static double angularDenominator(D6PhysicsBody body,Vec3 axis){var inverse=body.worldInverseInertia();return body.mass()<=1e-9||inverse==null?0:axis.dot(inverse.transform(axis));}
    private static void angular(D6PhysicsBody body,Vec3 impulse){if(body.mass()>1e-9)body.withAngularMomentum(body.angularMomentum().plus(impulse));}
    public void solve(){if(!broken)for(var row:rows)row.solve();}
    public boolean broken(){return broken;}
    public Vec3 linearImpulse(){return linearImpulse;}
    public Vec3 angularImpulse(){return angularImpulse;}
}

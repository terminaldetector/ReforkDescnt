package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import java.util.Optional;

/** Fifteen-axis box SAT. Bounds are broadphase only; normals/depth come from the oriented shape. */
public final class OrientedBox {
    public record Contact(Vec3 normal,double penetration) {}
    public record Sweep(double fraction,Vec3 normal) {}
    private final Vec3 centre,half;
    private final Vec3[] axes;
    public OrientedBox(Vec3 centre,Vec3 half,Quat orientation) {
        BodyBounds.finite(centre);BodyBounds.finite(half);
        if(half.x()<=0 || half.y()<=0 || half.z()<=0 || orientation==null
            || !Double.isFinite(orientation.x()) || !Double.isFinite(orientation.y())
            || !Double.isFinite(orientation.z()) || !Double.isFinite(orientation.w())
            || !Double.isFinite(orientation.x()*orientation.x()+orientation.y()*orientation.y()+orientation.z()*orientation.z()+orientation.w()*orientation.w())
            || orientation.x()*orientation.x()+orientation.y()*orientation.y()+orientation.z()*orientation.z()+orientation.w()*orientation.w()<1e-20)
            throw new IllegalArgumentException("invalid oriented box");
        this.centre=centre;this.half=half;Quat q=orientation.normalized();
        axes=new Vec3[]{q.rotate(new Vec3(1,0,0)),q.rotate(new Vec3(0,1,0)),q.rotate(new Vec3(0,0,1))};
    }
    public static OrientedBox axisAligned(BodyBounds bounds){return new OrientedBox(bounds.centre(),bounds.half(),Quat.IDENTITY);}
    /** position is world COM, centreOfMass is the local-grid COM. */
    public static OrientedBox local(BodyBounds bounds,Vec3 position,Vec3 centreOfMass,Quat orientation) {
        return new OrientedBox(position.plus(orientation.normalized().rotate(bounds.centre().minus(centreOfMass))),bounds.half(),orientation);
    }
    public Vec3 centre(){return centre;}
    public Vec3 half(){return half;}
    /** Immutable world-space local basis, for clipped face and edge manifolds. */
    public Vec3 basis(int axis){return axes[axis];}
    /** Euclidean projection onto the occupied OBB, rather than onto its world broadphase. */
    public Vec3 closestPoint(Vec3 point) {
        BodyBounds.finite(point); Vec3 delta=point.minus(centre),result=centre;
        for(int i=0;i<3;i++) {
            double h=BodyBounds.component(half,i);
            result=result.plus(axes[i].scaled(Math.max(-h,Math.min(h,delta.dot(axes[i])))));
        }
        return result;
    }
    /** Centre of the supporting face/edge/vertex; zero projection keeps that axis centred. */
    public Vec3 supportCentre(Vec3 direction) {
        BodyBounds.finite(direction); Vec3 result=centre;
        for(int i=0;i<3;i++) {
            double dot=direction.dot(axes[i]);
            if(Math.abs(dot)>1e-10)result=result.plus(axes[i].scaled(Math.copySign(BodyBounds.component(half,i),dot)));
        }
        return result;
    }
    public BodyBounds bounds() {
        Vec3 reach=new Vec3(radius(new Vec3(1,0,0)),radius(new Vec3(0,1,0)),radius(new Vec3(0,0,1)));
        return new BodyBounds(centre.minus(reach),centre.plus(reach));
    }
    private Vec3 axis(OrientedBox other,int i) {
        return i<3?axes[i]:i<6?other.axes[i-3]:axes[(i-6)/3].cross(other.axes[(i-6)%3]);
    }
    private double radius(Vec3 axis) {
        return Math.abs(axis.dot(axes[0]))*half.x()+Math.abs(axis.dot(axes[1]))*half.y()+Math.abs(axis.dot(axes[2]))*half.z();
    }
    /** Unit normal points from other into this box. Positive skin admits a small separated contact. */
    public Optional<Contact> contact(OrientedBox other,double skin) {
        if(!Double.isFinite(skin) || skin<0)throw new IllegalArgumentException("invalid contact skin");
        Vec3 delta=centre.minus(other.centre),normal=null;double depth=Double.POSITIVE_INFINITY;
        for(int i=0;i<15;i++) {
            Vec3 axis=axis(other,i);double length=axis.length();if(length<1e-10)continue;
            axis=axis.scaled(1/length);
            double separation=delta.dot(axis),overlap=radius(axis)+other.radius(axis)-Math.abs(separation);
            if(overlap < -skin)return Optional.empty();
            if(overlap<depth){depth=overlap;normal=axis.scaled(separation>=0?1:-1);}
        }
        return Optional.of(new Contact(normal,Math.max(0,depth)));
    }
    /** Exact linear CCD for two fixed orientations. Angular motion needs bounded rotational substeps. */
    public Optional<Sweep> sweep(OrientedBox other,Vec3 movement,Vec3 otherMovement) {
        BodyBounds.finite(movement);BodyBounds.finite(otherMovement);
        Vec3 relative=movement.minus(otherMovement),delta=centre.minus(other.centre);
        var initial=contact(other,0);
        if(initial.isPresent()) {
            Contact c=initial.get();
            if(c.penetration()>1e-9)return Optional.of(new Sweep(0,c.normal()));
            // For touching boxes the interval test below distinguishes closing, tangent and
            // opening motion, including a corner with more than one zero-depth axis.
        }
        double enter=Double.NEGATIVE_INFINITY,exit=Double.POSITIVE_INFINITY;Vec3 normal=null;
        for(int i=0;i<15;i++) {
            Vec3 axis=axis(other,i);double length=axis.length();if(length<1e-10)continue;
            axis=axis.scaled(1/length);double distance=delta.dot(axis),speed=relative.dot(axis);
            double r=radius(axis)+other.radius(axis);
            if(Math.abs(speed)<1e-12){if(Math.abs(distance)>r)return Optional.empty();continue;}
            double a=(-r-distance)/speed,b=(r-distance)/speed;
            if(a>b){double swap=a;a=b;b=swap;}
            if(a>enter){enter=a;normal=axis.scaled(speed>0?-1:1);}
            exit=Math.min(exit,b);if(enter>exit+1e-12)return Optional.empty();
        }
        if(enter<0 || enter>1 || exit<0 || normal==null)return Optional.empty();
        return Optional.of(new Sweep(enter,normal));
    }
}

package com.terminaldetector.drmd.physics.collision;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/** Immutable numeric bounds shared by the existing D6 body, collision and local-grid queries. */
public record BodyBounds(Vec3 min,Vec3 max) {
    public BodyBounds {
        finite(min);finite(max);
        if(min.x()>=max.x() || min.y()>=max.y() || min.z()>=max.z()
            || !Double.isFinite(max.x()-min.x()) || !Double.isFinite(max.y()-min.y()) || !Double.isFinite(max.z()-min.z()))throw new IllegalArgumentException("empty body bounds");
    }
    public Vec3 centre(){return min.plus(max.minus(min).scaled(.5));}
    public Vec3 half(){return max.minus(min).scaled(.5);}
    public boolean intersects(BodyBounds b) {
        return max.x()>=b.min.x() && min.x()<=b.max.x() && max.y()>=b.min.y() && min.y()<=b.max.y()
            && max.z()>=b.min.z() && min.z()<=b.max.z();
    }
    public BodyBounds union(BodyBounds b) {
        return new BodyBounds(new Vec3(Math.min(min.x(),b.min.x()),Math.min(min.y(),b.min.y()),Math.min(min.z(),b.min.z())),
            new Vec3(Math.max(max.x(),b.max.x()),Math.max(max.y(),b.max.y()),Math.max(max.z(),b.max.z())));
    }
    public static void finite(Vec3 p){if(p==null || !Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z()))throw new IllegalArgumentException("non-finite body coordinate");}
    public static double component(Vec3 p,int axis){return axis==0?p.x():axis==1?p.y():p.z();}
}

package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/** Signed shortest turn about the actual world hinge axis, never a pose assignment. */
public final class HingeAim {
    private HingeAim(){}
    public static double velocity(Vec3 forward,Vec3 desired,Vec3 hinge,double limit){
        if(!Double.isFinite(forward.lengthSquared())||!Double.isFinite(desired.lengthSquared())||!Double.isFinite(hinge.lengthSquared())||hinge.lengthSquared()<1e-12||!Double.isFinite(limit)||limit<0)throw new IllegalArgumentException("finite directions, hinge and motor limit required");
        var axis=hinge.normalized();var a=forward.minus(axis.scaled(forward.dot(axis)));var b=desired.minus(axis.scaled(desired.dot(axis)));
        if(a.lengthSquared()<1e-12||b.lengthSquared()<1e-12)return 0;
        double angle=Math.atan2(axis.dot(a.cross(b)),a.dot(b));return Math.max(-limit,Math.min(limit,angle*2));
    }
}

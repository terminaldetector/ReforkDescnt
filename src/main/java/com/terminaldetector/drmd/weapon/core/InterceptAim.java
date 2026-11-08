package com.terminaldetector.drmd.weapon.core;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.Optional;

/** Constant-velocity interception in tick units, relative to the launcher's material velocity. */
public final class InterceptAim {
    public record Solution(Vec3 direction,double ticks){}
    private InterceptAim(){}
    public static Optional<Solution> solve(Vec3 offset,Vec3 relativeVelocity,double speed,double lifetime){
        if(!Double.isFinite(offset.lengthSquared())||!Double.isFinite(relativeVelocity.lengthSquared())||!Double.isFinite(speed)||!Double.isFinite(lifetime)||speed<=0||lifetime<=0)return Optional.empty();
        double c=offset.lengthSquared();if(c<1e-12)return Optional.empty();
        double a=relativeVelocity.lengthSquared()-speed*speed,b=2*offset.dot(relativeVelocity),t=Double.POSITIVE_INFINITY;
        if(Math.abs(a)<1e-10){if(Math.abs(b)>1e-12)t=-c/b;}
        else{double d=b*b-4*a*c;if(d<0)return Optional.empty();double q=-.5*(b+Math.copySign(Math.sqrt(d),b));double first=q/a,second=q==0?Double.POSITIVE_INFINITY:c/q;if(first>0)t=first;if(second>0)t=Math.min(t,second);}
        if(!Double.isFinite(t)||t<=0||t>lifetime)return Optional.empty();
        return Optional.of(new Solution(offset.plus(relativeVelocity.scaled(t)).normalized(),t));
    }
}

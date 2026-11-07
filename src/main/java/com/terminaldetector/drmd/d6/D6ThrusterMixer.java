package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import java.util.*;
import java.util.function.IntPredicate;

/** Fixed vector-thruster mixer on an authored layout. Forces act at real local mounts.
 * Missing motors lose their nominal contribution; surviving motors gain no free capacity. */
public final class D6ThrusterMixer {
    public record Output(int mount,Vec3 localPoint,Vec3 force) {}
    private final List<Vec3> mounts;
    private final Vec3 centroid;
    private final D6Mat3 inverseMoment;
    private final double capacity;

    public D6ThrusterMixer(List<Vec3> points,double forcePerMount) {
        if(points.size()<3||points.size()>16||!Double.isFinite(forcePerMount)||forcePerMount<=0)
            throw new IllegalArgumentException("bounded non-collinear layout and positive motor capacity required");
        mounts=List.copyOf(points);capacity=forcePerMount;Vec3 sum=new Vec3(0,0,0);
        for(var point:mounts){finite(point);sum=sum.plus(point);}
        centroid=sum.scaled(1.0/mounts.size());var moment=D6Mat3.ZERO;
        for(var point:mounts) {
            var r=point.minus(centroid);double x=r.x(),y=r.y(),z=r.z();
            moment=moment.plus(new D6Mat3(new Vec3(y*y+z*z,-x*y,-x*z),
                new Vec3(-x*y,x*x+z*z,-y*z),new Vec3(-x*z,-y*z,x*x+y*y)));
        }
        inverseMoment=moment.inverse();
        if(inverseMoment==null)throw new IllegalArgumentException("authored thruster layout is collinear");
    }

    /** Desired local force/torque about the current material COM. One shared saturation
     * factor preserves the commanded wrench when all motors are present. */
    public List<Output> mix(Vec3 centreOfMass,Vec3 force,Vec3 torque,IntPredicate online) {
        finite(centreOfMass);finite(force);finite(torque);Objects.requireNonNull(online);
        var residual=torque.minus(centroid.minus(centreOfMass).cross(force));
        var spin=inverseMoment.transform(residual);var translation=force.scaled(1.0/mounts.size());
        var nominal=new ArrayList<Vec3>(mounts.size());double peak=0;
        for(var point:mounts) {
            var f=translation.plus(spin.cross(point.minus(centroid)));finite(f);
            nominal.add(f);peak=Math.max(peak,f.length());
        }
        double scale=peak>capacity?capacity/peak:1;var result=new ArrayList<Output>();
        for(int i=0;i<mounts.size();i++)if(online.test(i)&&nominal.get(i).lengthSquared()>1e-24)
            result.add(new Output(i,mounts.get(i),nominal.get(i).scaled(scale)));
        return List.copyOf(result);
    }
    private static void finite(Vec3 v){if(v==null||!Double.isFinite(v.lengthSquared()))throw new IllegalArgumentException("finite thruster vector required");}
}

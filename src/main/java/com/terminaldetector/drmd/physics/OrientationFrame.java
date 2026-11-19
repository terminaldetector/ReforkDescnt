package com.terminaldetector.drmd.physics;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import net.minecraft.util.math.Vec3d;
import java.util.Optional;

/** One finite proper quaternion basis for flight and local-gravity coordinate boundaries. */
public final class OrientationFrame {
    private static final Vec3d WORLD_UP=new Vec3d(0,1,0),WORLD_FORWARD=new Vec3d(0,0,1);
    private OrientationFrame() {}
    public static Optional<Quat> decode(Vec3d forward,Vec3d up) {
        if(!finite(forward)||!finite(up)||forward.lengthSquared()<1e-20||up.lengthSquared()<1e-20
            ||!Double.isFinite(forward.lengthSquared())||!Double.isFinite(up.lengthSquared()))return Optional.empty();
        var f=forward.normalize();var u=up.normalize();u=u.subtract(f.multiply(u.dotProduct(f)));
        if(u.lengthSquared()<1e-12) {
            var fallback=Math.abs(f.y)<.9?WORLD_UP:WORLD_FORWARD;
            u=fallback.subtract(f.multiply(fallback.dotProduct(f)));
        }
        u=u.normalize();
        var turn=PortalTransform.rotationBetween(new Vec3(0,0,1),pure(f));
        var first=mc(turn.rotate(new Vec3(0,1,0)));
        double roll=Math.atan2(f.dotProduct(first.crossProduct(u)),first.dotProduct(u));
        return Optional.of(ImmPtlQuaternions.hamiltonProduct(ImmPtlQuaternions.rotationByRadians(pure(f),roll),turn).normalized());
    }
    public static Quat fromUp(Vec3d up) {
        if(!finite(up)||up.lengthSquared()<1e-20||!Double.isFinite(up.lengthSquared()))return Quat.IDENTITY;
        var normal=up.normalize();var forward=WORLD_FORWARD.subtract(normal.multiply(normal.z));
        if(forward.lengthSquared()<1e-8)forward=WORLD_UP.subtract(normal.multiply(normal.y));
        return decode(forward,normal).orElse(Quat.IDENTITY);
    }
    public static Vec3d toWorld(Quat frame,Vec3d local){return mc(frame.rotate(pure(local)));}
    public static Vec3d toLocal(Quat frame,Vec3d world){return mc(frame.inverse().rotate(pure(world)));}
    private static boolean finite(Vec3d v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static Vec3 pure(Vec3d v){return new Vec3(v.x,v.y,v.z);}
    private static Vec3d mc(Vec3 v){return new Vec3d(v.x(),v.y(),v.z());}
}

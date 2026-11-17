package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.Vec3d;

/** First cardinal-frame force adapter. Native collision/effect results remain inputs, not a target velocity. */
public final class LocalSurfaceMotion {
    private static final double NORMAL_DRAG=0.9800000190734863;
    private static final Vec3d[] CARDINALS={new Vec3d(1,0,0),new Vec3d(-1,0,0),new Vec3d(0,1,0),
        new Vec3d(0,-1,0),new Vec3d(0,0,1),new Vec3d(0,0,-1)};
    private LocalSurfaceMotion() {}

    /** Signed permutations preserve the components already stopped by native axis collisions. */
    public static Vec3d cardinalUp(Vec3d up) {
        if(up==null||!finite(up)||Math.abs(up.lengthSquared()-1)>1e-6)return null;
        for(var axis:CARDINALS)
            if(up.squaredDistanceTo(axis)<=1e-12)return axis;
        return null;
    }

    public static Vec3d dryResult(Vec3d moved,Vec3d nativeResult,Vec3d up,float tangentDrag,boolean noDrag) {
        var cardinal=cardinalUp(up);
        if(noDrag||cardinal==null||!finite(moved)||!finite(nativeResult)||!Float.isFinite(tangentDrag))return nativeResult;
        var nativeDamping=moved.multiply(tangentDrag,NORMAL_DRAG,tangentDrag);
        var residual=nativeResult.subtract(nativeDamping);
        var local=ActorFrameMath.toLocal(moved,cardinal).multiply(tangentDrag,NORMAL_DRAG,tangentDrag);
        return ActorFrameMath.toWorld(local,cardinal).add(residual);
    }

    private static boolean finite(Vec3d v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}

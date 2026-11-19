package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.Vec3d;

/** Native eye-relative sample offsets in the already admitted physical eye frame. */
public final class LocalEyeSamples {
    private LocalEyeSamples() {}
    public static Vec3d transform(Vec3d nativePoint,Vec3d nativeEye,Vec3d physicalEye,Vec3d up){
        if(!finite(nativePoint)||!finite(nativeEye)||!finite(physicalEye)||!finite(up)
            ||Math.abs(up.lengthSquared()-1)>1e-6)return nativePoint;
        if(up.x==0&&up.y==1&&up.z==0&&physicalEye.x==nativeEye.x&&physicalEye.y==nativeEye.y&&physicalEye.z==nativeEye.z)return nativePoint;
        return physicalEye.add(ActorFrameMath.toWorld(nativePoint.subtract(nativeEye),up));
    }
    private static boolean finite(Vec3d v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}

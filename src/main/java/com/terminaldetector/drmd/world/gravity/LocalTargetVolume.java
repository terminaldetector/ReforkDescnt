package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Conservative world AABB of vanilla's unchanged local (range,4,range) search band. */
public final class LocalTargetVolume {
    private LocalTargetVolume() {}
    public static Box expand(Box body,Vec3d up,double range){
        if(up==null||!Double.isFinite(up.lengthSquared())||Math.abs(up.lengthSquared()-1)>1e-6
            ||!Double.isFinite(range)||range<0||up.x==0&&up.y==1&&up.z==0)return body.expand(range,4,range);
        var x=ActorFrameMath.toWorld(new Vec3d(range,0,0),up);
        var y=up.multiply(4);var z=ActorFrameMath.toWorld(new Vec3d(0,0,range),up);
        return body.expand(Math.abs(x.x)+Math.abs(y.x)+Math.abs(z.x),Math.abs(x.y)+Math.abs(y.y)+Math.abs(z.y),Math.abs(x.z)+Math.abs(y.z)+Math.abs(z.z));
    }
}

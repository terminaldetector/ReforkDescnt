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

    /** Entity.move applies its actual material multiplier before travel's separate dry damping. */
    public static Vec3d materialMultiplier(Vec3d resolved,Vec3d nativeResult,Vec3d up,double factor){
        var cardinal=cardinalUp(up);
        if(cardinal==null||Math.abs(cardinal.y)>.5||factor==1||!finite(resolved)||!finite(nativeResult)
            ||!Double.isFinite(factor)||factor<0)return nativeResult;
        var normal=cardinal.multiply(resolved.dotProduct(cardinal));
        return resolved.subtract(normal).multiply(factor).add(normal);
    }

    /** Native .4 aim/.2 inherited tangent algebra; the goal still owns selection, timing and leap height. */
    public static Vec3d pounce(Vec3d targetDelta,Vec3d previous,Vec3d up,float leap,Vec3d nativeResult) {
        var cardinal=cardinalUp(up);
        if(cardinal==null||!finite(targetDelta)||!finite(previous)||!Float.isFinite(leap))return nativeResult;
        var tangent=targetDelta.subtract(cardinal.multiply(targetDelta.dotProduct(cardinal)));
        if(tangent.lengthSquared()>1e-7) {
            var inherited=previous.subtract(cardinal.multiply(previous.dotProduct(cardinal)));
            tangent=tangent.normalize().multiply(.4).add(inherited.multiply(.2));
        }
        return tangent.add(cardinal.multiply((double)leap));
    }

    /** Native jump replaces its normal component; keep existing world tangent and the computed strength. */
    public static Vec3d ordinaryJump(Vec3d previous,Vec3d nativeResult,Vec3d up) {
        var cardinal=cardinalUp(up);
        if(cardinal==null||cardinal.equals(ActorFrameMath.WORLD_UP)||!finite(previous)||!finite(nativeResult)
            ||nativeResult.x!=previous.x||nativeResult.z!=previous.z)return nativeResult;
        return previous.subtract(cardinal.multiply(previous.dotProduct(cardinal)))
            .add(cardinal.multiply(nativeResult.y));
    }

    /** Map only the new native yaw-derived sprint addition, never the actor's accumulated momentum. */
    public static Vec3d jumpSprint(Vec3d nativeDelta,Vec3d up) {
        var cardinal=cardinalUp(up);
        if(cardinal==null||cardinal.equals(ActorFrameMath.WORLD_UP)||!finite(nativeDelta))return nativeDelta;
        return ActorFrameMath.toWorld(nativeDelta,cardinal);
    }

    private static boolean finite(Vec3d v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}

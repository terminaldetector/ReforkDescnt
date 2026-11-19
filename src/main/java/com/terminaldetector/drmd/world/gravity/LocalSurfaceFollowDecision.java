package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.Vec3d;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/** Pure bounded selection: a nearby corner is not consumed until the real next edge is clear. */
final class LocalSurfaceFollowDecision {
    record Decision(int index,boolean clear){}
    private LocalSurfaceFollowDecision(){}

    /** The caller supplies the existing loaded-only edge query; at most two such queries execute. */
    static Decision select(Vec3d feet,int current,int length,IntFunction<Vec3d> point,IntPredicate clear){
        return select(current,length,index->feet.distanceTo(point.apply(index))<.18,clear);
    }

    /** Native EntityNavigation's width-dependent X/Z reach, expressed in the local body frame.
     * C1 feet need not occupy another actor's centre before progressing to a clear next edge. */
    static boolean nativeWaypointReached(Vec3d localDelta,float width){
        if(!Float.isFinite(width)||width<=0)return false;
        float proximity=width>.75f?width*.5f:.75f-width*.5f;
        return Math.abs(localDelta.x)<proximity&&Math.abs(localDelta.z)<proximity&&Math.abs(localDelta.y)<1;
    }

    static Decision select(int current,int length,IntPredicate reached,IntPredicate clear){
        if(current>=length)return new Decision(current,true);
        int candidate=current;
        for(int i=0;i<4&&candidate<length&&reached.test(candidate);i++)candidate++;
        if(candidate!=current&&clear.test(candidate))return new Decision(candidate,true);
        return new Decision(current,clear.test(current));
    }
}

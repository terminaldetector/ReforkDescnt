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
        if(current>=length)return new Decision(current,true);
        int candidate=current;
        for(int i=0;i<4&&candidate<length&&feet.distanceTo(point.apply(candidate))<.18;i++)candidate++;
        if(candidate!=current&&clear.test(candidate))return new Decision(candidate,true);
        return new Decision(current,clear.test(current));
    }
}

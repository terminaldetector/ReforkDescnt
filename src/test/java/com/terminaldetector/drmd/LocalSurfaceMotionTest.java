package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.ActorFrameMath;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceMotion;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalSurfaceMotionTest {
    private static final Vec3d[] UPS={new Vec3d(1,0,0),new Vec3d(-1,0,0),new Vec3d(0,1,0),
        new Vec3d(0,-1,0),new Vec3d(0,0,1),new Vec3d(0,0,-1)};
    private static void same(Vec3d a,Vec3d b){assertEquals(0,a.distanceTo(b),1e-12);}
    @Test void worldUpRetainsNativeDryResultIncludingEffectResidual() {
        var moved=new Vec3d(.13,-.07,.22);var nativeResult=new Vec3d(.071,-.04,.12);
        same(nativeResult,LocalSurfaceMotion.dryResult(moved,nativeResult,UPS[2],.54600006f,false));
    }
    @Test void allSixCardinalFramesHaveTheSameMaterialDampingInLocalAxes() {
        var local=new Vec3d(.13,-.07,.22);
        for(var up:UPS)for(float t:new float[]{.54600006f,.89180005f,.91f}) {
            var world=ActorFrameMath.toWorld(local,up);var nativeResult=world.multiply(t,.9800000190734863,t);
            same(local.multiply(t,.9800000190734863,t),ActorFrameMath.toLocal(
                LocalSurfaceMotion.dryResult(world,nativeResult,up,t,false),up));
        }
    }
    @Test void effectResidualIsRetainedInItsExistingWorldAxes() {
        var moved=new Vec3d(.12,.31,-.17);var residual=new Vec3d(.003,.025,-.004);float t=.54600006f;
        for(var up:UPS) {
            var nativeResult=moved.multiply(t,.9800000190734863,t).add(residual);
            var expected=ActorFrameMath.toWorld(ActorFrameMath.toLocal(moved,up).multiply(t,.9800000190734863,t),up).add(residual);
            same(expected,LocalSurfaceMotion.dryResult(moved,nativeResult,up,t,false));
        }
    }
    @Test void nativeNoDragResultIsReturnedWithoutAnAddedDampingPass() {
        var nativeResult=new Vec3d(.12,.31,-.17);
        assertSame(nativeResult,LocalSurfaceMotion.dryResult(new Vec3d(.2,.4,.6),nativeResult,UPS[0],.546f,true));
    }
    @Test void nativeCollisionStoppedCardinalComponentCannotBeResurrectedByTheOperator() {
        for(var up:UPS)for(int axis=0;axis<3;axis++) {
            double[] v={.2,-.3,.4};v[axis]=0;var moved=new Vec3d(v[0],v[1],v[2]);float t=.54600006f;
            var result=LocalSurfaceMotion.dryResult(moved,moved.multiply(t,.9800000190734863,t),up,t,false);
            assertEquals(0,new double[]{result.x,result.y,result.z}[axis],0);
        }
    }
    @Test void unprovedObliqueOrInvalidFramesDelegateToNativeResult() {
        var result=new Vec3d(.2,.4,.6);
        for(var up:new Vec3d[]{new Vec3d(1,1,0).normalize(),Vec3d.ZERO,new Vec3d(Double.NaN,0,0)})
            assertSame(result,LocalSurfaceMotion.dryResult(result,result,up,.546f,false));
        assertSame(result,LocalSurfaceMotion.dryResult(result,result,UPS[0],Float.NaN,false));
    }
}

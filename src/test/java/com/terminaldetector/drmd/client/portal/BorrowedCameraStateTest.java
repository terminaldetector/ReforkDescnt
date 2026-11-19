package com.terminaldetector.drmd.client.portal;

import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BorrowedCameraStateTest {
    /** Real Minecraft Camera, exposing only the two already-existing production invokers. */
    private static final class Lens extends Camera implements CameraAccessor {
        @Override public void drmd$invokeSetPos(Vec3d p){super.setPos(p);}
        @Override public void drmd$invokeSetRotation(float y,float p){super.setRotation(y,p);}
    }
    private static Lens banked(){
        var c=new Lens();c.drmd$invokeSetPos(new Vec3d(4.25,700.5,-2.75));c.drmd$invokeSetRotation(37,-29);
        var f=c.getHorizontalPlane();var bank=new Quaternionf().rotationAxis(.83f,f.x,f.y,f.z);
        c.getRotation().premul(bank).normalize();
        c.getHorizontalPlane().rotate(bank);c.getVerticalPlane().rotate(bank);c.getDiagonalPlane().rotate(bank);
        return c;
    }
    private static float[] orientation(Camera c){
        var q=c.getRotation();var f=c.getHorizontalPlane();var u=c.getVerticalPlane();var l=c.getDiagonalPlane();
        return new float[]{c.getYaw(),c.getPitch(),q.x,q.y,q.z,q.w,f.x,f.y,f.z,u.x,u.y,u.z,l.x,l.y,l.z};
    }
    @Test void realYawPitchRestoreLosesBankButBorrowedPoseRestoresEveryComponent(){
        var c=banked();var before=orientation(c);var position=c.getPos();
        try(var saved=new BorrowedCameraState(c,c)){
            c.drmd$invokeSetPos(new Vec3d(20,80,90));c.drmd$invokeSetRotation(-76,66);
            c.drmd$invokeSetRotation(before[0],before[1]);
            assertFalse(java.util.Arrays.equals(before,orientation(c)),"negative: the old Euler-only restoration loses bank");
        }
        assertArrayEquals(before,orientation(c));assertEquals(position,c.getPos());
    }
    @Test void nestedFailureStillRestoresTheRealCameraPose(){
        var c=banked();var before=orientation(c);var position=c.getPos();
        assertThrows(IllegalStateException.class,()->{
            try(var saved=new BorrowedCameraState(c,c)){
                c.drmd$invokeSetPos(new Vec3d(-1,12,4));c.drmd$invokeSetRotation(150,-80);
                throw new IllegalStateException("nested draw rejected");
            }
        });
        assertArrayEquals(before,orientation(c));assertEquals(position,c.getPos());
    }
    @Test void repeatedVisibleSurfacesDoNotAccumulateAnEulerReset(){
        var c=banked();var before=orientation(c);
        for(int i=0;i<1000;i++){
            try(var saved=new BorrowedCameraState(c,c)){c.drmd$invokeSetRotation(i*13.25f,i%178-89);}
            assertArrayEquals(before,orientation(c));
        }
    }
}

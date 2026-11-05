package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.render.FlightEnemyPose;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlightEnemyPoseTest {
    @Test void noseAndHandednessRemainCorrectThroughVerticalFlightAndFullBank() {
        for(float yaw=-180;yaw<=180;yaw+=45)for(float pitch:new float[]{-90,-60,0,60,90})for(float bank:new float[]{-180,-90,0,90,180}) {
            var q=FlightEnemyPose.rotation(yaw,pitch,bank);var forward=Vec3d.fromPolar(pitch,yaw).normalize();var nose=new Vector3f(0,0,1).rotate(q);
            assertEquals(forward.x,nose.x,2e-5);assertEquals(forward.y,nose.y,2e-5);assertEquals(forward.z,nose.z,2e-5);
            var right=new Vector3f(1,0,0).rotate(q);var up=new Vector3f(0,1,0).rotate(q);
            assertEquals(1,new Vector3f(right).cross(up).dot(nose),2e-5);assertEquals(1,q.lengthSquared(),2e-5);
        }
    }
    @Test void corruptPoseFallsBackToAFiniteHullInsteadOfPoisoningTheRenderStack() {
        var q=FlightEnemyPose.rotation(Float.NaN,0,0);assertEquals(0,q.x);assertEquals(0,q.y);assertEquals(0,q.z);assertEquals(1,q.w);
    }
}

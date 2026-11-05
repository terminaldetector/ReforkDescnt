package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.ai.FlightAttitude;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Direct +Z-nose hull basis, independent of vanilla standing-model transforms. */
public final class FlightEnemyPose {
    private FlightEnemyPose(){}
    public static Quaternionf rotation(float yaw,float pitch,float bank) {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(bank))return new Quaternionf();
        var forward=Vec3d.fromPolar(pitch,yaw).normalize();var up=FlightAttitude.upOf(forward,bank);up=up.subtract(forward.multiply(up.dotProduct(forward))).normalize();var right=up.crossProduct(forward).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f((float)right.x,(float)right.y,(float)right.z,(float)up.x,(float)up.y,(float)up.z,(float)forward.x,(float)forward.y,(float)forward.z));
    }
}

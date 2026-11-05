package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Shared physical/render basis: local+Z nose and+Y top, including pitch and bank. */
public final class FlightHullPose {
    private FlightHullPose(){}
    public static Quaternionf rotation(float yaw,float pitch,float bank){
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(bank))return new Quaternionf();
        var forward=Vec3d.fromPolar(pitch,yaw).normalize();var up=FlightAttitude.upOf(forward,bank);up=up.subtract(forward.multiply(up.dotProduct(forward))).normalize();var right=up.crossProduct(forward).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f((float)right.x,(float)right.y,(float)right.z,(float)up.x,(float)up.y,(float)up.z,(float)forward.x,(float)forward.y,(float)forward.z));
    }
    public static Quat physicalRotation(float yaw,float pitch,float bank){
        var q=rotation(yaw,pitch,bank);return new Quat(q.x,q.y,q.z,q.w).normalized();
    }
}

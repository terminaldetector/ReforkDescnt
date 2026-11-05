package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.model.FlightHullPose;
import org.joml.Quaternionf;

/** Client adapter for the same+Z-nose physical hull basis used by death replacements. */
public final class FlightEnemyPose {
    private FlightEnemyPose(){}
    public static Quaternionf rotation(float yaw,float pitch,float bank){return FlightHullPose.rotation(yaw,pitch,bank);}
}

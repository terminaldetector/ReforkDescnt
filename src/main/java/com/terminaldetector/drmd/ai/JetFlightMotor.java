package com.terminaldetector.drmd.ai;

import net.minecraft.util.math.Vec3d;

/** Thrust changes velocity, preserving existing momentum; velocities are blocks/tick. */
public final class JetFlightMotor {
    /** 24 blocks/s² at20Hz, applied by the real AI once per server tick. */
    public static final double MAX_VELOCITY_STEP=24.0/(20.0*20.0);
    private JetFlightMotor(){}
    public static Vec3d steer(Vec3d current,Vec3d desired) {
        if(!Double.isFinite(current.lengthSquared())||!Double.isFinite(desired.lengthSquared()))throw new IllegalArgumentException("non-finite flight velocity");
        var change=desired.subtract(current);double length=change.length();
        return length<=MAX_VELOCITY_STEP?desired:current.add(change.multiply(MAX_VELOCITY_STEP/length));
    }
}

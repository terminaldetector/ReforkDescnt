package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.Vec3d;

/** Stateless basis shared by collision, interaction rays and the living model. */
public final class ActorFrameMath {
    public static final Vec3d WORLD_UP = new Vec3d(0, 1, 0);
    private ActorFrameMath() {}

    /** Zero is the wire/save sentinel for an inactive frame. Reject corrupt metadata. */
    public static Vec3d decode(double x, double y, double z) {
        double length = x*x+y*y+z*z;
        if (!Double.isFinite(length) || length < .25 || length > 4) return Vec3d.ZERO;
        return new Vec3d(x,y,z).multiply(1/Math.sqrt(length));
    }

    public static Vec3d toWorld(Vec3d local, Vec3d up) {
        return com.terminaldetector.drmd.physics.OrientationFrame.toWorld(
            com.terminaldetector.drmd.physics.OrientationFrame.fromUp(up),local);
    }

    public static Vec3d toLocal(Vec3d world,Vec3d up) {
        return com.terminaldetector.drmd.physics.OrientationFrame.toLocal(
            com.terminaldetector.drmd.physics.OrientationFrame.fromUp(up),world);
    }
}

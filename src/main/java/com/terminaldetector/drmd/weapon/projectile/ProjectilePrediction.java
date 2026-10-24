package com.terminaldetector.drmd.weapon.projectile;

import net.minecraft.util.math.Vec3d;

/** Short visual extrapolation between authoritative positions, never unlimited client travel. */
public final class ProjectilePrediction {
    public static final int MAX_LEAD_TICKS = 4;
    private Vec3d anchor;
    private int leadTicks;

    public void accept(Vec3d position) {
        anchor = position;
        leadTicks = 0;
    }

    public Vec3d advance(Vec3d current, Vec3d velocity) {
        if (anchor == null) accept(current);
        leadTicks = Math.min(MAX_LEAD_TICKS, leadTicks + 1);
        return anchor.add(velocity.multiply(leadTicks));
    }
}

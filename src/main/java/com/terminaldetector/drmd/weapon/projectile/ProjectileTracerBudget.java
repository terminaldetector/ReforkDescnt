package com.terminaldetector.drmd.weapon.projectile;

import net.minecraft.util.math.Vec3d;

/** Client tick allowance for cosmetic projectile dust. Flight and projectile models have their own owners. */
public final class ProjectileTracerBudget {
    public static final int MAX_PARTICLES_PER_TICK = 768;
    private static final int NEAR_RESERVE = 256;
    private static final int NEAR_DISTANCE = 16;
    private static final int FAR_DISTANCE = 128;
    public static final ProjectileTracerBudget CLIENT = new ProjectileTracerBudget();

    private Vec3d camera;
    private int nearRemaining;
    private int sharedRemaining;
    private int requested;
    private int admitted;
    private long tick;

    public void begin(Vec3d camera) {
        this.camera = camera;
        nearRemaining = NEAR_RESERVE;
        sharedRemaining = MAX_PARTICLES_PER_TICK - NEAR_RESERVE;
        requested = admitted = 0;
        tick++;
    }

    public int take(Vec3d head, Vec3d travel, int preferred) {
        if (camera == null || preferred <= 0) return 0;
        requested += preferred;
        if (nearRemaining == 0 && sharedRemaining == 0) return 0;

        // A fast bolt can pass the camera while its head is already far away.
        var offset = head.subtract(camera);
        double travelSquared = travel.lengthSquared();
        double closest = travelSquared < 1e-6 ? 0 : Math.clamp(offset.dotProduct(travel) / travelSquared, 0, 1);
        double distanceSquared = offset.subtract(travel.multiply(closest)).lengthSquared();
        if (!Double.isFinite(distanceSquared) || distanceSquared > FAR_DISTANCE * FAR_DISTANCE) return 0;

        boolean near = distanceSquared <= NEAR_DISTANCE * NEAR_DISTANCE;
        if (!near) preferred = Math.min(preferred, distanceSquared > 64 * 64 ? 3 : 8);
        int reserved = near ? Math.min(preferred, nearRemaining) : 0;
        int shared = Math.min(preferred - reserved, sharedRemaining);
        nearRemaining -= reserved;
        sharedRemaining -= shared;
        admitted += reserved + shared;
        return reserved + shared;
    }

    public record Sample(long tick, int requested, int admitted) {}

    public Sample sample() {
        return new Sample(tick, requested, admitted);
    }
}

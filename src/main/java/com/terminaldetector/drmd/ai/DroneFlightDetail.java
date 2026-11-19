package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Distant steering may coast between expensive decisions. Entity ticks, collision and damage remain live. */
public final class DroneFlightDetail {
    private static final int COAST_TICKS = 4;
    private static final double INTEREST_DISTANCE = 80;
    private static final double RENDER_DETAILS_DISTANCE = 56;

    private DroneFlightDetail() {}

    static boolean mayCoast(DroneEntity drone, LivingEntity target, double weaponRange, long ticksSinceSteering) {
        if (ticksSinceSteering <= 0 || ticksSinceSteering >= COAST_TICKS
                || drone.hurtTime > 0 || drone.horizontalCollision || drone.verticalCollision
                || !(drone.getWorld() instanceof ServerWorld world) || world.getPlayers().isEmpty()) return false;
        double targetDistance = drone.squaredDistanceTo(target);
        if (targetDistance <= (weaponRange + 16) * (weaponRange + 16)) return false;
        for (var player : world.getPlayers()) {
            if (player.isAlive() && !player.isSpectator()
                    && player.squaredDistanceTo(drone) <= INTEREST_DISTANCE * INTEREST_DISTANCE) return false;
        }
        Vec3d velocity = drone.getVelocity();
        return velocity.lengthSquared() > 1e-6 && FlightRouteSpace.clearMovement(drone, velocity);
    }

    public static boolean showSmallHullDetails(double squaredCameraDistance) {
        return !Double.isFinite(squaredCameraDistance)
            || squaredCameraDistance < RENDER_DETAILS_DISTANCE * RENDER_DETAILS_DISTANCE;
    }
}

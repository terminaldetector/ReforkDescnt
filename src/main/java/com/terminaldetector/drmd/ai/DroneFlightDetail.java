package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.DroneEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Distant steering may coast between expensive decisions. Entity ticks, collision and damage remain live. */
public final class DroneFlightDetail {
    public enum PlanningTier { FULL, NEAR, REMOTE }
    public static final int NEAR_PLAN_TICKS=4,REMOTE_PLAN_TICKS=16;
    private static final double INTEREST_DISTANCE = 80;
    private static final double REMOTE_DISTANCE = 160;
    private static final double RENDER_DETAILS_DISTANCE = 56;

    private DroneFlightDetail() {}

    static boolean mayCoast(DroneEntity drone, LivingEntity target, double weaponRange, long ticksSinceSteering) {
        var tier=planningTier(drone,target,weaponRange);int interval=tier==PlanningTier.REMOTE?REMOTE_PLAN_TICKS:tier==PlanningTier.NEAR?NEAR_PLAN_TICKS:1;
        if (ticksSinceSteering <= 0 || ticksSinceSteering >= interval) return false;
        // Physical travel, unknown-space refusal and material collision remain
        // live on every coasting update; this is planning LOD, not entity sleep.
        Vec3d velocity = drone.getVelocity();
        return velocity.lengthSquared() > 1e-6 && FlightRouteSpace.clearMovement(drone, velocity);
    }

    /** Read-only interest classification. A native target selector still runs
     * normally, including actors above/below the old vanilla vertical band. */
    public static PlanningTier planningTier(DroneEntity drone){
        double range=com.terminaldetector.drmd.DescentMod.su("standoff".equals(drone.getRole().ai)?2800:2000);
        return planningTier(drone,drone.getTarget(),range);
    }
    private static PlanningTier planningTier(DroneEntity drone,LivingEntity target,double weaponRange){
        if(!(drone.getWorld() instanceof ServerWorld world)||!world.getServer().isOnThread()||!drone.isAlive()||drone.isRemoved()||drone.isAiDisabled()
            ||world.getPlayers().isEmpty()||drone.hurtTime>0||world.getTime()<drone.planningWakeUntil()
            ||drone.horizontalCollision||drone.verticalCollision||drone.hasPassengers()||drone.hasVehicle()
            ||drone.getHealth()/Math.max(1,drone.getMaxHealth())<.22f||drone.getRole()==AiRole.SUPPORT
            ||com.terminaldetector.drmd.world.threat.SurvivalThreatDirector.retreatFrom(drone)!=null)return PlanningTier.FULL;
        if(target!=null&&target.isAlive()&&target.getWorld()==world&&drone.squaredDistanceTo(target)<=(weaponRange+16)*(weaponRange+16))return PlanningTier.FULL;
        double nearest=Double.POSITIVE_INFINITY;
        for (var player : world.getPlayers()) {
            if(player.isAlive()&&!player.isSpectator())nearest=Math.min(nearest,player.squaredDistanceTo(drone));
        }
        if(nearest<=INTEREST_DISTANCE*INTEREST_DISTANCE)return PlanningTier.FULL;
        // Existing village/special ability goals retain the original4tick
        // maximum; they are never advertised as dormant by this adapter.
        if(drone.getRole().villageBomber||drone instanceof com.terminaldetector.drmd.entity.TeleportFlyerEntity)return PlanningTier.NEAR;
        return nearest>REMOTE_DISTANCE*REMOTE_DISTANCE?PlanningTier.REMOTE:PlanningTier.NEAR;
    }

    public static boolean showSmallHullDetails(double squaredCameraDistance) {
        return !Double.isFinite(squaredCameraDistance)
            || squaredCameraDistance < RENDER_DETAILS_DISTANCE * RENDER_DETAILS_DISTANCE;
    }
}

package com.terminaldetector.drmd.ai;

import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/** The formation supplies a moving destination to the drone's ordinary 3D movement adapter. */
public final class UfoEscortGoal extends Goal {
    private final DroneEntity drone;
    private final DroneNavigation navigation;

    public UfoEscortGoal(DroneEntity drone) {
        this.drone = drone;
        navigation = new DroneNavigation(drone);
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override public boolean canStart() {
        SkyUfoEntity carrier = drone.escortCarrier();
        if (carrier == null || !carrier.hasPoweredReactor() || drone.controlSuspended()) return false;
        var target = drone.getTarget();
        return !drone.combatAllowed() || target == null || !target.isAlive()
                || !HostileEnvironment.isHostileTarget(drone, target)
                || target.squaredDistanceTo(carrier) > 72 * 72 || drone.squaredDistanceTo(carrier) > 68 * 68;
    }

    @Override public boolean shouldContinue() { return canStart(); }
    @Override public boolean shouldRunEveryTick() { return true; }

    @Override public void tick() {
        SkyUfoEntity carrier = drone.escortCarrier();
        var slot = drone.escortSlot();
        if (carrier == null || slot == null || drone.controlSuspended()) return;
        if (!drone.combatAllowed()) drone.setTarget(null);
        else if (drone.age % 5 == 0 && drone.squaredDistanceTo(carrier) < 56 * 56) {
            var target = carrier.autonomousTarget().orElse(null);
            if (target != null && target.isAlive() && HostileEnvironment.isHostileTarget(drone, target)
                    && FlightRouteSpace.clearSight(drone, drone.getEyePos(), target.getEyePos(), 80))
                drone.setTarget(target);
        }
        Vec3d destination = slot.position(carrier);
        Vec3d delta = destination.subtract(drone.getBoundingBox().getCenter());
        double distance = delta.length();
        Vec3d desired = delta.normalize().multiply(Math.min(.65, distance * .18)).add(carrier.getVelocity());
        Vec3d steered = drone.steerFlightVelocity(navigation.quietSupport(desired, destination));
        drone.setVelocity(steered);
        drone.velocityModified = true;
        if (steered.lengthSquared() > .0025) FlightAttitude.steer(drone, steered, 6, .05f);
    }

    @Override public void stop() {
        navigation.stop();
        super.stop();
    }
}

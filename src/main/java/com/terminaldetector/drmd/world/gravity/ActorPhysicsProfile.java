package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.entity.mob.*;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import com.terminaldetector.drmd.world.end.EndReactorBossEntity;
import net.minecraft.entity.Entity;

/** Explicit locomotion adapters; NoGravity is an owner's flag, not a locomotion classifier. */
public final class ActorPhysicsProfile {
    private ActorPhysicsProfile() {}
    public static boolean flying(Entity entity) {
        return entity instanceof DroneEntity || entity instanceof ScannerEntity
            || entity instanceof OblivionSeekerEntity || entity instanceof ReactorKeeperEntity
            || entity instanceof EndReactorBossEntity || entity instanceof AirMineEntity;
    }
    public static boolean bankedModel(Entity entity) {
        return entity instanceof DroneEntity || entity instanceof ScannerEntity || entity instanceof OblivionSeekerEntity;
    }
    public static float roll(Entity entity) {
        if(entity instanceof DroneEntity drone)return drone.getFlightRoll();
        if(entity instanceof CyberMobEntity cyber)return cyber.getFlightRoll();
        return 0;
    }
}

package com.terminaldetector.drmd.world.gravity;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.entity.mob.*;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import com.terminaldetector.drmd.world.end.EndReactorBossEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.FishEntity;

/** Explicit locomotion adapters; NoGravity is an owner's flag, not a locomotion classifier. */
public final class ActorPhysicsProfile {
    /** Native constraints are explicit refusal, never a substitute body/teleport. */
    public enum GravityPolicy { FRAMED_GROUND, FORCE_ONLY, NATIVE_CONSTRAINT }
    public enum Family { GROUND, CLIMBER, FLYER, AQUATIC, MECHANICAL, SPECIAL, ANCHORED, FALLBACK }
    private ActorPhysicsProfile() {}
    public static boolean nativeConstraint(Entity entity) {
        return entity instanceof net.minecraft.entity.mob.ShulkerEntity
            || entity instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity
            || entity instanceof net.minecraft.entity.passive.BatEntity bat&&bat.isRoosting();
    }
    public static Family family(Entity entity) {
        if(entity instanceof net.minecraft.entity.mob.ShulkerEntity
            || entity instanceof net.minecraft.entity.passive.BatEntity bat&&bat.isRoosting())return Family.ANCHORED;
        if(entity instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity
            || entity instanceof net.minecraft.entity.boss.WitherEntity)return Family.SPECIAL;
        if(entity instanceof DroneEntity||entity instanceof ScannerEntity||entity instanceof OblivionSeekerEntity
            ||entity instanceof com.terminaldetector.drmd.entity.mob.TripodEntity
            ||entity instanceof com.terminaldetector.drmd.entity.mob.SpiderTurretEntity
            ||entity instanceof ReactorKeeperEntity||entity instanceof EndReactorBossEntity||entity instanceof AirMineEntity)return Family.MECHANICAL;
        if(entity instanceof net.minecraft.entity.mob.SpiderEntity)return Family.CLIMBER;
        if(aquatic(entity))return Family.AQUATIC;
        if(flying(entity))return Family.FLYER;
        if(ActorFrames.supported(entity))return Family.GROUND;
        return Family.FALLBACK;
    }
    public static GravityPolicy gravityPolicy(Entity entity) {
        if(nativeConstraint(entity))return GravityPolicy.NATIVE_CONSTRAINT;
        return ActorFrames.supported(entity)?GravityPolicy.FRAMED_GROUND:GravityPolicy.FORCE_ONLY;
    }
    /** Known water locomotion families; medium state remains owned by the native entity. */
    public static boolean aquatic(Entity entity) {
        return entity instanceof FishEntity||entity instanceof net.minecraft.entity.passive.SquidEntity
            ||entity instanceof net.minecraft.entity.passive.DolphinEntity
            ||entity instanceof net.minecraft.entity.passive.AxolotlEntity
            ||entity instanceof net.minecraft.entity.mob.GuardianEntity;
    }
    public static boolean submergedAquatic(Entity entity) {
        return aquatic(entity)&&entity.isTouchingWater()&&entity.isSubmergedInWater();
    }
    public static boolean flying(Entity entity) {
        // These native controllers own aerial motion. Flutterer alone also describes mixed
        // shoulder/ground species, so animation membership must not select this adapter.
        return entity instanceof FlyingEntity || entity instanceof BeeEntity
            || entity instanceof net.minecraft.entity.passive.BatEntity bat&&!bat.isRoosting()
            || entity instanceof net.minecraft.entity.passive.ParrotEntity parrot&&!parrot.isOnGround()&&!parrot.isInSittingPose()
            || entity instanceof net.minecraft.entity.boss.WitherEntity
            || entity instanceof AllayEntity || entity instanceof VexEntity
            || entity instanceof DroneEntity || entity instanceof ScannerEntity
            || entity instanceof OblivionSeekerEntity || entity instanceof ReactorKeeperEntity
            || entity instanceof EndReactorBossEntity || entity instanceof AirMineEntity;
    }
    /** Native fish own submerged motion and water drag; dry/shore movement keeps its existing policy. */
    public static boolean submergedFish(Entity entity) {
        return entity instanceof FishEntity fish && fish.isTouchingWater() && fish.isSubmergedInWater();
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

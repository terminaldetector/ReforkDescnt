package com.terminaldetector.drmd.weapon.projectile;

import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.DescentRocketFire;

/** Shared visual identities for the projectile tracker and cosmetic launch/contact messages. */
public enum ProjectileVisual {
    DEFAULT(Family.LIGHT),
    LASER(Family.LIGHT),
    SLUG(Family.BALLISTIC),
    VULCAN(Family.BALLISTIC),
    GATLING(Family.BALLISTIC),
    HELIX(Family.BALLISTIC),
    PLASMA(Family.PLASMA),
    FUSION(Family.FUSION),
    BURN(Family.LIGHT),
    DRILL(Family.DRILL),
    GRAVITY(Family.GRAVITY),
    MINE(Family.MINE),
    ROCKET(Family.ROCKET),
    HOMING(Family.ROCKET),
    GUIDED(Family.ROCKET),
    SMART(Family.ROCKET),
    MEGA(Family.ROCKET),
    FLASH(Family.ROCKET),
    MERCURY(Family.ROCKET),
    EARTHSHAKER(Family.ROCKET),
    DUAL(Family.ROCKET),
    HEAVY(Family.ROCKET),
    GENERIC_ROCKET(Family.ROCKET);

    public enum Family { LIGHT, BALLISTIC, PLASMA, FUSION, DRILL, GRAVITY, MINE, ROCKET }

    private final Family family;
    private static final ProjectileVisual[] VALUES = values();

    ProjectileVisual(Family family) { this.family = family; }
    public Family family() { return family; }

    public static ProjectileVisual fromWire(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : DEFAULT;
    }

    public static ProjectileVisual fromSave(String name) {
        for (ProjectileVisual visual : VALUES) {
            if (visual.name().equals(name)) return visual;
        }
        return DEFAULT;
    }

    public static ProjectileVisual fromProjectile(int mesh, DamageClass damage, boolean seeking, boolean guided) {
        return switch (mesh) {
            case ProjectileEntity.MESH_ROCKET -> guided ? GUIDED : seeking ? HOMING : GENERIC_ROCKET;
            case ProjectileEntity.MESH_ORB -> PLASMA;
            case ProjectileEntity.MESH_DRILL -> DRILL;
            case ProjectileEntity.MESH_MINE -> MINE;
            default -> damage == DamageClass.KINETIC ? SLUG : LASER;
        };
    }

    public static ProjectileVisual forMissile(DescentRocketFire.Kind kind) {
        return switch (kind) {
            case CONCUSSION -> ROCKET;
            case HOMING -> HOMING;
            case GUIDED -> GUIDED;
            case SMART -> SMART;
            case MEGA -> MEGA;
            case FLASH -> FLASH;
            case MERCURY -> MERCURY;
            case EARTHSHAKER -> EARTHSHAKER;
            case DUAL -> DUAL;
            case HEAVY -> HEAVY;
        };
    }
}

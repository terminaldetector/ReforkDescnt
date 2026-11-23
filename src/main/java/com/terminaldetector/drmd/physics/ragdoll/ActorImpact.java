package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.weapon.core.DamageClass;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Vec3d;

/** Carries an authoritative weapon contact through the synchronous damage/death call. */
public final class ActorImpact extends DamageSource {
    private final Vec3d point;
    private final Vec3d impulse;
    private final DamageClass damageClass;

    private ActorImpact(DamageSource source, Vec3d point, Vec3d impulse, DamageClass damageClass) {
        super(source.getTypeRegistryEntry(), source.getSource(), source.getAttacker());
        this.point = point;
        this.impulse = impulse;
        this.damageClass = damageClass;
    }

    public static DamageSource at(DamageSource source, Vec3d point, Vec3d direction, float amount,
                                  DamageClass damageClass) {
        if (point == null || direction == null || !Double.isFinite(point.lengthSquared())
                || !Double.isFinite(direction.lengthSquared()) || direction.lengthSquared() < 1e-12) return source;
        double coupling = switch (damageClass) {
            case KINETIC -> .15;
            case ENERGY -> .09;
            case EXPLOSIVE -> .24;
            case EXOTIC -> .12;
        };
        return new ActorImpact(source, point, direction.normalize().multiply(Math.min(80, amount * coupling)), damageClass);
    }

    public Vec3d point() { return point; }
    public Vec3d impulse() { return impulse; }
    public DamageClass damageClass() { return damageClass; }

    /** A localized live contact feeds existing travel and retains the actor's motor ownership. */
    public void applyLiveRecoil(LivingEntity actor, double mass) {
        double resistance = actor.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        double speed = Math.min(.35, impulse.length() / (mass * 20)) * Math.max(0, 1 - resistance);
        if (speed <= 0) return;
        Vec3d delta = impulse.normalize().multiply(speed);
        actor.addVelocity(delta.x, delta.y, delta.z);
        actor.velocityModified = true;
    }
}

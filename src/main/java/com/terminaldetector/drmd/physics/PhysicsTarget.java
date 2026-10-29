package com.terminaldetector.drmd.physics;

import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * A server-side physical object that can be addressed by weapons without pretending to be a
 * {@code LivingEntity}.
 *
 * <p>The contract is deliberately small. Block assemblies and articulated ragdolls have different
 * shapes and solvers, but a projectile or gravity tool only needs an exact ray hit, a world-space
 * impulse and the object's aggregate motion. Keeping those operations here prevents every weapon
 * from growing another {@code instanceof BlockBodyEntity || instanceof RagdollEntity} branch.</p>
 */
public interface PhysicsTarget {
	/** Exact shape raycast in world coordinates. */
	Optional<Vec3d> physicsRaycast(Vec3d start, Vec3d end);

	/** Apply a world-space impulse at a world-space point. */
	void applyPhysicsImpulse(Vec3d impact, Vec3d impulse);

    /** World COM; an entity's tracking pivot may be different after geometry edits. */
    default Vec3d physicsCentre(){return this instanceof net.minecraft.entity.Entity e?e.getPos():Vec3d.ZERO;}
    /** Occupied aim point; keep the existing torso target for other physical entities. */
    default Vec3d physicsAimPoint(){return this instanceof net.minecraft.entity.Entity e?e.getPos().add(0,e.getHeight()*.5,0):physicsCentre();}

    /** Weapon contact, separate from gravity-tool impulses. Hulls may also damage local material. */
    default void weaponHit(Vec3d impact, Vec3d incoming, float damage) {
        if (!Float.isFinite(damage) || damage <= 0 || !Double.isFinite(incoming.lengthSquared())) return;
        applyPhysicsImpulse(impact, incoming.normalize().multiply(Math.min(80, damage * .15)));
    }

    /** Radial weapon load; the default adapter keeps the former COM falloff. */
    default void weaponBlast(Vec3d origin, float damage, double radius) {
        if (!Float.isFinite(damage) || damage <= 0 || !Double.isFinite(radius) || radius <= 0) return;
        Vec3d centre = physicsCentre(); double distance = centre.distanceTo(origin);
        if (distance >= radius) return;
        Vec3d direction = centre.subtract(origin);
        if (direction.lengthSquared() < 1e-8) direction = new Vec3d(0, 1, 0);
        applyPhysicsImpulse(centre, direction.normalize().multiply(Math.min(80, damage * (1-distance/radius) * .15)));
    }

	/** Aggregate velocity in blocks per second, matching the D6 physics core. */
	Vec3d physicsVelocity();

	/** Total inertial mass used by hold springs and throw tools. */
	double physicsMass();

	/**
	 * Dominant world-space axis, when the body has one.
	 *
	 * <p>The heavy gravity rail uses this to point a log, column or articulated wreck down the
	 * launch corridor. A spherical or purely translational adapter can keep the default.</p>
	 */
	default Optional<Vec3d> physicsLongAxis() {
		return Optional.empty();
	}

	/** Aggregate angular velocity in radians per second. */
	default Vec3d physicsAngularVelocity() {
		return Vec3d.ZERO;
	}

	/** Apply a world-space torque. Translational-only adapters may ignore it. */
	default void applyPhysicsTorque(Vec3d torque) {
	}

	/** Opt the body into world/local gravity after a gravity-shifter launch. */
	default void enablePhysicsGravity() {
	}
}

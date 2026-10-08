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

	/** Aggregate velocity in blocks per second, matching the D6 physics core. */
	Vec3d physicsVelocity();

	/** Total inertial mass used by hold springs and throw tools. */
	double physicsMass();
}

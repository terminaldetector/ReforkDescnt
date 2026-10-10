package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative articulated rigid-body core. It owns no Minecraft entity or renderer: those
 * are adapters around the same deterministic segment state, not alternative physics paths.
 */
public final class RagdollSimulation {
	public static final int DEFAULT_SUBSTEPS = 4;
	public static final int DEFAULT_SOLVER_ITERATIONS = 8;
	/**
	 * Maximum distance a complete joint correction may close in one solver iteration.
	 *
	 * <p>Contacts are resolved between iterations, so this must remain smaller than a block face.
	 * Without the cap, a pinned limb can accumulate enough error for one positional correction to
	 * move its partner from one side of a wall to the other without ever overlapping the wall.</p>
	 */
	private static final double MAX_JOINT_CORRECTION = .12;

	/** Minecraft-facing adapters may correct a part after each integration/constraint phase. */
	@FunctionalInterface
	public interface CollisionResolver {
		void resolve(Part part, Vec3 fallbackPosition);
	}

	public static final class Part {
		private final RagdollRig.Segment segment;
		private final D6PhysicsBody body;

		private Part(RagdollRig.Segment segment, D6PhysicsBody body) {
			this.segment = segment;
			this.body = body;
		}

		public RagdollRig.Segment segment() { return segment; }
		public D6PhysicsBody body() { return body; }
	}

	private final RagdollRig rig;
	private final List<Part> parts;

	public RagdollSimulation(RagdollRig rig, Vec3 origin, Quat rotation, Vec3 initialVelocity) {
		if (rig == null || origin == null || rotation == null || initialVelocity == null)
			throw new IllegalArgumentException("ragdoll construction has null state");
		this.rig = rig;
		List<Part> built = new ArrayList<>(rig.segments().size());
		for (RagdollRig.Segment segment : rig.segments()) {
			Vec3 half = segment.halfExtents();
			double width = half.x() * 2, height = half.y() * 2, depth = half.z() * 2;
			double scale = segment.mass() / 12.0;
			D6Mat3 inertia = D6Mat3.diagonal(
					scale * (height * height + depth * depth),
					scale * (width * width + depth * depth),
					scale * (width * width + height * height));
			D6PhysicsBody body = new D6PhysicsBody()
					.withMass(segment.mass())
					.withInertia(inertia)
					.withPosition(origin.plus(rotation.rotate(segment.restPosition())))
					.withRotation(rotation)
					.withLinearVelocity(initialVelocity)
					.withLimits(35, 12);
			built.add(new Part(segment, body));
		}
		this.parts = List.copyOf(built);
	}

	public RagdollRig rig() { return rig; }
	public List<Part> parts() { return parts; }

	public void applyImpulse(int part, Vec3 impulse, Vec3 localPoint) {
		Part target = parts.get(part);
		Vec3 offset = target.body().rotation().rotate(localPoint);
		target.body().applyImpulse(impulse, offset);
	}

	public void step(double seconds, Vec3 gravity) {
		step(seconds, gravity, DEFAULT_SUBSTEPS, DEFAULT_SOLVER_ITERATIONS, null);
	}

	public void step(double seconds, Vec3 gravity, int substeps, int solverIterations) {
		step(seconds, gravity, substeps, solverIterations, null);
	}

	/**
	 * Advances the articulated body while allowing the world adapter to resolve terrain contacts.
	 * The pure overloads above remain deterministic and Minecraft-free for unit tests.
	 */
	public void step(double seconds, Vec3 gravity, int substeps, int solverIterations,
			CollisionResolver collisionResolver) {
		if (!Double.isFinite(seconds) || seconds <= 0 || seconds > .25)
			throw new IllegalArgumentException("invalid ragdoll timestep");
		if (gravity == null || !finite(gravity)) throw new IllegalArgumentException("invalid ragdoll gravity");
		if (substeps < 1 || substeps > 32 || solverIterations < 1 || solverIterations > 64)
			throw new IllegalArgumentException("invalid ragdoll solver budget");
		double dt = seconds / substeps;
		Vec3[] beforeConstraints = collisionResolver == null ? null : new Vec3[parts.size()];
		for (int substep = 0; substep < substeps; substep++) {
			for (Part part : parts) {
				Vec3 before = part.body().position();
				part.body().applyForce(gravity.scaled(part.body().mass()));
				part.body().step(dt);
				if (collisionResolver != null) collisionResolver.resolve(part, before);
			}
			for (int iteration = 0; iteration < solverIterations; iteration++) {
				if (collisionResolver != null)
					for (int part = 0; part < parts.size(); part++)
						beforeConstraints[part] = parts.get(part).body().position();
				for (RagdollRig.Joint joint : rig.joints()) solve(joint, dt);
				// Positional constraints are capable of crossing terrain just as integration is.
				// Resolve each iteration while its correction is still short and unambiguous.
				if (collisionResolver != null)
					for (int part = 0; part < parts.size(); part++)
						collisionResolver.resolve(parts.get(part), beforeConstraints[part]);
			}
		}
	}

	/** Aggregate centre of mass in world space. */
	public Vec3 centreOfMass() {
		double mass = totalMass();
		Vec3 weighted = new Vec3(0, 0, 0);
		for (Part part : parts) weighted = weighted.plus(part.body().position().scaled(part.body().mass()));
		return weighted.scaled(1.0 / mass);
	}

	/** Aggregate linear velocity in world space. */
	public Vec3 centreVelocity() {
		double mass = totalMass();
		Vec3 weighted = new Vec3(0, 0, 0);
		for (Part part : parts) weighted = weighted.plus(part.body().linearVelocity().scaled(part.body().mass()));
		return weighted.scaled(1.0 / mass);
	}

	public double totalMass() {
		double result = 0;
		for (Part part : parts) result += part.body().mass();
		return result;
	}

	public double jointError(int jointIndex) {
		RagdollRig.Joint joint = rig.joints().get(jointIndex);
		Part first = parts.get(joint.first()), second = parts.get(joint.second());
		Vec3 a = first.body().position().plus(first.body().rotation().rotate(joint.firstAnchor()));
		Vec3 b = second.body().position().plus(second.body().rotation().rotate(joint.secondAnchor()));
		return b.minus(a).length();
	}

	private void solve(RagdollRig.Joint joint, double dt) {
		Part first = parts.get(joint.first()), second = parts.get(joint.second());
		D6PhysicsBody a = first.body(), b = second.body();
		Vec3 ra = a.rotation().rotate(joint.firstAnchor());
		Vec3 rb = b.rotation().rotate(joint.secondAnchor());
		Vec3 error = b.position().plus(rb).minus(a.position().plus(ra));
		double distance = error.length();
		if (distance < 1e-9) return;
		Vec3 normal = error.scaled(1.0 / distance);
		double invA = 1.0 / a.mass(), invB = 1.0 / b.mass(), invSum = invA + invB;
		double softness = joint.compliance() / (dt * dt);
		double correctionScale = 1.0 / (invSum + softness);
		Vec3 correction = error.scaled(correctionScale);
		double closure = correction.length() * invSum;
		if (closure > MAX_JOINT_CORRECTION)
			correction = correction.scaled(MAX_JOINT_CORRECTION / closure);
		Vec3 moveA = correction.scaled(invA);
		Vec3 moveB = correction.scaled(-invB);
		a.withPosition(a.position().plus(moveA));
		b.withPosition(b.position().plus(moveB));

		// Remove velocity that keeps opening the joint. Applying it at the anchors also produces the
		// angular response a ragdoll needs when an arm or spar is struck off-centre.
		double separating = b.velocityAtPoint(rb).minus(a.velocityAtPoint(ra)).dot(normal);
		if (separating > 0) {
			double impulseMagnitude = separating / invSum;
			Vec3 impulse = normal.scaled(impulseMagnitude);
			a.applyImpulse(impulse, ra);
			b.applyImpulse(impulse.scaled(-1), rb);
		}
	}

	private static boolean finite(Vec3 value) {
		return Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
	}
}

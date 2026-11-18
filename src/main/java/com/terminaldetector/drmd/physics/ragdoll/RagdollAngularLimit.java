package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;

/** Rest-frame angular constraints on the existing D6 bodies; no separate integration path. */
public final class RagdollAngularLimit {
	private static final Vec3 X = new Vec3(1, 0, 0);
	private static final Vec3 Y = new Vec3(0, 1, 0);
	private static final double MAX_CORRECTION = .08;
	private static final double LIMIT_SLOP = 1e-7;

	private RagdollAngularLimit() {}

	public record Angles(double swing, double twist) {}

	public static Angles angles(Quat first, Quat second, RagdollRig.AngularLimits limits) {
		Quat a = multiply(first, limits.firstFrame());
		Quat b = multiply(second, limits.secondFrame());
		Quat relative = multiply(a.inverse(), b).normalized();
		Vec3 direction = relative.rotate(X);
		double swing = Math.acos(clamp(direction.x(), -1, 1));
		// At exactly pi swing the twist axis is ambiguous. Choose zero deterministically, then
		// correct swing before evaluating twist again. The q/-q representation gives the same result.
		double twist = relative.x() * relative.x() + relative.w() * relative.w() < 1e-16
				? 0 : wrap(2 * Math.atan2(relative.x(), relative.w()));
		if (twist == Math.PI && limits.minTwist() == -Math.PI && limits.maxTwist() < Math.PI)
			twist = -Math.PI;
		return new Angles(swing, twist);
	}

	static void solve(D6PhysicsBody first, D6PhysicsBody second, RagdollRig.AngularLimits limits) {
		solve(first, second, limits, MAX_CORRECTION);
	}

	static void solve(D6PhysicsBody first, D6PhysicsBody second, RagdollRig.AngularLimits limits,
			double maximumCorrection) {
		Quat frameA = multiply(first.rotation(), limits.firstFrame());
		Quat frameB = multiply(second.rotation(), limits.secondFrame());
		Vec3 axisA = frameA.rotate(X), axisB = frameB.rotate(X);
		double swing = Math.acos(clamp(axisA.dot(axisB), -1, 1));
		if (swing >= limits.maxSwing() - LIMIT_SLOP) {
			Vec3 cross = axisA.cross(axisB);
			// Antiparallel axes have no unique shortest path. A joint-local axis makes the choice
			// independent of world gravity and quaternion sign.
			Vec3 axis = cross.lengthSquared() > 1e-16 ? cross.normalized() : frameA.rotate(Y);
			project(first, second, axis, Math.max(0, swing - limits.maxSwing()), maximumCorrection);
			if (swing < 1e-8) {
				// A zero cone constrains both transverse axes, including at its degenerate centre.
				stopBothDirections(first, second, frameA.rotate(Y));
				stopBothDirections(first, second, frameA.rotate(new Vec3(0, 0, 1)));
			}
		}

		if (limits.minTwist() == -Math.PI && limits.maxTwist() == Math.PI) return;
		frameA = multiply(first.rotation(), limits.firstFrame());
		frameB = multiply(second.rotation(), limits.secondFrame());
		axisA = frameA.rotate(X);
		axisB = frameB.rotate(X);
		double denominator = 1 + axisA.dot(axisB);
		if (denominator < 1e-10) return; // swing projection will leave this singularity on later iterations
		// The twist derivative is (axisA+axisB)/(1+axisA.axisB), not world X. Its norm converts
		// twist error to an angular correction about the normalized gradient.
		Vec3 gradient = axisA.plus(axisB).scaled(1 / denominator);
		double gradientLength = gradient.length();
		Vec3 axis = gradient.scaled(1 / gradientLength);
		double twist = angles(first.rotation(), second.rotation(), limits).twist();
		if (twist > limits.maxTwist() || twist < limits.minTwist()) {
			// Outside a signed interval the closest boundary can lie across the +/-pi seam.
			// Project along that short arc rather than folding a wing almost a full turn.
			double upperError = wrap(twist - limits.maxTwist());
			double lowerError = wrap(twist - limits.minTwist());
			double error = Math.abs(lowerError) < Math.abs(upperError) ? lowerError : upperError;
			project(first, second, error < 0 ? axis.scaled(-1) : axis,
					Math.abs(error) / gradientLength, maximumCorrection);
		} else if (twist >= limits.maxTwist() - LIMIT_SLOP)
			project(first, second, axis, 0, maximumCorrection);
		else if (twist <= limits.minTwist() + LIMIT_SLOP)
			project(first, second, axis.scaled(-1), 0, maximumCorrection);
		if (limits.minTwist() == limits.maxTwist()) stopBothDirections(first, second, axis);
	}

	private static void project(D6PhysicsBody a, D6PhysicsBody b, Vec3 axis, double error, double maximumCorrection) {
		D6Mat3 inverseA = a.worldInverseInertia(), inverseB = b.worldInverseInertia();
		if (inverseA == null || inverseB == null) return;
		Vec3 responseA = inverseA.transform(axis), responseB = inverseB.transform(axis);
		double inverseMass = axis.dot(responseA.plus(responseB));
		if (inverseMass <= 1e-12) return;
		if (error > LIMIT_SLOP) {
			double before = angularEnergy(a) + angularEnergy(b);
			double lambda = Math.min(error / inverseMass,
					Math.min(MAX_CORRECTION, maximumCorrection) / (responseA.length() + responseB.length()));
			turn(a, responseA.scaled(lambda));
			turn(b, responseB.scaled(-lambda));
			// Rotating anisotropic inertia at fixed world momentum can add kinetic energy. This
			// inelastic stabilization only removes that gain, never converts angle error into speed.
			// It may dissipate angular momentum; the velocity impulse below conserves the pair's L.
			double after = angularEnergy(a) + angularEnergy(b);
			if (after > before && after > 1e-20) {
				double scale = Math.sqrt(Math.max(0, before) / after);
				a.withAngularMomentum(a.angularMomentum().scaled(scale));
				b.withAngularMomentum(b.angularMomentum().scaled(scale));
			}
		}
		stopOutward(a, b, axis);
	}

	private static void stopBothDirections(D6PhysicsBody a, D6PhysicsBody b, Vec3 axis) {
		stopOutward(a, b, axis);
		stopOutward(a, b, axis.scaled(-1));
	}

	private static void stopOutward(D6PhysicsBody a, D6PhysicsBody b, Vec3 axis) {
		double speed = b.angularVelocity().minus(a.angularVelocity()).dot(axis);
		if (speed <= 0) return; // no restitution, bias velocity or braking of inward/free motion
		D6Mat3 inverseA = a.worldInverseInertia(), inverseB = b.worldInverseInertia();
		if (inverseA == null || inverseB == null) return;
		double denominator = axis.dot(inverseA.transform(axis).plus(inverseB.transform(axis)));
		if (denominator <= 1e-12) return;
		Vec3 impulse = axis.scaled(speed / denominator);
		a.withAngularMomentum(a.angularMomentum().plus(impulse));
		b.withAngularMomentum(b.angularMomentum().minus(impulse));
	}

	private static double angularEnergy(D6PhysicsBody body) {
		return .5 * body.angularMomentum().dot(body.angularVelocity());
	}

	private static void turn(D6PhysicsBody body, Vec3 rotation) {
		double angle = rotation.length();
		if (angle > 1e-12)
			body.withRotation(multiply(ImmPtlQuaternions.rotationByRadians(rotation, angle), body.rotation()));
	}

	private static Quat multiply(Quat a, Quat b) {
		return ImmPtlQuaternions.hamiltonProduct(a, b);
	}

	private static double wrap(double angle) {
		if (angle > Math.PI) angle -= Math.PI * 2;
		if (angle < -Math.PI) angle += Math.PI * 2;
		// pi and -pi describe the same orientation; use one side for q/-q stability.
		return Math.abs(angle) >= Math.PI - 1e-12 ? Math.PI : angle;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}

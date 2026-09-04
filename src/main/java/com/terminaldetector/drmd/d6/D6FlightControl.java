package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/**
 * Turns what a pilot or an autopilot <em>wants</em> into forces and torques on a body.
 *
 * <p><b>The gap this fills.</b> The integration plan asks navigation and the AI roles to emit desired
 * velocities and never touch a transform, and asks physics to accept only forces and torques. Nothing
 * in it connects the two. This is that connection, and the shape of it is taken from Eureka's own
 * control class, read during the source audit — see {@code docs/source-audit/algorithm-map.md}.
 *
 * <p><b>The bridge is one multiplication:</b> torque is the inertia tensor times the desired angular
 * acceleration. The caller says how fast it wants the turn rate to change; {@code τ = Iα} converts
 * that into a torque, and the consequence is the point — a heavy hull and a light one answer the
 * stick at the same rate, so input maps to feel rather than to force.
 *
 * <p><b>Turn limits come from the hull's size, not from a constant.</b> Angular acceleration times
 * radius is linear acceleration, so a large ship turning at a given rate throws its extremities
 * faster than a small one. What is capped here is therefore the speed of the <em>tip</em>, and the
 * angular caps fall out of it by division. One pair of numbers then covers every hull instead of a
 * table of per-size constants.
 *
 * <p><b>Drive and brake are one expression.</b> With input and headroom, the input drives; with no
 * input or past the cap, the negated rate brakes. Not a separate assist that can disagree with the
 * accelerator — which is what DRMD has today, with flight assist as its own flag on its own path.
 *
 * <p>Pure and stateless: every method takes the body and the wish and applies to the accumulators.
 * Nothing here decides when to run, which is what lets it be tested as arithmetic.
 */
public final class D6FlightControl {
	private D6FlightControl() {}

	/**
	 * Ask the body to turn, per axis, at up to its limits.
	 *
	 * @param input  per world axis in −1..1: how hard to turn about each. Values outside are clamped,
	 *               because a caller feeding 3.0 wants "as hard as possible" and not three times the
	 *               limit.
	 * @param radius the hull's reach from its centre of mass, in blocks — the distance whose tip speed
	 *               is being limited. Clamped below so a single-block craft is not given an infinite
	 *               turn rate by division.
	 * @param maxTipSpeed how fast an extremity may travel sideways, in blocks per second.
	 * @param maxTipAcceleration how fast that tip speed may change, in blocks per second squared.
	 */
	public static void steer(D6PhysicsBody body, Vec3 input, double radius,
			double maxTipSpeed, double maxTipAcceleration) {
		double reach = Math.max(radius, 0.5);
		double maxRate = maxTipSpeed / reach;
		double maxAngularAcceleration = maxTipAcceleration / reach;

		Vec3 rate = body.angularVelocity();
		Vec3 desired = new Vec3(
				axisAcceleration(clamp(input.x(), -1, 1), rate.x(), maxRate, maxAngularAcceleration),
				axisAcceleration(clamp(input.y(), -1, 1), rate.y(), maxRate, maxAngularAcceleration),
				axisAcceleration(clamp(input.z(), -1, 1), rate.z(), maxRate, maxAngularAcceleration));

		// The bridge: a wanted angular acceleration becomes a torque through the inertia tensor.
		body.applyTorque(body.worldInertia().transform(desired));
	}

	/**
	 * One axis of {@link #steer}: drive while there is input and headroom, brake otherwise.
	 *
	 * <p>The brake term is the rate itself, clamped to one and negated, so it fades out as the body
	 * comes to rest instead of overshooting through zero and hunting.
	 */
	private static double axisAcceleration(double input, double rate, double maxRate,
			double maxAngularAcceleration) {
		boolean headroom = Math.abs(rate) < maxRate;
		double normalized = headroom && input != 0 ? input : -clamp(rate, -1, 1);
		return normalized * maxAngularAcceleration;
	}

	/**
	 * Push along a direction.
	 *
	 * <p>Scaled by mass so that {@code acceleration} means what it says: a laden hull and an empty one
	 * accelerate alike for the same throttle, which is what a pilot expects of a thruster rated in
	 * gravities rather than in newtons.
	 */
	public static void thrust(D6PhysicsBody body, Vec3 direction, double acceleration) {
		if (acceleration == 0) return;
		body.applyForce(direction.normalized().scaled(acceleration * body.mass()));
	}

	/**
	 * Hold a velocity — the servo the plan's {@code D6_DesiredVel} needs.
	 *
	 * <p>A proportional controller: force is the velocity error times the gain times the mass. Mass
	 * cancels in the acceleration, so the response time is the same for every hull, and {@code gain}
	 * is in reciprocal seconds — how much of the error is removed per second.
	 *
	 * <p>The one number worth knowing about it: with {@code gain × dt = 1} a single step lands exactly
	 * on the target, and above that it overshoots. A caller stepping at twenty a second should keep
	 * the gain under twenty.
	 */
	public static void holdVelocity(D6PhysicsBody body, Vec3 desiredVelocity, double gain) {
		Vec3 error = desiredVelocity.minus(body.linearVelocity());
		body.applyForce(error.scaled(gain * body.mass()));
	}

	/**
	 * Hold a velocity against a constant acceleration such as gravity.
	 *
	 * <p>The feed-forward term is what removes the steady-state droop: a plain proportional servo has
	 * to keep an error to produce the force that fights gravity, so it sits permanently below its
	 * target. Subtracting the known disturbance first lets the error go to zero.
	 */
	public static void holdVelocityAgainst(D6PhysicsBody body, Vec3 desiredVelocity, double gain,
			Vec3 constantAcceleration) {
		Vec3 error = desiredVelocity.minus(body.linearVelocity()).minus(constantAcceleration.scaled(1.0 / gain));
		body.applyForce(error.scaled(gain * body.mass()));
	}

	/**
	 * A saturating curve for raw input: rises like {@code x} near zero and flattens toward
	 * {@code max}, so a control approaches its limit instead of hitting it.
	 *
	 * <p>{@code atan}-shaped, as Eureka's is. Clipping at the limit makes the last part of a stick's
	 * travel do nothing, which reads as a dead zone at the wrong end.
	 *
	 * <p>The bound is {@code max × 0.638 × π/2}, which is {@code max} to within a fifth of a percent
	 * rather than exactly {@code max} — 0.638 is the donor's rounding of {@code 2/π = 0.63662}, and
	 * it is kept because the number is a feel constant and not a hard limit. Anything that must not
	 * be exceeded should be clamped by its owner, not left to this curve.
	 */
	public static double saturate(double value, double max) {
		if (max <= 0) return 0;
		double softness = 1.0 / (max * 0.638);
		return Math.atan(value * softness) / softness;
	}

	private static double clamp(double value, double low, double high) {
		return value < low ? low : Math.min(value, high);
	}
}

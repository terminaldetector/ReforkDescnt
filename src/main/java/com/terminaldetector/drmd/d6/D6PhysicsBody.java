package com.terminaldetector.drmd.d6;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;

/**
 * A rigid body with six degrees of freedom, moved by forces and torques.
 *
 * <p><b>Why this exists.</b> DRMD's flight has been kinematic since it was written:
 * {@code ShipAttitude} keeps orientation as a forward/up vector pair turned by increments, and
 * {@code FlightSystem} ends in {@code setVelocity}. There is no mass, no inertia tensor and no
 * angular velocity as state, so nothing about a ship's motion follows from what it is made of — a
 * heavy hull turns exactly as fast as a light one. This is the replacement, and it is a replacement
 * rather than a refactor: everything that tunes the feel of flight has to be re-expressed in forces
 * and torques.
 *
 * <p><b>What the state is, and where each part lives.</b>
 *
 * <ul>
 *   <li>{@code position} and {@code rotation} — world space.</li>
 *   <li>{@code linearVelocity} — world axes.</li>
 *   <li>{@code angularMomentum} — world axes, and <b>not</b> angular velocity. See below.</li>
 *   <li>{@code inertia} — <b>body</b> axes, and deliberately: it is a property of the shape and
 *       changes only when the shape does. World axes are reached by {@code R I Rᵀ}, recomputed each
 *       step because {@code R} changes each step.</li>
 *   <li>{@code force} and {@code torque} — accumulators, cleared by every {@link #step}.</li>
 * </ul>
 *
 * <p><b>Angular momentum is the state, not angular velocity — and this is a deliberate departure
 * from the donor.</b> Valkyrien Skies keeps angular velocity and leaves it alone when no torque is
 * applied. That is wrong for anything but a sphere: a torque-free body conserves angular
 * <em>momentum</em>, while its angular velocity precesses, and holding ω fixed instead means the
 * momentum quietly drifts. Measured on an asymmetric body spun near its intermediate axis over
 * 8,000 steps: holding ω fixed moved L by 0.36 out of 12 and produced no precession at all, while
 * holding L fixed kept it exact and reproduced the tumble. A 6DoF game about asymmetric craft is
 * precisely where that difference is visible, so DRMD keeps L and derives ω from it.
 *
 * <p><b>Forces, not impulses.</b> The accumulators hold force and torque, and {@code dt} is applied
 * once at integration. Valkyrien Skies folds {@code dt} into the accumulator instead, so its
 * "torque" is really an angular impulse — a defensible choice, and one worth naming here because
 * reading their code with this convention in mind is how the two get mixed up. See
 * {@code docs/source-audit/algorithm-map.md}.
 *
 * <p>Pure: no Minecraft, no threads, no ticking. Something else decides when to call {@link #step}
 * and with what, which is what lets the whole of this be tested as arithmetic.
 */
public final class D6PhysicsBody {

	/**
	 * Below this angular speed the rotation is left alone.
	 *
	 * <p>Not an optimisation. Building an axis-angle turn out of an angular velocity requires
	 * normalizing it, and normalizing something arbitrarily close to zero produces an arbitrary axis;
	 * the quaternion then accumulates a rotation about a direction that means nothing.
	 */
	private static final double MIN_ANGULAR_SPEED = 1e-6;

	private Vec3 position = new Vec3(0, 0, 0);
	private Quat rotation = Quat.IDENTITY;
	private Vec3 linearVelocity = new Vec3(0, 0, 0);
	private Vec3 angularMomentum = new Vec3(0, 0, 0);

	private double mass;
	private double inverseMass;
	private D6Mat3 inertia = D6Mat3.ZERO;
	private D6Mat3 inverseInertia;

	private Vec3 force = new Vec3(0, 0, 0);
	private Vec3 torque = new Vec3(0, 0, 0);

	private double maxSpeed = Double.POSITIVE_INFINITY;
	private double maxAngularSpeed = Double.POSITIVE_INFINITY;

	/**
	 * How many steps the rotation has taken since it was last renormalized.
	 *
	 * <p>Composing a quaternion thousands of times drifts off unit length, and the donor records what
	 * that drift broke for it: teleportation and collision, both of which read the rotation back to
	 * decide where something is. Cheaper to snap it periodically than to normalize every step.
	 */
	private int stepsSinceFix;
	private static final int STEPS_PER_DRIFT_FIX = 512;

	/** Sets mass, centre of mass and inertia from an accumulated shape. */
	public D6PhysicsBody withMassProperties(D6MassProperties properties) {
		return withMass(properties.mass()).withInertia(properties.inertia());
	}

	public D6PhysicsBody withMass(double newMass) {
		this.mass = newMass;
		this.inverseMass = newMass > 1e-9 ? 1.0 / newMass : 0.0;
		return this;
	}

	/** The inertia tensor in body axes. Its inverse is cached, since every step needs it. */
	public D6PhysicsBody withInertia(D6Mat3 bodyInertia) {
		this.inertia = bodyInertia;
		this.inverseInertia = bodyInertia.inverse();
		return this;
	}

	public D6PhysicsBody withPosition(Vec3 newPosition) {
		this.position = newPosition;
		return this;
	}

	public D6PhysicsBody withRotation(Quat newRotation) {
		this.rotation = newRotation.normalized();
		return this;
	}

	public D6PhysicsBody withLinearVelocity(Vec3 velocity) {
		this.linearVelocity = velocity;
		return this;
	}

	/**
	 * Set the angular velocity by converting it to momentum.
	 *
	 * <p><b>Call this after {@link #withInertia} and {@link #withRotation}</b>, since the conversion
	 * needs both. Set momentum directly with {@link #withAngularMomentum} to avoid the ordering
	 * entirely.
	 */
	public D6PhysicsBody withAngularVelocity(Vec3 velocity) {
		this.angularMomentum = worldInertia().transform(velocity);
		return this;
	}

	public D6PhysicsBody withAngularMomentum(Vec3 momentum) {
		this.angularMomentum = momentum;
		return this;
	}

	/**
	 * Caps, off by default.
	 *
	 * <p>Worth having and worth being opt-in: a runaway body at this project's speeds outruns chunk
	 * loading long before it becomes numerically interesting, and a cap silently applied is a cap
	 * nobody can find later.
	 */
	public D6PhysicsBody withLimits(double newMaxSpeed, double newMaxAngularSpeed) {
		this.maxSpeed = newMaxSpeed;
		this.maxAngularSpeed = newMaxAngularSpeed;
		return this;
	}

	public Vec3 position() {
		return position;
	}

	public Quat rotation() {
		return rotation;
	}

	public Vec3 linearVelocity() {
		return linearVelocity;
	}

	/**
	 * Derived from the momentum and the current orientation, so it changes as the body tumbles even
	 * with nothing pushing it. That is the precession, and it is the point.
	 */
	public Vec3 angularVelocity() {
		D6Mat3 worldInverse = worldInverseInertia();
		return worldInverse == null ? new Vec3(0, 0, 0) : worldInverse.transform(angularMomentum);
	}

	public double mass() {
		return mass;
	}

	public D6Mat3 inertia() {
		return inertia;
	}

	/** Through the centre of mass, so it turns nothing. */
	public void applyForce(Vec3 newForce) {
		force = force.plus(newForce);
	}

	/**
	 * A force applied somewhere other than the centre of mass, which both pushes and turns.
	 *
	 * @param offsetFromCentreOfMass in <b>world</b> axes — the lever arm, already rotated. A thruster
	 *                               fixed to the hull has a constant offset in body space, so the
	 *                               caller rotates it by {@link #rotation} first; doing that here
	 *                               would rotate an offset that is already in world axes when it
	 *                               comes from a collision instead.
	 */
	public void applyForceAtPoint(Vec3 newForce, Vec3 offsetFromCentreOfMass) {
		force = force.plus(newForce);
		torque = torque.plus(offsetFromCentreOfMass.cross(newForce));
	}

	public void applyTorque(Vec3 newTorque) {
		torque = torque.plus(newTorque);
	}

	/** The inertia tensor in world axes right now. */
	public D6Mat3 worldInertia() {
		return inertia.rotatedBy(rotation);
	}

	/** Its inverse, or null when the body has no inertia to speak of yet. */
	private D6Mat3 worldInverseInertia() {
		if (inverseInertia == null) return null;
		D6Mat3 r = D6Mat3.rotationOf(rotation);
		return r.multiply(inverseInertia).multiply(r.transposed());
	}

	/**
	 * Advance by {@code seconds} and clear the accumulators.
	 *
	 * <p>Order matters and is the usual one: turn the accumulated force and torque into velocity,
	 * then move by the velocity. Integrating position before applying this step's force would make a
	 * body respond one step late, which is invisible at sixty steps a second and obvious at twenty.
	 */
	public void step(double seconds) {
		if (seconds <= 0) return;

		// Torque goes straight into momentum: no inertia tensor is involved, which is the whole reason
		// momentum is the state. The tensor only appears when velocity is wanted.
		angularMomentum = angularMomentum.plus(torque.scaled(seconds));
		linearVelocity = linearVelocity.plus(force.scaled(inverseMass * seconds));

		force = new Vec3(0, 0, 0);
		torque = new Vec3(0, 0, 0);

		linearVelocity = clamped(linearVelocity, maxSpeed);

		Vec3 angular = angularVelocity();
		Vec3 limited = clamped(angular, maxAngularSpeed);
		if (limited != angular) {
			// Clamped in velocity, then written back as momentum — otherwise the next step derives the
			// unclamped velocity again from the momentum that was never limited.
			angularMomentum = worldInertia().transform(limited);
			angular = limited;
		}

		position = position.plus(linearVelocity.scaled(seconds));
		integrateRotation(angular, seconds);
	}

	private void integrateRotation(Vec3 angular, double seconds) {
		double angularSpeed = angular.length();
		if (angularSpeed < MIN_ANGULAR_SPEED) return;

		// Angular velocity is in world axes, so the increment is applied on the left. Composed the
		// other way the body would turn about its own axes instead, which looks plausible in a still
		// frame and wrong in motion.
		Quat increment = ImmPtlQuaternions.rotationByRadians(angular, angularSpeed * seconds);
		rotation = ImmPtlQuaternions.hamiltonProduct(increment, rotation).normalized();

		if (++stepsSinceFix >= STEPS_PER_DRIFT_FIX) {
			rotation = ImmPtlQuaternions.fixFloatingPointErrorAccumulation(rotation);
			stepsSinceFix = 0;
		}
	}

	/** The velocity of a point of the body, given its world-axes offset from the centre of mass. */
	public Vec3 velocityAtPoint(Vec3 offsetFromCentreOfMass) {
		return angularVelocity().cross(offsetFromCentreOfMass).plus(linearVelocity);
	}

	/**
	 * Angular momentum in world axes — the state itself.
	 *
	 * <p>The invariant worth testing against: with no torque applied this must not change at all,
	 * however the body tumbles, while its angular velocity changes constantly whenever it is spinning
	 * about anything but a principal axis.
	 */
	public Vec3 angularMomentum() {
		return angularMomentum;
	}

	private static Vec3 clamped(Vec3 v, double limit) {
		if (!Double.isFinite(limit)) return v;
		double lengthSquared = v.lengthSquared();
		if (lengthSquared <= limit * limit) return v;
		return v.scaled(limit / Math.sqrt(lengthSquared));
	}
}

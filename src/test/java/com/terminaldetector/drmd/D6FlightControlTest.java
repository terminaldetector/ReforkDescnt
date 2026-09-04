package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6FlightControl;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The control bridge: what a pilot wants, turned into forces and torques.
 *
 * <p>Every expected number here was computed independently before the assertion was written. The
 * fixture body is deliberately asymmetric — inertia {@code diag(2, 3, 5)} — because a symmetric one
 * would let a wrong tensor multiplication pass unnoticed.
 */
class D6FlightControlTest {

	private static final double EPS = 1e-9;

	/** Reach 4, so a tip speed of 8 is 2 rad/s and a tip acceleration of 12 is 3 rad/s². */
	private static final double RADIUS = 4;
	private static final double MAX_TIP_SPEED = 8;
	private static final double MAX_TIP_ACCELERATION = 12;

	private static void assertVec(Vec3 expected, Vec3 actual, String what) {
		assertEquals(expected.x(), actual.x(), EPS, what + " x");
		assertEquals(expected.y(), actual.y(), EPS, what + " y");
		assertEquals(expected.z(), actual.z(), EPS, what + " z");
	}

	private static D6PhysicsBody hull() {
		return new D6PhysicsBody().withMass(3).withInertia(D6Mat3.diagonal(2, 3, 5));
	}

	/**
	 * Reads back the torque that was just applied.
	 *
	 * <p>A step of one second adds exactly one second's worth of torque to the momentum, so the
	 * difference is the torque itself. Works on a spinning body too, since nothing else changes the
	 * momentum during a step.
	 */
	private static Vec3 torqueOn(D6PhysicsBody body) {
		Vec3 before = body.angularMomentum();
		body.step(1.0);
		return body.angularMomentum().minus(before);
	}

	@Test
	@DisplayName("steer: torque is the inertia tensor times the wanted angular acceleration")
	void steerIsInertiaTimesAcceleration() {
		D6PhysicsBody b = hull();
		D6FlightControl.steer(b, new Vec3(1, 0, 0), RADIUS, MAX_TIP_SPEED, MAX_TIP_ACCELERATION);

		// Full input, room to spare: alpha = maxTipAcceleration/reach = 3. Torque = Ixx * 3 = 6.
		assertVec(new Vec3(6, 0, 0), torqueOn(b), "torque");
	}

	@Test
	@DisplayName("steer: an untouched axis brakes instead of drifting")
	void steerBrakesUntouchedAxes() {
		D6PhysicsBody b = hull().withAngularVelocity(new Vec3(0.5, 0, 0));
		D6FlightControl.steer(b, new Vec3(0, 0, 0), RADIUS, MAX_TIP_SPEED, MAX_TIP_ACCELERATION);

		// No input, so the term is -clamp(rate) * alpha = -0.5 * 3, times Ixx = 2.
		assertVec(new Vec3(-3, 0, 0), torqueOn(b), "braking torque");
	}

	@Test
	@DisplayName("steer: past the rate cap the brake wins over full input")
	void steerCapsTheTurnRate() {
		// maxRate is 2 rad/s; this body is already at 3.
		D6PhysicsBody b = hull().withAngularVelocity(new Vec3(3, 0, 0));
		D6FlightControl.steer(b, new Vec3(1, 0, 0), RADIUS, MAX_TIP_SPEED, MAX_TIP_ACCELERATION);

		// No headroom, so the input is ignored: -clamp(3) = -1, times alpha 3, times Ixx 2.
		assertVec(new Vec3(-6, 0, 0), torqueOn(b), "torque against the cap");
	}

	@Test
	@DisplayName("steer: input outside -1..1 asks for the limit, not a multiple of it")
	void steerClampsInput() {
		D6PhysicsBody wild = hull();
		D6FlightControl.steer(wild, new Vec3(3, 0, 0), RADIUS, MAX_TIP_SPEED, MAX_TIP_ACCELERATION);

		assertVec(new Vec3(6, 0, 0), torqueOn(wild), "torque at input 3");
	}

	@Test
	@DisplayName("steer: a tiny hull gets a finite turn rate, not one divided by its radius")
	void steerClampsReach() {
		D6PhysicsBody small = hull();
		D6PhysicsBody none = hull();
		D6FlightControl.steer(small, new Vec3(0, 1, 0), 0.1, MAX_TIP_SPEED, MAX_TIP_ACCELERATION);
		D6FlightControl.steer(none, new Vec3(0, 1, 0), 0, MAX_TIP_SPEED, MAX_TIP_ACCELERATION);

		// Both clamp to reach 0.5: alpha = 12/0.5 = 24, torque = Iyy * 24 = 72.
		assertVec(new Vec3(0, 72, 0), torqueOn(small), "torque at radius 0.1");
		assertVec(new Vec3(0, 72, 0), torqueOn(none), "torque at radius 0");
	}

	@Test
	@DisplayName("steer: the tensor that scales the torque is the world one, so rolling changes the feel")
	void steerUsesTheWorldTensor() {
		// A quarter turn about Z carries the body's X axis onto world Y, so the world tensor reads
		// diag(3, 2, 5) — the same shape seen from a different angle.
		D6PhysicsBody rolled = hull()
				.withRotation(ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 0, 1), 90));
		D6FlightControl.steer(rolled, new Vec3(1, 0, 0), RADIUS, MAX_TIP_SPEED, MAX_TIP_ACCELERATION);

		// Same input, same wanted acceleration of 3, but now against 3 rather than 2.
		assertVec(new Vec3(9, 0, 0), torqueOn(rolled), "torque while rolled");
	}

	@Test
	@DisplayName("thrust: mass cancels, so a laden hull accelerates like an empty one")
	void thrustIsMassIndependent() {
		D6PhysicsBody light = new D6PhysicsBody().withMass(2).withInertia(D6Mat3.diagonal(1, 1, 1));
		D6PhysicsBody heavy = new D6PhysicsBody().withMass(100).withInertia(D6Mat3.diagonal(1, 1, 1));

		for (D6PhysicsBody b : new D6PhysicsBody[] { light, heavy }) {
			// Direction is not a unit vector on purpose: it must be normalized, not scaled by.
			D6FlightControl.thrust(b, new Vec3(0, 0, 3), 6);
			b.step(0.5);
		}

		assertVec(new Vec3(0, 0, 3), light.linearVelocity(), "light hull after half a second at 6 m/s^2");
		assertVec(new Vec3(0, 0, 3), heavy.linearVelocity(), "heavy hull, same throttle");
	}

	@Test
	@DisplayName("thrust: no throttle, no force")
	void thrustIgnoresZeroAcceleration() {
		D6PhysicsBody b = hull().withLinearVelocity(new Vec3(1, 0, 0));
		D6FlightControl.thrust(b, new Vec3(0, 1, 0), 0);
		b.step(0.5);

		assertVec(new Vec3(1, 0, 0), b.linearVelocity(), "velocity");
	}

	@Test
	@DisplayName("holdVelocity: gain times step of one lands exactly on the target")
	void holdVelocityLandsOnTarget() {
		D6PhysicsBody b = new D6PhysicsBody().withMass(7).withInertia(D6Mat3.diagonal(1, 1, 1))
				.withLinearVelocity(new Vec3(1, 2, 3));

		// gain 20 and a step of 0.05 multiply to one, so the whole error is removed in one go.
		D6FlightControl.holdVelocity(b, new Vec3(5, -1, 0), 20);
		b.step(0.05);

		assertVec(new Vec3(5, -1, 0), b.linearVelocity(), "velocity");
	}

	@Test
	@DisplayName("holdVelocity: under gravity a plain servo settles below its target, by g/gain")
	void holdVelocityDroopsUnderGravity() {
		D6PhysicsBody b = new D6PhysicsBody().withMass(4).withInertia(D6Mat3.diagonal(1, 1, 1))
				.withLinearVelocity(new Vec3(0, 3, 0));

		for (int i = 0; i < 100; i++) {
			b.applyForce(new Vec3(0, -10 * 4, 0)); // a constant -10 m/s^2
			D6FlightControl.holdVelocity(b, new Vec3(0, 0, 0), 5);
			b.step(0.05);
		}

		// The servo needs an error to make the force that fights gravity: v = g/gain = -10/5.
		assertVec(new Vec3(0, -2, 0), b.linearVelocity(), "settled velocity");
	}

	@Test
	@DisplayName("holdVelocityAgainst: feeding gravity forward removes the droop entirely")
	void holdVelocityAgainstRemovesTheDroop() {
		D6PhysicsBody b = new D6PhysicsBody().withMass(4).withInertia(D6Mat3.diagonal(1, 1, 1))
				.withLinearVelocity(new Vec3(0, 3, 0));

		for (int i = 0; i < 100; i++) {
			b.applyForce(new Vec3(0, -10 * 4, 0));
			D6FlightControl.holdVelocityAgainst(b, new Vec3(0, 0, 0), 5, new Vec3(0, -10, 0));
			b.step(0.05);
		}

		// Same hundred steps as above, and this one reaches the target it was given.
		assertVec(new Vec3(0, 0, 0), b.linearVelocity(), "settled velocity");
	}

	@Test
	@DisplayName("holdVelocityAgainst: on target already, the force exactly cancels the disturbance")
	void holdVelocityAgainstCancelsTheDisturbance() {
		D6PhysicsBody b = new D6PhysicsBody().withMass(4).withInertia(D6Mat3.diagonal(1, 1, 1));

		b.applyForce(new Vec3(0, -10 * 4, 0));
		D6FlightControl.holdVelocityAgainst(b, new Vec3(0, 0, 0), 5, new Vec3(0, -10, 0));
		b.step(0.05);

		// Nothing left over in a single step: the hull hangs still rather than sagging and recovering.
		assertVec(new Vec3(0, 0, 0), b.linearVelocity(), "velocity after one step");
	}

	@Test
	@DisplayName("saturate: straight near zero, curved near the limit, odd about it")
	void saturateShape() {
		assertEquals(0, D6FlightControl.saturate(0, 10), EPS, "at rest");
		// atan(x*s)/s differs from x by (x*s)^2*x/3 near zero: about 8.2e-6 at a tenth of the limit.
		assertEquals(0.1, D6FlightControl.saturate(0.1, 10), 1e-5, "near zero it is the identity");
		assertEquals(-D6FlightControl.saturate(5, 10), D6FlightControl.saturate(-5, 10), EPS, "odd");
		// Well inside the limit long before the input reaches it.
		assertEquals(6.3985228404, D6FlightControl.saturate(10, 10), 1e-9, "at the nominal limit");
	}

	@Test
	@DisplayName("saturate: the curve is bounded, at 0.638*pi/2 of the limit")
	void saturateIsBounded() {
		double huge = D6FlightControl.saturate(1e9, 10);
		// The asymptote is max*0.638*pi/2. Eureka's 0.638 approximates 2/pi = 0.63662, which would
		// put it exactly on the limit; as written it overshoots by 0.22%, and that is the constant
		// this was taken from rather than an accident of ours.
		assertEquals(10 * 0.638 * Math.PI / 2, huge, 1e-6, "asymptote");
		assertTrue(huge < 10.03, "bounded just above the nominal limit, at " + huge);

		assertEquals(0, D6FlightControl.saturate(5, 0), EPS, "no limit means no travel");
	}
}

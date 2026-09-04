package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6MassProperties;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rigid body, exercised as the plan's own physics test list asks: free flight, gravity, force,
 * torque, roll/pitch/yaw, angular momentum.
 *
 * <p>Every expected value was computed independently first. Where a figure looks arbitrary — the
 * −5.5 below, the 1/6 — it is the closed form of the integrator or of a solid cube, and the comment
 * says which.
 */
class D6PhysicsBodyTest {

	private static final double EPS = 1e-9;

	private static void assertVec(Vec3 expected, Vec3 actual, String what) {
		assertEquals(expected.x(), actual.x(), EPS, what + " x");
		assertEquals(expected.y(), actual.y(), EPS, what + " y");
		assertEquals(expected.z(), actual.z(), EPS, what + " z");
	}

	private static D6PhysicsBody body(double mass, D6Mat3 inertia) {
		return new D6PhysicsBody().withMass(mass).withInertia(inertia);
	}

	@Test
	@DisplayName("free flight: nothing pushing, so velocity is kept and position follows it")
	void freeFlight() {
		D6PhysicsBody b = body(3, D6Mat3.diagonal(1, 1, 1)).withLinearVelocity(new Vec3(1, 2, 3));
		for (int i = 0; i < 20; i++) b.step(0.05);

		assertVec(new Vec3(1, 2, 3), b.linearVelocity(), "velocity");
		// 20 steps of 0.05 is exactly one second.
		assertVec(new Vec3(1, 2, 3), b.position(), "position");
	}

	@Test
	@DisplayName("gravity: a constant force gives constant acceleration")
	void gravity() {
		D6PhysicsBody b = body(2, D6Mat3.diagonal(1, 1, 1));
		for (int i = 0; i < 10; i++) {
			b.applyForce(new Vec3(0, -20, 0)); // a = F/m = -10
			b.step(0.1);
		}

		assertVec(new Vec3(0, -10, 0), b.linearVelocity(), "velocity after 1 second at -10 m/s^2");
		// Semi-implicit Euler moves by the NEW velocity each step, so the drop is
		// a*dt^2*(1+2+...+10) = -10 * 0.01 * 55.
		assertVec(new Vec3(0, -5.5, 0), b.position(), "position");
	}

	@Test
	@DisplayName("F = ma, and a heavier body accelerates less for the same push")
	void forceScalesWithMass() {
		D6PhysicsBody light = body(4, D6Mat3.diagonal(1, 1, 1));
		light.applyForce(new Vec3(8, 0, 0));
		light.step(0.5);
		assertVec(new Vec3(1, 0, 0), light.linearVelocity(), "8N on 4kg for half a second");

		D6PhysicsBody heavy = body(8, D6Mat3.diagonal(1, 1, 1));
		heavy.applyForce(new Vec3(8, 0, 0));
		heavy.step(0.5);
		assertVec(new Vec3(0.5, 0, 0), heavy.linearVelocity(), "twice the mass, half the speed");
	}

	@Test
	@DisplayName("torque turns the body, and the inertia tensor decides how fast")
	void torqueGivesAngularVelocity() {
		D6PhysicsBody b = body(1, D6Mat3.diagonal(2, 2, 2));
		for (int i = 0; i < 5; i++) {
			b.applyTorque(new Vec3(0, 4, 0));
			b.step(0.1);
		}

		// L = tau * t = 4 * 0.5 = 2; omega = L / I = 2 / 2 = 1 rad/s.
		assertVec(new Vec3(0, 2, 0), b.angularMomentum(), "angular momentum");
		assertVec(new Vec3(0, 1, 0), b.angularVelocity(), "angular velocity");
	}

	@Test
	@DisplayName("a force off the centre of mass both pushes and turns")
	void offCentreForceDoesBoth() {
		D6PhysicsBody b = body(1, D6Mat3.diagonal(1, 1, 1));
		// Pushed along +X at a point one metre up: the push goes to velocity, the lever to spin.
		b.applyForceAtPoint(new Vec3(1, 0, 0), new Vec3(0, 1, 0));
		b.step(1.0);

		assertVec(new Vec3(1, 0, 0), b.linearVelocity(), "the push");
		// r x F = (0,1,0) x (1,0,0) = (0,0,-1)
		assertVec(new Vec3(0, 0, -1), b.angularMomentum(), "the lever");
	}

	@Test
	@DisplayName("yaw: a quarter turn about +Y takes forward from +Z to +X")
	void quarterTurnAboutY() {
		D6PhysicsBody b = body(1, D6Mat3.diagonal(1, 1, 1))
				.withAngularVelocity(new Vec3(0, Math.PI / 2, 0));
		for (int i = 0; i < 100; i++) b.step(0.01);

		assertVec(new Vec3(1, 0, 0), b.rotation().rotate(new Vec3(0, 0, 1)), "forward after a quarter turn");
	}

	@Test
	@DisplayName("roll, pitch and yaw each turn about their own axis and leave it alone")
	void rollPitchYawAreIndependent() {
		Vec3[] axes = {new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 1, 0)};
		String[] names = {"roll", "pitch", "yaw"};
		for (int i = 0; i < axes.length; i++) {
			D6PhysicsBody b = body(1, D6Mat3.diagonal(1, 1, 1)).withAngularVelocity(axes[i].scaled(1.0));
			for (int step = 0; step < 50; step++) b.step(0.02);
			// A turn about an axis leaves that axis where it was.
			assertVec(axes[i], b.rotation().rotate(axes[i]), names[i] + " moved its own axis");
		}
	}

	@Test
	@DisplayName("with no torque the angular momentum does not move at all, and the velocity does")
	void torqueFreeTumbleConservesMomentumAndPrecesses() {
		// Strongly asymmetric, spun near its intermediate axis — the unstable one, where a body really
		// does tumble. This is the case that separates integrating momentum from integrating velocity.
		D6PhysicsBody b = body(1, D6Mat3.diagonal(1, 4, 9))
				.withAngularVelocity(new Vec3(0.05, 3.0, 0.02));

		Vec3 momentumAtStart = b.angularMomentum();
		Vec3 velocityAtStart = b.angularVelocity();
		double worstVelocityChange = 0;

		for (int i = 0; i < 300; i++) {
			b.step(1.0 / 60.0);
			worstVelocityChange = Math.max(worstVelocityChange,
					b.angularVelocity().minus(velocityAtStart).length());
		}

		// Exactly, not approximately: with no torque the momentum is never written to.
		assertVec(momentumAtStart, b.angularMomentum(), "angular momentum drifted");
		// And the velocity must have moved, or the body is not precessing and the whole point of
		// keeping momentum as the state is lost.
		assertTrue(worstVelocityChange > 1.0,
				"the angular velocity never precessed (worst change " + worstVelocityChange + ")");
	}

	@Test
	@DisplayName("the velocity of a point of the body is the spin plus the drift")
	void velocityAtAPoint() {
		D6PhysicsBody b = body(1, D6Mat3.diagonal(1, 1, 1))
				.withLinearVelocity(new Vec3(0, 0, 5))
				.withAngularVelocity(new Vec3(0, 0, 1));
		// omega x r = (0,0,1) x (1,0,0) = (0,1,0), plus the body's own (0,0,5).
		assertVec(new Vec3(0, 1, 5), b.velocityAtPoint(new Vec3(1, 0, 0)), "point velocity");
	}

	@Test
	@DisplayName("a body made of one block has the inertia of a solid cube")
	void oneBlockIsASolidCube() {
		D6MassProperties props = new D6MassProperties().addBlock(new Vec3(0, 0, 0), 1.0);

		assertEquals(1.0, props.mass(), 1e-12, "mass");
		assertVec(new Vec3(0, 0, 0), props.centreOfMass(), "centre of mass");
		// A solid cube of side 1 and mass m has I = m/6 about each axis through its centre.
		assertEquals(1.0 / 6.0, props.inertia().row0().x(), 1e-12, "Ixx");
		assertEquals(1.0 / 6.0, props.inertia().row1().y(), 1e-12, "Iyy");
		assertEquals(1.0 / 6.0, props.inertia().row2().z(), 1e-12, "Izz");
		assertEquals(0.0, props.inertia().row0().y(), 1e-12, "Ixy should vanish by symmetry");
	}

	@Test
	@DisplayName("the rotation stays a rotation over thousands of steps")
	void rotationDoesNotDrift() {
		D6PhysicsBody b = body(1, D6Mat3.diagonal(1, 2, 3))
				.withAngularVelocity(new Vec3(0.7, -1.3, 2.1));
		for (int i = 0; i < 5000; i++) b.step(1.0 / 120.0);

		Quat r = b.rotation();
		double lengthSquared = r.x() * r.x() + r.y() * r.y() + r.z() * r.z() + r.w() * r.w();
		assertEquals(1.0, lengthSquared, 1e-9, "the rotation is no longer a unit quaternion");
	}
}

package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagdollAngularLimitTest {
	private static final Vec3 ZERO = new Vec3(0, 0, 0);
	private static final Vec3 X = new Vec3(1, 0, 0);
	private static final Vec3 Y = new Vec3(0, 1, 0);
	private static final Vec3 Z = new Vec3(0, 0, 1);
	private static final Quat ROOT = turn(new Vec3(1, 2, -3), 1.2);
	private static final RagdollRig.AngularLimits LIMITS = limits(.6, -.15, .25);

	@Test void explicitLocalFramesAndQuaternionSignDefineTheSameRestAndDeflection() {
		Quat frameA = turn(Y, .9), frameB = turn(Z, -.7);
		var limits = new RagdollRig.AngularLimits(frameA, frameB, .7, -.3, .3);
		Quat restB = mul(mul(ROOT, frameA), frameB.inverse());
		var rest = RagdollAngularLimit.angles(ROOT, restB, limits);
		assertEquals(0, rest.swing(), 3e-8);
		assertEquals(0, rest.twist(), 1e-12);
		Quat deflected = mul(mul(mul(ROOT, frameA), mul(turn(Y, .4), turn(X, -.2))), frameB.inverse());
		var measured = RagdollAngularLimit.angles(ROOT, deflected, limits);
		assertEquals(.4, measured.swing(), 1e-12);
		assertEquals(-.2, measured.twist(), 1e-12);
		var otherSign = RagdollAngularLimit.angles(negate(ROOT), negate(deflected), limits);
		assertEquals(measured.swing(), otherSign.swing(), 1e-12);
		assertEquals(measured.twist(), otherSign.twist(), 1e-12);
	}

	@Test void legacyJointsRemainUnlimitedAndMalformedAngularContractsAreRejected() {
		assertNull(new RagdollRig.Joint(0, 1, ZERO, ZERO, 0).angularLimits());
		assertThrows(IllegalArgumentException.class, () -> limits(Math.PI, 0, .1));
		assertThrows(IllegalArgumentException.class, () -> limits(.5, .3, .2));
		assertThrows(IllegalArgumentException.class, () -> limits(.5, -Math.PI - .01, .2));
		assertThrows(IllegalArgumentException.class, () -> limits(.5, -.1, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> new RagdollRig.AngularLimits(
				new Quat(0, 0, 0, 0), Quat.IDENTITY, .5, -.1, .1));
		assertThrows(IllegalArgumentException.class, () -> new RagdollRig.AngularLimits(
				new Quat(Double.NaN, 0, 0, 1), Quat.IDENTITY, .5, -.1, .1));
	}

	@Test void actualJointIterationsProjectBothBoundsWithoutInventingVelocity() {
		for (double twist : new double[]{-.9, .9}) {
			var simulation = centredPair(LIMITS);
			simulation.parts().get(1).body().withRotation(mul(ROOT, mul(turn(Y, 1.6), turn(X, twist))));
			simulation.step(.001, ZERO, 1, 64);
			assertWithin(simulation, LIMITS, 1e-6);
			for (var part : simulation.parts()) {
				assertEquals(0, part.body().linearVelocity().length(), 0);
				assertEquals(0, part.body().angularMomentum().length(), 0);
			}
		}
	}

	@Test void aSingleProjectionIsCappedAndWeightsEachBodyByItsInertia() {
		var a = body(ROOT, D6Mat3.diagonal(10, 10, 10), ZERO);
		var b = body(mul(ROOT, turn(Y, 2)), D6Mat3.IDENTITY, ZERO);
		Quat beforeA = a.rotation(), beforeB = b.rotation();
		RagdollAngularLimit.solve(a, b, limits(.2, -Math.PI, Math.PI));
		double movementA = rotationDistance(beforeA, a.rotation());
		double movementB = rotationDistance(beforeB, b.rotation());
		assertEquals(.08, movementA + movementB, 1e-10);
		assertEquals(10, movementB / movementA, 1e-8);
		assertTrue(RagdollAngularLimit.angles(a.rotation(), b.rotation(), LIMITS).swing() > .2,
				"a single iteration must not snap an over-rotated limb to its boundary");
	}

	@Test void unilateralVelocityStopConservesPairMomentumAndRemovesEnergy() {
		var a = body(ROOT, D6Mat3.diagonal(.4, 1.5, 3), ROOT.rotate(new Vec3(.2, .4, -.3)));
		var b = body(mul(ROOT, turn(X, .25)), D6Mat3.diagonal(.7, 2, .3),
				ROOT.rotate(new Vec3(6.2, .4, -.3)));
		Vec3 momentum = a.angularMomentum().plus(b.angularMomentum());
		double energy = energy(a) + energy(b);
		RagdollAngularLimit.solve(a, b, LIMITS);
		assertTrue(a.angularMomentum().plus(b.angularMomentum()).minus(momentum).length() < 1e-12);
		assertEquals(0, b.angularVelocity().minus(a.angularVelocity()).dot(ROOT.rotate(X)), 1e-12);
		assertTrue(energy(a) + energy(b) < energy - 1);
	}

	@Test void inwardAndInteriorVelocitiesAreNotBraked() {
		for (double twist : new double[]{0, .25}) {
			var a = body(ROOT, D6Mat3.IDENTITY, ZERO);
			var b = body(mul(ROOT, turn(X, twist)), D6Mat3.IDENTITY, ROOT.rotate(X.scaled(-3)));
			Vec3 beforeA = a.angularMomentum(), beforeB = b.angularMomentum();
			RagdollAngularLimit.solve(a, b, LIMITS);
			assertEquals(beforeA, a.angularMomentum());
			assertEquals(beforeB, b.angularMomentum());
		}
	}

	@Test void anisotropicProjectionWithExistingSpinNeverAddsAngularKineticEnergy() {
		var a = body(ROOT, D6Mat3.diagonal(.3, 1.7, 3.2), ROOT.rotate(new Vec3(2, -.7, .8)));
		var b = body(mul(ROOT, mul(turn(Y, 1.3), turn(X, .8))),
				D6Mat3.diagonal(.12, 2.8, .45), ROOT.rotate(new Vec3(-.4, 1.7, -.6)));
		assertTrue(energy(a) + energy(b) > 1);
		for (int iteration = 0; iteration < 100; iteration++) {
			double before = energy(a) + energy(b);
			RagdollAngularLimit.solve(a, b, LIMITS);
			assertTrue(energy(a) + energy(b) <= before + 1e-11, "stabilization injected angular energy");
		}
		var angles = RagdollAngularLimit.angles(a.rotation(), b.rotation(), LIMITS);
		assertTrue(angles.swing() <= LIMITS.maxSwing() + 1e-6);
		assertTrue(angles.twist() <= LIMITS.maxTwist() + 1e-6);
		assertTrue(angles.twist() >= LIMITS.minTwist() - 1e-6);
	}

	@Test void antiparallelSwingAndPiTwistRemainFiniteAndIgnoreQuaternionSign() {
		for (Vec3 axis : List.of(X, Y)) {
			var a = body(ROOT, D6Mat3.IDENTITY, ZERO);
			var b = body(mul(ROOT, turn(axis, Math.PI)), D6Mat3.IDENTITY, ZERO);
			var signedA = body(negate(a.rotation()), D6Mat3.IDENTITY, ZERO);
			var signedB = body(negate(b.rotation()), D6Mat3.IDENTITY, ZERO);
			for (int iteration = 0; iteration < 80; iteration++) {
				RagdollAngularLimit.solve(a, b, LIMITS);
				RagdollAngularLimit.solve(signedA, signedB, LIMITS);
				assertTrue(Double.isFinite(energy(a) + energy(b)));
				assertEquals(0, rotationDistance(a.rotation(), signedA.rotation()), 5e-8);
				assertEquals(0, rotationDistance(b.rotation(), signedB.rotation()), 5e-8);
			}
			var angles = RagdollAngularLimit.angles(a.rotation(), b.rotation(), LIMITS);
			assertTrue(angles.swing() <= .600001);
			assertTrue(angles.twist() >= -.150001 && angles.twist() <= .250001);
		}
	}

	@Test void asymmetricTwistLimitsChooseTheShortArcAcrossThePiSeam() {
		var limits = limits(.6, -2.9, .2);
		var a = body(ROOT, D6Mat3.IDENTITY, ZERO);
		var b = body(mul(ROOT, turn(X, 3)), D6Mat3.IDENTITY, ZERO);
		RagdollAngularLimit.solve(a, b, limits);
		assertEquals(3.08, RagdollAngularLimit.angles(a.rotation(), b.rotation(), limits).twist(), 1e-10);
		for(int iteration=0;iteration<20;iteration++)RagdollAngularLimit.solve(a,b,limits);
		assertEquals(-2.9, RagdollAngularLimit.angles(a.rotation(), b.rotation(), limits).twist(), 1e-7);

		var seamLimits=limits(.6,-Math.PI,.2);
		a=body(ROOT,D6Mat3.IDENTITY,ZERO);
		b=body(mul(ROOT,turn(X,Math.PI)),D6Mat3.IDENTITY,ROOT.rotate(X.scaled(-3)));
		assertEquals(-Math.PI,RagdollAngularLimit.angles(a.rotation(),b.rotation(),seamLimits).twist(),1e-12);
		RagdollAngularLimit.solve(a,b,seamLimits);
		assertEquals(0,b.angularVelocity().minus(a.angularVelocity()).dot(ROOT.rotate(X)),1e-12);
	}

	@Test void offCentreImpulseUnderRotatedGravityKeepsAnchorsAndAngularBounds() {
		var simulation = linkedPair(new Vec3(0, 4, 0));
		Vec3 impulse = ROOT.rotate(new Vec3(0, 0, 3));
		Vec3 gravity = ROOT.rotate(new Vec3(0, -9.81, 0));
		simulation.applyImpulse(1, impulse, new Vec3(.35, .05, 0));
		for (int step = 0; step < 200; step++) {
			simulation.step(.01, gravity);
			assertWithin(simulation, LIMITS, .003);
			assertTrue(simulation.jointError(0) < .02);
			assertFinite(simulation);
		}
		assertTrue(simulation.centreVelocity().minus(gravity.scaled(2).plus(impulse.scaled(.2))).length() < 1e-9,
				"angular limits must not erase or duplicate the existing force/linear impulse");
	}

	@Test void limitedLimbContactsAreResolvedAfterEveryAngularAndAnchorProjection() {
		Vec3 normal = ROOT.rotate(X);
		var simulation = linkedPair(normal.scaled(3));
		simulation.applyImpulse(1, ROOT.rotate(new Vec3(0, 0, 2)), new Vec3(.35, .05, 0));
		int[] contacts = {0};
		for (int step = 0; step < 240; step++) {
			simulation.step(.01, normal.scaled(-9.81), 4, 8, (part, fallback) -> {
				double radius = projectionRadius(part, normal);
				double penetration = radius - part.body().position().dot(normal);
				if (penetration <= 0) return;
				contacts[0]++;
				part.body().withPosition(part.body().position().plus(normal.scaled(penetration + 1e-5)));
				part.body().contactImpulse(normal.scaled(-radius), normal, .72);
			});
			for (var part : simulation.parts())
				assertTrue(part.body().position().dot(normal) - projectionRadius(part, normal) >= -1e-8,
						"angular/anchor correction carried a contacted box through the plane");
			assertFinite(simulation);
		}
		assertTrue(contacts[0] > 100, "negative must actually reach and remain in contact with the wall");
		assertWithin(simulation, LIMITS, .02);
		assertTrue(simulation.jointError(0) < .04);
	}

	private static RagdollSimulation centredPair(RagdollRig.AngularLimits limits) {
		return new RagdollSimulation(new RagdollRig(List.of(
				new RagdollRig.Segment("a", 4, new Vec3(.5, .12, .18), ZERO),
				new RagdollRig.Segment("b", 1, new Vec3(.5, .12, .18), ZERO)),
				List.of(new RagdollRig.Joint(0, 1, ZERO, ZERO, 0, limits))), ZERO, ROOT, ZERO);
	}

	private static RagdollSimulation linkedPair(Vec3 origin) {
		return new RagdollSimulation(new RagdollRig(List.of(
				new RagdollRig.Segment("a", 4, new Vec3(.5, .12, .18), ZERO),
				new RagdollRig.Segment("b", 1, new Vec3(.5, .12, .18), X)),
				List.of(new RagdollRig.Joint(0, 1, X.scaled(.5), X.scaled(-.5), 0, LIMITS))), origin, ROOT, ZERO);
	}

	private static void assertWithin(RagdollSimulation simulation, RagdollRig.AngularLimits limits, double tolerance) {
		var angles = RagdollAngularLimit.angles(simulation.parts().get(0).body().rotation(),
				simulation.parts().get(1).body().rotation(), limits);
		assertTrue(angles.swing() <= limits.maxSwing() + tolerance, "swing " + angles.swing());
		assertTrue(angles.twist() >= limits.minTwist() - tolerance, "lower twist " + angles.twist());
		assertTrue(angles.twist() <= limits.maxTwist() + tolerance, "upper twist " + angles.twist());
	}

	private static void assertFinite(RagdollSimulation simulation) {
		for (var part : simulation.parts()) {
			assertTrue(Double.isFinite(part.body().position().lengthSquared()));
			assertTrue(Double.isFinite(energy(part.body())));
			double qNorm = Math.pow(part.body().rotation().x(), 2) + Math.pow(part.body().rotation().y(), 2)
					+ Math.pow(part.body().rotation().z(), 2) + Math.pow(part.body().rotation().w(), 2);
			assertEquals(1, qNorm, 1e-10);
		}
	}

	private static double projectionRadius(RagdollSimulation.Part part, Vec3 normal) {
		Vec3 half = part.segment().halfExtents();
		Quat rotation = part.body().rotation();
		return Math.abs(rotation.rotate(X.scaled(half.x())).dot(normal))
				+ Math.abs(rotation.rotate(Y.scaled(half.y())).dot(normal))
				+ Math.abs(rotation.rotate(Z.scaled(half.z())).dot(normal));
	}

	private static RagdollRig.AngularLimits limits(double swing, double lower, double upper) {
		return new RagdollRig.AngularLimits(Quat.IDENTITY, Quat.IDENTITY, swing, lower, upper);
	}
	private static D6PhysicsBody body(Quat rotation, D6Mat3 inertia, Vec3 angularVelocity) {
		return new D6PhysicsBody().withMass(1).withInertia(inertia).withRotation(rotation).withAngularVelocity(angularVelocity);
	}
	private static double energy(D6PhysicsBody body) { return .5 * body.angularMomentum().dot(body.angularVelocity()); }
	private static Quat turn(Vec3 axis, double angle) { return ImmPtlQuaternions.rotationByRadians(axis, angle); }
	private static Quat mul(Quat a, Quat b) { return ImmPtlQuaternions.hamiltonProduct(a, b); }
	private static Quat negate(Quat q) { return new Quat(-q.x(), -q.y(), -q.z(), -q.w()); }
	private static double rotationDistance(Quat a, Quat b) {
		return 2 * Math.acos(Math.min(1, Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w())));
	}
}

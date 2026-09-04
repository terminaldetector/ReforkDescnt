package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.d6.D6PhysicsBody;
import com.terminaldetector.drmd.d6.D6PortalTransform;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrying a whole body through a portal.
 *
 * <p>The property under test is that nothing is left behind describing the world the body just left.
 * A transform that moves the position and forgets the orientation produces a ship that arrives facing
 * the wrong way, which renders perfectly and is wrong — the class of bug this whole file exists to
 * make impossible.
 *
 * <p>One expectation is worth stating because it reads as a mistake the first time: a point standing
 * a metre <b>in front of</b> the source arrives a metre <b>behind</b> the destination. A portal's
 * normal points at the side you stand on, so you enter against it and leave along the far one.
 */
class D6PortalTransformTest {

	private static final double EPS = 1e-9;

	/** Source at the origin facing +Z; destination ten east, facing +X. */
	private static final D6PortalTransform TURN = D6PortalTransform.of(
			new Vec3(0, 0, 0), new Vec3(0, 0, 1),
			new Vec3(10, 0, 0), new Vec3(1, 0, 0));

	/** Two portals facing each other down the Z axis — the rotation should be nothing at all. */
	private static final D6PortalTransform FACING = D6PortalTransform.of(
			new Vec3(0, 0, 0), new Vec3(0, 0, 1),
			new Vec3(0, 0, 20), new Vec3(0, 0, -1));

	private static void assertVec(Vec3 expected, Vec3 actual, String what) {
		assertEquals(expected.x(), actual.x(), EPS, what + " x");
		assertEquals(expected.y(), actual.y(), EPS, what + " y");
		assertEquals(expected.z(), actual.z(), EPS, what + " z");
	}

	@Test
	@DisplayName("walking in against one normal comes out along the other")
	void entryDirectionBecomesExitDirection() {
		// Entering the source means moving along -sourceNormal; leaving means moving along +destNormal.
		assertVec(new Vec3(1, 0, 0), TURN.rotation().rotate(new Vec3(0, 0, -1)), "entry direction");
		// And the facing pair changes nothing, because there is nothing to change.
		assertVec(new Vec3(0, 0, -1), FACING.rotation().rotate(new Vec3(0, 0, -1)), "facing pair");
	}

	@Test
	@DisplayName("in front of the source is behind the destination")
	void positionLandsBehindTheFarFace() {
		// Two along +sourceNormal lands two along -destNormal from the destination: (10,0,0) less
		// two of the destination's own normal (1,0,0).
		assertVec(new Vec3(8, 0, 0), TURN.transformPoint(new Vec3(0, 0, 2)), "two in front");
		// The same rule, and the sign is the whole test: the destination faces -Z, so behind it is
		// +Z and the point lands at 22 rather than at 18. Which is what the see-through view draws,
		// so what you see through the source is what is behind the destination.
		assertVec(new Vec3(0, 0, 22), FACING.transformPoint(new Vec3(0, 0, 2)), "facing pair");
	}

	@Test
	@DisplayName("velocity turns with the portal and scales with it")
	void velocityIsCarried() {
		assertVec(new Vec3(5, 0, 0), TURN.transformVelocity(new Vec3(0, 0, -5)), "flying in at 5");

		D6PortalTransform doubled = new D6PortalTransform(
				new Vec3(0, 0, 0), new Vec3(0, 0, 1), new Vec3(10, 0, 0), new Vec3(1, 0, 0), 2.0);
		assertVec(new Vec3(10, 0, 0), doubled.transformVelocity(new Vec3(0, 0, -5)), "a portal twice the size");
	}

	@Test
	@DisplayName("orientation composes on the left, so the body turns with the world")
	void orientationIsCarried() {
		// A body whose own forward is +X in world terms.
		Quat body = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 1, 0), 90);
		Vec3 forwardBefore = body.rotate(new Vec3(0, 0, 1));

		Quat after = TURN.transformOrientation(body);
		// Whatever it was pointing at, the portal's rotation applies to that too.
		assertVec(TURN.rotation().rotate(forwardBefore), after.rotate(new Vec3(0, 0, 1)),
				"the body's forward did not follow the portal");
	}

	@Test
	@DisplayName("angular momentum is rotated, so a tumbling body keeps tumbling the same way")
	void angularMomentumIsCarried() {
		Vec3 spin = new Vec3(0, 3, 0);
		assertVec(TURN.rotation().rotate(spin), TURN.transformAngularMomentum(spin), "spin");
		// The facing pair leaves it alone entirely.
		assertVec(spin, FACING.transformAngularMomentum(spin), "facing pair");
	}

	@Test
	@DisplayName("the crossing point is where it went through, not where the tick ended")
	void crossingPoint() {
		// A body that moved from one side to the other during a tick.
		Vec3 crossed = TURN.crossingPoint(new Vec3(0, 0, 1), new Vec3(0, 0, -1));
		assertNotNull(crossed);
		assertVec(new Vec3(0, 0, 0), crossed, "crossing point");

		// A body that did not reach the face crossed nothing.
		assertNull(TURN.crossingPoint(new Vec3(0, 0, 5), new Vec3(0, 0, 2)));
	}

	@Test
	@DisplayName("leaving through the front is not entering")
	void crossingIsDirectional() {
		assertTrue(TURN.crossedInward(new Vec3(0, 0, 1), new Vec3(0, 0, -1)), "front to back is entering");
		assertFalse(TURN.crossedInward(new Vec3(0, 0, -1), new Vec3(0, 0, 1)), "back to front is not");
		assertFalse(TURN.crossedInward(new Vec3(0, 0, 5), new Vec3(0, 0, 2)), "never reached the face");
	}

	@Test
	@DisplayName("one call carries the whole body, so none of the four can be forgotten")
	void applyCarriesEverything() {
		D6PhysicsBody body = new D6PhysicsBody()
				.withMass(2)
				.withInertia(D6Mat3.diagonal(1, 1, 1))
				.withPosition(new Vec3(0, 0, 1))
				.withLinearVelocity(new Vec3(0, 0, -5))
				.withAngularMomentum(new Vec3(0, 3, 0));
		Quat orientationBefore = body.rotation();

		Vec3 crossed = TURN.crossingPoint(new Vec3(0, 0, 1), new Vec3(0, 0, -1));
		TURN.apply(body, crossed);

		assertVec(new Vec3(10, 0, 0), body.position(), "placed at the far face, not at the tick boundary");
		assertVec(new Vec3(5, 0, 0), body.linearVelocity(), "velocity");
		assertVec(TURN.rotation().rotate(new Vec3(0, 3, 0)), body.angularMomentum(), "angular momentum");
		assertVec(TURN.transformOrientation(orientationBefore).rotate(new Vec3(0, 0, 1)),
				body.rotation().rotate(new Vec3(0, 0, 1)), "orientation");
	}

	@Test
	@DisplayName("the far side of the pair undoes this side, for any pair of normals")
	void inverseUndoesIt() {
		assertVec(new Vec3(0.3, -1.2, 2.5),
				TURN.inverse().transformPoint(TURN.transformPoint(new Vec3(0.3, -1.2, 2.5))),
				"the fixture pair");

		// Not only for axis-aligned faces: the shortest arc from source to minus-destination and the
		// one from destination to minus-source share an axis and an angle, so they really are inverses.
		Random random = new Random(5);
		for (int i = 0; i < 500; i++) {
			D6PortalTransform t = D6PortalTransform.of(
					randomVec(random, 9), randomVec(random, 1).normalized(),
					randomVec(random, 9), randomVec(random, 1).normalized());
			Vec3 p = randomVec(random, 5);
			assertVec(p, t.inverse().transformPoint(t.transformPoint(p)), "random pair " + i);
		}
	}

	private static Vec3 randomVec(Random random, double spread) {
		return new Vec3(random.nextGaussian() * spread, random.nextGaussian() * spread,
				random.nextGaussian() * spread);
	}
}

package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.world.portal.PortalCrossing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link PortalCrossing} directly — pure geometry, the {@code SkirtGeometryTest} idiom. */
class PortalCrossingTest {
	private static final Vec3 PLANE = new Vec3(0, 0, 0);
	private static final Vec3 NORMAL = new Vec3(0, 0, 1); // portal faces +Z

	private static void assertClose(Vec3 expected, Vec3 actual, String what) {
		assertTrue(Math.abs(expected.x() - actual.x()) < 1e-9
						&& Math.abs(expected.y() - actual.y()) < 1e-9
						&& Math.abs(expected.z() - actual.z()) < 1e-9,
				what + ": expected " + expected + " but was " + actual);
	}

	@Test
	@DisplayName("walking into the front face counts as a crossing")
	void frontToBackCrosses() {
		assertTrue(PortalCrossing.crossedInward(new Vec3(0, 0, 1), new Vec3(0, 0, -1), PLANE, NORMAL));
	}

	@Test
	@DisplayName("walking into the back face does not — a portal is entered from the front")
	void backToFrontDoesNotCross() {
		assertFalse(PortalCrossing.crossedInward(new Vec3(0, 0, -1), new Vec3(0, 0, 1), PLANE, NORMAL),
				"entering from behind must not teleport");
	}

	@Test
	@DisplayName("staying on one side never counts, however far it moves")
	void sameSideNeverCrosses() {
		assertFalse(PortalCrossing.crossedInward(new Vec3(0, 0, 5), new Vec3(0, 0, 1), PLANE, NORMAL));
		assertFalse(PortalCrossing.crossedInward(new Vec3(0, 0, -1), new Vec3(0, 0, -5), PLANE, NORMAL));
	}

	@Test
	@DisplayName("landing exactly on the plane counts as through, so a step cannot stall inside it")
	void landingOnThePlaneCounts() {
		assertTrue(PortalCrossing.crossedInward(new Vec3(0, 0, 1), new Vec3(0, 0, 0), PLANE, NORMAL));
	}

	@Test
	@DisplayName("a step far longer than the portal is thick still registers — no tunnelling")
	void fastMoverDoesNotTunnel() {
		// A ship at DRMD speeds covers many blocks in a tick; an inside-the-block test would miss this.
		assertTrue(PortalCrossing.crossedInward(new Vec3(0, 0, 40), new Vec3(0, 0, -40), PLANE, NORMAL));
	}

	@Test
	@DisplayName("the crossing fraction locates where the plane was met")
	void crossingFractionIsWhereThePlaneIsMet() {
		// From z=+3 to z=-1 is 4 long and meets z=0 three quarters of the way.
		double t = PortalCrossing.crossingFraction(new Vec3(0, 0, 3), new Vec3(0, 0, -1), PLANE, NORMAL);
		assertEquals(0.75, t, 1e-9);

		Vec3 point = PortalCrossing.crossingPoint(new Vec3(0, 0, 3), new Vec3(0, 0, -1), PLANE, NORMAL);
		assertClose(new Vec3(0, 0, 0), point, "crossing point");
	}

	@Test
	@DisplayName("the crossing point keeps the sideways offset, so the caller can reject a miss")
	void crossingPointKeepsOffset() {
		// Passes through the plane 10 blocks to the side — through the wall, not the portal.
		Vec3 point = PortalCrossing.crossingPoint(new Vec3(10, 2, 1), new Vec3(10, 2, -1), PLANE, NORMAL);
		assertClose(new Vec3(10, 2, 0), point, "off-centre crossing point");
	}

	@Test
	@DisplayName("a step that never reaches the plane reports no crossing")
	void noCrossingReportsNothing() {
		assertEquals(-1.0, PortalCrossing.crossingFraction(new Vec3(0, 0, 5), new Vec3(0, 0, 2), PLANE, NORMAL));
		assertNull(PortalCrossing.crossingPoint(new Vec3(0, 0, 5), new Vec3(0, 0, 2), PLANE, NORMAL));
		// Travelling parallel to the plane is the degenerate case: no sign change to divide by.
		assertEquals(-1.0, PortalCrossing.crossingFraction(new Vec3(0, 0, 1), new Vec3(5, 5, 1), PLANE, NORMAL));
	}

	@Test
	@DisplayName("through a facing pair, the traveller keeps going the same way and lands clear of the exit")
	void facingPairKeepsHeading() {
		// Portal A at the origin facing +Z; partner B at z=-100 facing -Z, i.e. the two face each other.
		Vec3 srcPos = new Vec3(0, 0, 0);
		Vec3 srcNormal = new Vec3(0, 0, 1);
		Vec3 dstPos = new Vec3(0, 0, -100);
		Vec3 dstNormal = new Vec3(0, 0, -1);

		Vec3 entry = new Vec3(0, 0, 0);
		Vec3 velocity = new Vec3(0, 0, -1); // moving into A's face

		PortalCrossing.Exit exit = PortalCrossing.exitFor(entry, velocity, srcPos, srcNormal, dstPos, dstNormal);

		// Still travelling the same way through the world — a straight tunnel, not a reversal.
		assertClose(new Vec3(0, 0, -1), exit.velocity(), "velocity through a facing pair");
		// And placed clear of B, on the side its normal points to, so the next tick reads unambiguously.
		assertEquals(-100 - PortalCrossing.EXIT_CLEARANCE, exit.position().z(), 1e-9);
	}

	@Test
	@DisplayName("the exit is always in front of the destination, never inside its plane")
	void exitIsClearOfTheDestinationPlane() {
		Vec3[][] pairs = {
				{new Vec3(0, 0, 1), new Vec3(0, 0, -1)},
				{new Vec3(1, 0, 0), new Vec3(0, 0, 1)},
				{new Vec3(0, 1, 0), new Vec3(1, 0, 0)},
				{new Vec3(0, 0, 1), new Vec3(0, 0, 1)},
		};
		for (Vec3[] pair : pairs) {
			Vec3 srcNormal = pair[0];
			Vec3 dstNormal = pair[1];
			PortalCrossing.Exit exit = PortalCrossing.exitFor(
					new Vec3(0, 0, 0), new Vec3(0, 0, -1),
					new Vec3(0, 0, 0), srcNormal,
					new Vec3(50, 60, 70), dstNormal);

			double side = exit.position().minus(new Vec3(50, 60, 70)).dot(dstNormal.normalized());
			assertEquals(PortalCrossing.EXIT_CLEARANCE, side, 1e-9,
					"exit not clear of the destination plane for " + srcNormal + " -> " + dstNormal);
		}
	}

	@Test
	@DisplayName("speed is preserved through the portal — a turn, not a push")
	void speedIsPreserved() {
		Vec3 velocity = new Vec3(0.3, -1.2, 0.75);
		PortalCrossing.Exit exit = PortalCrossing.exitFor(
				new Vec3(0, 0, 0), velocity,
				new Vec3(0, 0, 0), new Vec3(0, 0, 1),
				new Vec3(10, 20, 30), new Vec3(1, 0, 0));

		assertEquals(velocity.length(), exit.velocity().length(), 1e-9,
				"the transform must rotate the velocity, not change its magnitude");
	}

	@Test
	@DisplayName("a crossing on the face counts, one past its edge does not")
	void withinFaceBoundsTheFace() {
		// A one-block mirror: its own corner is at 0.5, so 0.7 is inside the forgiving span and 0.8 out.
		assertTrue(PortalCrossing.withinFace(new Vec3(0.7, 0.7, 0), PLANE, NORMAL, 0.75));
		assertFalse(PortalCrossing.withinFace(new Vec3(0.8, 0, 0), PLANE, NORMAL, 0.75));
		// Exactly on the edge counts, so the span reads as a closed rectangle.
		assertTrue(PortalCrossing.withinFace(new Vec3(0.75, 0, 0), PLANE, NORMAL, 0.75));
	}

	@Test
	@DisplayName("a wide portal keeps its corners — the face is a rectangle, not a circle")
	void withinFaceKeepsTheCornersOfAWidePortal() {
		// A 4-wide portal panel. The corner is 2.69 from the centre, so a radius test of 2.0 would
		// refuse a traveller aiming at a visibly open part of the portal.
		assertTrue(PortalCrossing.withinFace(new Vec3(1.9, 1.9, 0), PLANE, NORMAL, 2.0),
				"the corner of a 4-wide panel must be inside its own face");
		assertFalse(PortalCrossing.withinFace(new Vec3(2.1, 0, 0), PLANE, NORMAL, 2.0));
	}

	@Test
	@DisplayName("distance along the normal is not the face test's business")
	void withinFaceIgnoresDepth() {
		// The caller passes a point already known to be on the plane; the normal component is projected
		// out so a stray depth cannot make a centred hit read as a miss.
		assertTrue(PortalCrossing.withinFace(new Vec3(0, 0, 7), PLANE, NORMAL, 0.75));
	}

	@Test
	@DisplayName("the face test works off any axis and does not need a unit normal")
	void withinFaceHandlesOtherAxesAndScales() {
		assertTrue(PortalCrossing.withinFace(new Vec3(5, 0.7, -0.74), PLANE, new Vec3(1, 0, 0), 0.75));
		assertFalse(PortalCrossing.withinFace(new Vec3(5, 0.7, -0.9), PLANE, new Vec3(1, 0, 0), 0.75));
		assertTrue(PortalCrossing.withinFace(new Vec3(0.7, 0.7, 0), PLANE, new Vec3(0, 0, 5), 0.75),
				"an unnormalised normal must measure the same as a unit one");
	}

	@Test
	@DisplayName("crossing and exit are deterministic")
	void isDeterministic() {
		var a = PortalCrossing.exitFor(new Vec3(1, 2, 3), new Vec3(0, 0, -1),
				new Vec3(0, 0, 0), new Vec3(0, 0, 1), new Vec3(9, 9, 9), new Vec3(1, 0, 0));
		var b = PortalCrossing.exitFor(new Vec3(1, 2, 3), new Vec3(0, 0, -1),
				new Vec3(0, 0, 0), new Vec3(0, 0, 1), new Vec3(9, 9, 9), new Vec3(1, 0, 0));
		assertEquals(a.position(), b.position());
		assertEquals(a.velocity(), b.velocity());
	}
}

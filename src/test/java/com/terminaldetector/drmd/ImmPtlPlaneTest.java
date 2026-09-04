package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlPlane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plane vendored from Immersive Portals.
 *
 * <p>The sign convention is the whole risk here. Every one of these operations is a subtraction away
 * from being backwards, and backwards is the difference between a portal that shows the room and one
 * that shows the wall — a failure that renders perfectly and is wrong. So the fixtures are ones whose
 * answers can be read off by eye, and every expected value was computed before it was written down.
 */
class ImmPtlPlaneTest {

	private static final double EPS = 1e-12;

	/** The horizontal plane at y = 5, facing up. */
	private static final ImmPtlPlane FLOOR = new ImmPtlPlane(new Vec3(0, 5, 0), new Vec3(0, 1, 0));

	private static void assertVec(Vec3 expected, Vec3 actual, String what) {
		assertEquals(expected.x(), actual.x(), EPS, what + " x");
		assertEquals(expected.y(), actual.y(), EPS, what + " y");
		assertEquals(expected.z(), actual.z(), EPS, what + " z");
	}

	@Test
	@DisplayName("distance is signed, and positive on the side the normal points at")
	void distanceIsSigned() {
		assertEquals(3.0, FLOOR.distanceTo(new Vec3(0, 8, 0)), EPS, "above the plane");
		assertEquals(-4.0, FLOOR.distanceTo(new Vec3(0, 1, 0)), EPS, "below the plane");
		assertEquals(0.0, FLOOR.distanceTo(new Vec3(7, 5, -2)), EPS, "on the plane");

		assertTrue(FLOOR.isInFront(new Vec3(0, 8, 0)));
		assertFalse(FLOOR.isInFront(new Vec3(0, 1, 0)));
		assertFalse(FLOOR.isInFront(new Vec3(0, 5, 0)), "a point on the plane is not in front of it");
	}

	@Test
	@DisplayName("the normal is normalized, so a long one does not scale every distance")
	void normalIsNormalized() {
		ImmPtlPlane scaled = new ImmPtlPlane(new Vec3(0, 5, 0), new Vec3(0, 100, 0));
		assertEquals(3.0, scaled.distanceTo(new Vec3(0, 8, 0)), EPS,
				"the caller's normal length leaked into the distance");
	}

	@Test
	@DisplayName("projection drops onto the plane, reflection goes through to the other side")
	void projectAndReflect() {
		Vec3 p = new Vec3(2, 8, 3);
		assertVec(new Vec3(2, 5, 3), FLOOR.project(p), "projection");
		assertVec(new Vec3(2, 2, 3), FLOOR.reflect(p), "reflection");
		// Reflecting twice is the identity, which is the property that catches a factor of two.
		assertVec(p, FLOOR.reflect(FLOOR.reflect(p)), "double reflection");
	}

	@Test
	@DisplayName("a ray reports how far along itself the plane lies, in units of its own direction")
	void rayTraceIsInUnitsOfTheDirection() {
		Vec3 origin = new Vec3(0, 0, 0);
		assertEquals(5.0, FLOOR.rayTraceT(origin, new Vec3(0, 1, 0)), EPS, "unit direction");
		// Twice the direction, half the t — this is what makes the segment test below a plain range check.
		assertEquals(2.5, FLOOR.rayTraceT(origin, new Vec3(0, 2, 0)), EPS, "doubled direction");
		assertVec(new Vec3(0, 5, 0), FLOOR.rayTrace(origin, new Vec3(0, 1, 0)), "hit point");
	}

	@Test
	@DisplayName("a ray parallel to the plane, or pointing away from it, hits nothing")
	void rayCanMiss() {
		assertTrue(Double.isNaN(FLOOR.rayTraceT(new Vec3(0, 0, 0), new Vec3(1, 0, 0))),
				"a parallel ray reported an intersection");
		assertNull(FLOOR.rayTrace(new Vec3(0, 0, 0), new Vec3(1, 0, 0)), "parallel");
		assertNull(FLOOR.rayTrace(new Vec3(0, 0, 0), new Vec3(0, -1, 0)), "pointing away");
		assertNotNull(FLOOR.rayTrace(new Vec3(0, 9, 0), new Vec3(0, -1, 0)),
				"starting above and pointing down should hit");
	}

	@Test
	@DisplayName("a segment crossing the plane gives the crossing point, and one stopping short gives null")
	void segmentCrossing() {
		// This is the portal-crossing test: where a mover was, and where it is.
		assertVec(new Vec3(0, 5, 0),
				FLOOR.intersectionWithSegment(new Vec3(0, 0, 0), new Vec3(0, 10, 0)),
				"crossing point");
		assertNull(FLOOR.intersectionWithSegment(new Vec3(0, 0, 0), new Vec3(0, 3, 0)),
				"a segment that stops short of the plane crossed nothing");
		assertNull(FLOOR.intersectionWithSegment(new Vec3(0, 6, 0), new Vec3(0, 9, 0)),
				"a segment entirely in front crossed nothing");
		// Crossing the other way is still a crossing.
		assertVec(new Vec3(0, 5, 0),
				FLOOR.intersectionWithSegment(new Vec3(0, 10, 0), new Vec3(0, 0, 0)),
				"downward crossing");
	}

	@Test
	@DisplayName("moving and flipping do what they say")
	void movedAndFlipped() {
		assertEquals(0.0, FLOOR.movedBy(3).distanceTo(new Vec3(0, 8, 0)), EPS,
				"moving the plane 3 up should put y=8 exactly on it");
		assertEquals(-3.0, FLOOR.flipped().distanceTo(new Vec3(0, 8, 0)), EPS,
				"flipping should put what was in front behind");
		assertEquals(0.0, FLOOR.through(new Vec3(1, 9, 1)).distanceTo(new Vec3(4, 9, -2)), EPS,
				"a parallel plane through a point should contain that point's height");
	}

	@Test
	@DisplayName("the plane equation agrees with the distance it is meant to encode")
	void equationMatchesDistance() {
		ImmPtlPlane tilted = new ImmPtlPlane(new Vec3(1, 2, 3), new Vec3(1, 1, 0));
		// Deliberately off the plane: (4, -1, 7) happens to lie exactly on it, and comparing zero to
		// zero would pass whatever the coefficients were.
		Vec3 p = new Vec3(4, 1, 7);
		assertEquals(Math.sqrt(2), tilted.distanceTo(p), EPS, "fixture is not off the plane");

		double viaEquation = tilted.equationX() * p.x()
				+ tilted.equationY() * p.y()
				+ tilted.equationZ() * p.z()
				+ tilted.equationW();
		assertEquals(tilted.distanceTo(p), viaEquation, EPS,
				"the four coefficients do not reproduce the signed distance");
		// And equationW is the number ObliqueNearPlane.offsetFor already computes.
		assertEquals(-tilted.normal().dot(tilted.point()), tilted.equationW(), EPS);
	}

	@Test
	@DisplayName("interpolation lands on each end and halfway between")
	void interpolationSpansTheEnds() {
		ImmPtlPlane a = new ImmPtlPlane(new Vec3(0, 0, 0), new Vec3(0, 1, 0));
		ImmPtlPlane b = new ImmPtlPlane(new Vec3(0, 10, 0), new Vec3(0, 1, 0));

		assertEquals(0.0, ImmPtlPlane.interpolate(a, b, 0).point().y(), EPS);
		assertEquals(10.0, ImmPtlPlane.interpolate(a, b, 1).point().y(), EPS);
		assertEquals(5.0, ImmPtlPlane.interpolate(a, b, 0.5).point().y(), EPS);
	}
}

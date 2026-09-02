package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.ObliqueNearPlane;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ObliqueNearPlane} against a projection matrix built here from the textbook formula
 * rather than borrowed from JOML — the class takes raw floats precisely so it can be tested without a
 * graphics library, the {@code MirrorScreenBoundsTest} idiom.
 *
 * <p>The invariant every test below leans on: after the modification the near-plane test
 * {@code z_clip + w_clip >= 0} must be the plane's own half-space, and must be it exactly — the two
 * quantities differ by one positive constant, not by an approximation.
 */
class ObliqueNearPlaneTest {
	private static final double NEAR = 0.05;
	private static final double FAR = 1000.0;

	/** Column-major, {@code [column * 4 + row]} — the layout {@code Matrix4f.get(float[])} writes. */
	private static float[] perspective(double fovDegrees, double aspect, double near, double far) {
		double f = 1.0 / Math.tan(Math.toRadians(fovDegrees) / 2.0);
		float[] m = new float[16];
		m[0] = (float) (f / aspect);
		m[5] = (float) f;
		m[10] = (float) ((far + near) / (near - far));
		m[11] = -1f;
		m[14] = (float) (2.0 * far * near / (near - far));
		return m;
	}

	/** {@code z_clip + w_clip} — the quantity OpenGL's near-plane test compares against zero. */
	private static double nearPlaneValue(float[] m, Vec3 p) {
		double z = m[2] * p.x() + m[6] * p.y() + m[10] * p.z() + m[14];
		double w = m[3] * p.x() + m[7] * p.y() + m[11] * p.z() + m[15];
		return z + w;
	}

	private static double planeValue(Vec3 normal, double offset, Vec3 p) {
		return normal.normalized().dot(p) + offset;
	}

	@Test
	@DisplayName("the near plane becomes the given plane, exactly")
	void nearPlaneBecomesTheGivenPlane() {
		float[] projection = perspective(70, 16.0 / 9.0, NEAR, FAR);
		Vec3 normal = new Vec3(0, 0, -1); // keep what is further down -Z than the plane
		double offset = ObliqueNearPlane.offsetFor(normal, new Vec3(0, 0, -3));
		float[] oblique = ObliqueNearPlane.apply(projection, normal, offset);
		assertNotNull(oblique);

		// Exactly, not approximately: the two quantities are proportional, so their ratio is one
		// constant for every point off the plane. An approximate clip would not hold this.
		Vec3 a = new Vec3(0, 0, -4);
		Vec3 b = new Vec3(2, 1, -9.5);
		double ratioA = nearPlaneValue(oblique, a) / planeValue(normal, offset, a);
		double ratioB = nearPlaneValue(oblique, b) / planeValue(normal, offset, b);
		assertTrue(ratioA > 0, "the surviving side must be the one the normal points at, was " + ratioA);
		assertEquals(ratioA, ratioB, Math.abs(ratioA) * 1e-4,
				"the modified near plane must be the plane itself, not an approximation of it");
	}

	@Test
	@DisplayName("a point on the plane sits exactly on the near plane")
	void aPointOnThePlaneIsOnTheNearPlane() {
		float[] projection = perspective(70, 16.0 / 9.0, NEAR, FAR);
		Vec3 normal = new Vec3(0, 0, -1);
		double offset = ObliqueNearPlane.offsetFor(normal, new Vec3(0, 0, -3));
		float[] oblique = ObliqueNearPlane.apply(projection, normal, offset);
		assertNotNull(oblique);
		assertEquals(0.0, nearPlaneValue(oblique, new Vec3(0, 0, -3)), 1e-4);
		assertEquals(0.0, nearPlaneValue(oblique, new Vec3(7, -4, -3)), 1e-4,
				"the whole plane, not just the point on the view axis");
	}

	@Test
	@DisplayName("a tilted plane clips the same half-space it describes")
	void tiltedPlaneAgreesEverywhere() {
		float[] projection = perspective(70, 16.0 / 9.0, NEAR, FAR);
		// A mirror seen at an angle — the ordinary case in a 6DoF game, and the one a fixed axis would
		// have got wrong.
		Vec3 normal = new Vec3(0.42, -0.15, -0.895);
		double offset = ObliqueNearPlane.offsetFor(normal, new Vec3(1.0, -0.5, -5.0));
		float[] oblique = ObliqueNearPlane.apply(projection, normal, offset);
		assertNotNull(oblique);

		Random random = new Random(11);
		for (int i = 0; i < 4000; i++) {
			Vec3 p = new Vec3(
					random.nextDouble() * 120 - 60,
					random.nextDouble() * 120 - 60,
					-random.nextDouble() * 400 - 0.2);
			double plane = planeValue(normal, offset, p);
			// Skip the boundary band: float storage makes the sign there genuinely undecided, and
			// asserting on it would be testing rounding rather than the transform.
			if (Math.abs(plane) < 1e-3) continue;
			assertEquals(plane > 0, nearPlaneValue(oblique, p) > 0,
					"disagreed at " + p + " (plane value " + plane + ")");
		}
	}

	@Test
	@DisplayName("the far plane is not quietly brought forward")
	void farPlaneKeepsItsRange() {
		float[] projection = perspective(70, 16.0 / 9.0, NEAR, FAR);
		Vec3 normal = new Vec3(0, 0, -1);
		double offset = ObliqueNearPlane.offsetFor(normal, new Vec3(0, 0, -3));
		float[] oblique = ObliqueNearPlane.apply(projection, normal, offset);
		assertNotNull(oblique);

		// The far corner in the plane normal's own direction is the one Lengyel's scale is chosen to
		// pin to the far plane; if the scale were merely positive this would land short and the view
		// would lose distant geometry.
		Vec3 corner = new Vec3(0, 0, -FAR);
		double z = oblique[2] * corner.x() + oblique[6] * corner.y() + oblique[10] * corner.z() + oblique[14];
		double w = oblique[3] * corner.x() + oblique[7] * corner.y() + oblique[11] * corner.z() + oblique[15];
		assertEquals(1.0, z / w, 1e-3, "the far corner must still map to the far plane");
	}

	@Test
	@DisplayName("the parts of the projection that are not the near plane are left alone")
	void onlyTheZRowChanges() {
		float[] projection = perspective(70, 16.0 / 9.0, NEAR, FAR);
		float[] oblique = ObliqueNearPlane.apply(projection, new Vec3(0, 0, -1),
				ObliqueNearPlane.offsetFor(new Vec3(0, 0, -1), new Vec3(0, 0, -3)));
		assertNotNull(oblique);
		for (int i = 0; i < 16; i++) {
			if (i == 2 || i == 6 || i == 10 || i == 14) continue; // the z row
			assertEquals(projection[i], oblique[i], 0f, "index " + i + " must not move");
		}
		// And the input is not modified in place — the caller may still want the original.
		assertEquals(0f, projection[2], 0f);
	}

	@Test
	@DisplayName("a refusal names its own guard, and success clears it")
	void refusalIsNamed() {
		float[] projection = perspective(70, 16.0 / 9.0, NEAR, FAR);
		Vec3 normal = new Vec3(0, 0, -1);
		double offset = ObliqueNearPlane.offsetFor(normal, new Vec3(0, 0, -3));

		// A report that says only "the clip plane failed" is one level too coarse to act on: there are
		// six guards here and they mean different things. Each says which one, and with the numbers.
		assertNull(ObliqueNearPlane.apply(projection, new Vec3(0, 0, 0), offset));
		assertNotNull(ObliqueNearPlane.lastRefusal());
		assertTrue(ObliqueNearPlane.lastRefusal().contains("normal is zero"),
				"unhelpful reason: " + ObliqueNearPlane.lastRefusal());

		float[] noFarPlane = projection.clone();
		noFarPlane[10] = -1f;
		assertNull(ObliqueNearPlane.apply(noFarPlane, normal, offset));
		assertTrue(ObliqueNearPlane.lastRefusal().contains("far plane"),
				"unhelpful reason: " + ObliqueNearPlane.lastRefusal());

		// And a success must clear it, or the next report would carry a stale reason as if it were live.
		assertNotNull(ObliqueNearPlane.apply(projection, normal, offset));
		assertNull(ObliqueNearPlane.lastRefusal(), "a stale refusal survived a successful call");
	}

	@Test
	@DisplayName("anything it cannot express returns null rather than a wrong clip")
	void degenerateInputReturnsNull() {
		float[] projection = perspective(70, 16.0 / 9.0, NEAR, FAR);
		Vec3 normal = new Vec3(0, 0, -1);
		double offset = ObliqueNearPlane.offsetFor(normal, new Vec3(0, 0, -3));

		assertNull(ObliqueNearPlane.apply(null, normal, offset));
		assertNull(ObliqueNearPlane.apply(new float[4], normal, offset));
		assertNull(ObliqueNearPlane.apply(projection, null, offset));
		assertNull(ObliqueNearPlane.apply(projection, new Vec3(0, 0, 0), offset),
				"a zero normal describes no plane");
		assertNull(ObliqueNearPlane.apply(new float[16], normal, offset),
				"an all-zero matrix is not a perspective projection");

		// The far plane is read back out of the matrix, so a matrix that does not carry one cannot be
		// bent: this is the guard that keeps a non-perspective projection from producing a plausible
		// but meaningless clip.
		float[] noFarPlane = projection.clone();
		noFarPlane[10] = -1f;
		assertNull(ObliqueNearPlane.apply(noFarPlane, normal, offset));

		float[] farPlaneBehindCamera = projection.clone();
		farPlaneBehindCamera[10] = -1.5f;
		farPlaneBehindCamera[14] = 0.5f;
		assertNull(ObliqueNearPlane.apply(farPlaneBehindCamera, normal, offset));
	}
}

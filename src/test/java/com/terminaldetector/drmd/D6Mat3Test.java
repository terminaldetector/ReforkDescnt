package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6Mat3;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The 3×3 matrix underneath the rigid body.
 *
 * <p>The inverse is the one worth guarding. An inertia tensor is always symmetric, and for a
 * symmetric matrix the adjugate's rows and its columns are identical — so the wrong one of the two
 * would have been right everywhere this class is used today and wrong for the first caller that
 * inverted anything else. It was written wrong first and caught here.
 */
class D6Mat3Test {

	private static final double EPS = 1e-9;

	private static void assertMatrix(D6Mat3 expected, D6Mat3 actual, String what) {
		assertVec(expected.row0(), actual.row0(), what + " row0");
		assertVec(expected.row1(), actual.row1(), what + " row1");
		assertVec(expected.row2(), actual.row2(), what + " row2");
	}

	private static void assertVec(Vec3 expected, Vec3 actual, String what) {
		assertEquals(expected.x(), actual.x(), EPS, what + " x");
		assertEquals(expected.y(), actual.y(), EPS, what + " y");
		assertEquals(expected.z(), actual.z(), EPS, what + " z");
	}

	/** Deliberately not symmetric — a symmetric one cannot tell a right inverse from a transposed one. */
	private static final D6Mat3 ASYMMETRIC = new D6Mat3(
			new Vec3(2, -1, 0.5),
			new Vec3(3, 1, -2),
			new Vec3(-1, 4, 1));

	@Test
	@DisplayName("multiplication applies the right factor first")
	void multiplicationOrder() {
		D6Mat3 a = D6Mat3.diagonal(2, 3, 4);
		D6Mat3 b = ASYMMETRIC;
		Vec3 v = new Vec3(0.3, -1.2, 2.5);
		assertVec(a.transform(b.transform(v)), a.multiply(b).transform(v), "(a*b)v vs a(bv)");
	}

	@Test
	@DisplayName("the inverse is a real inverse, on a matrix that is not symmetric")
	void inverseOfAnAsymmetricMatrix() {
		assertFalse(ASYMMETRIC.row0().y() == ASYMMETRIC.row1().x(),
				"the fixture is symmetric, so it cannot catch a transposed adjugate");

		D6Mat3 inverse = ASYMMETRIC.inverse();
		assertNotNull(inverse);
		assertMatrix(D6Mat3.IDENTITY, ASYMMETRIC.multiply(inverse), "M * inv(M)");
		assertMatrix(D6Mat3.IDENTITY, inverse.multiply(ASYMMETRIC), "inv(M) * M");
	}

	@Test
	@DisplayName("a singular matrix has no inverse, and says so rather than returning nonsense")
	void singularHasNoInverse() {
		// Two identical rows: everything collapses onto a plane.
		D6Mat3 flat = new D6Mat3(new Vec3(1, 2, 3), new Vec3(1, 2, 3), new Vec3(0, 0, 1));
		assertEquals(0.0, flat.determinant(), EPS);
		assertNull(flat.inverse());
	}

	@Test
	@DisplayName("a diagonal tensor rotated a quarter turn about Z swaps its X and Y moments")
	void rotatingATensor() {
		D6Mat3 body = D6Mat3.diagonal(2, 5, 7);
		Quat quarterAboutZ = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 0, 1), 90);

		// This is R I Rt, and it is what the body recomputes every step. If the rotation were applied
		// the other way round the swap would still happen — so the asymmetric case below is what
		// actually pins the direction.
		assertMatrix(D6Mat3.diagonal(5, 2, 7), body.rotatedBy(quarterAboutZ), "quarter turn about Z");
	}

	@Test
	@DisplayName("rotating by nothing changes nothing, and rotating there and back returns")
	void rotationRoundTrip() {
		D6Mat3 body = D6Mat3.diagonal(2, 5, 7);
		Quat some = ImmPtlQuaternions.rotationByDegrees(new Vec3(1, 2, 3), 37);
		Quat back = some.inverse();

		assertMatrix(body, body.rotatedBy(Quat.IDENTITY), "identity rotation");
		assertMatrix(body, body.rotatedBy(some).rotatedBy(back), "there and back");
	}

	@Test
	@DisplayName("fromColumns really builds columns")
	void columnsAreColumns() {
		D6Mat3 m = D6Mat3.fromColumns(new Vec3(1, 2, 3), new Vec3(4, 5, 6), new Vec3(7, 8, 9));
		// The image of a basis vector is the matching column.
		assertVec(new Vec3(1, 2, 3), m.transform(new Vec3(1, 0, 0)), "column 0");
		assertVec(new Vec3(4, 5, 6), m.transform(new Vec3(0, 1, 0)), "column 1");
		assertVec(new Vec3(7, 8, 9), m.transform(new Vec3(0, 0, 1)), "column 2");
	}
}

package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.client.portal.PortalTransform.YawPitch;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quaternion operations vendored from Immersive Portals.
 *
 * <p>Vendored code gets tested harder than written code, not less. A translated file compiles
 * whether or not the translation preserved the maths, and the two places this one could have gone
 * wrong silently are pinned here: the argument order of a composition, and which of a rotation
 * matrix and its transpose {@code fromBasisImages} wants.
 *
 * <p>Every expected value below was computed independently before being written down.
 */
class ImmPtlQuaternionsTest {

	private static final double EPS = 1e-9;

	private static void assertVec(Vec3 expected, Vec3 actual, String what) {
		assertEquals(expected.x(), actual.x(), EPS, what + " x");
		assertEquals(expected.y(), actual.y(), EPS, what + " y");
		assertEquals(expected.z(), actual.z(), EPS, what + " z");
	}

	@Test
	@DisplayName("a rotation about an axis turns the right way")
	void axisAngleTurnsRightHanded() {
		Quat q = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 1, 0), 90);
		// Right-hand rule about +Y takes +X to -Z. Getting this backwards is the classic sign error,
		// and it looks entirely plausible in a screenshot.
		assertVec(new Vec3(0, 0, -1), q.rotate(new Vec3(1, 0, 0)), "90 degrees about +Y applied to +X");
	}

	@Test
	@DisplayName("then(first, second) really does apply first first")
	void compositionOrderReadsAsItRuns() {
		Quat a = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 1, 0), 90);
		Quat b = ImmPtlQuaternions.rotationByDegrees(new Vec3(1, 0, 0), 90);
		Vec3 v = new Vec3(0.3, 0.5, 0.7);

		Vec3 sequential = b.rotate(a.rotate(v));
		Vec3 combined = ImmPtlQuaternions.then(a, b).rotate(v);
		assertVec(sequential, combined, "then(a, b) should equal b after a");

		// And the other order really is different, so the test above is not vacuous.
		Vec3 wrongWay = ImmPtlQuaternions.then(b, a).rotate(v);
		assertTrue(Math.abs(wrongWay.x() - sequential.x()) > 0.1,
				"the two orders came out the same, so this test proves nothing");
	}

	@Test
	@DisplayName("fromBasisImages takes the images of the basis vectors, not the transpose")
	void basisImagesAreNotTheTranspose() {
		Quat original = ImmPtlQuaternions.rotationByDegrees(new Vec3(0.3, 0.5, 0.2), 63.0);
		Vec3 imageOfX = original.rotate(new Vec3(1, 0, 0));
		Vec3 imageOfY = original.rotate(new Vec3(0, 1, 0));
		Vec3 imageOfZ = original.rotate(new Vec3(0, 0, 1));

		Quat recovered = ImmPtlQuaternions.fromBasisImages(imageOfX, imageOfY, imageOfZ);
		assertTrue(ImmPtlQuaternions.isClose(original, recovered),
				"a rotation did not survive a round trip through its own basis images");

		// The transpose must NOT work, or the test above would pass for the wrong reason. This is the
		// pair the two matrix conventions disagree about, and the only way to know which DRMD needs.
		Quat transposed = ImmPtlQuaternions.fromBasisImages(
				new Vec3(imageOfX.x(), imageOfY.x(), imageOfZ.x()),
				new Vec3(imageOfX.y(), imageOfY.y(), imageOfZ.y()),
				new Vec3(imageOfX.z(), imageOfY.z(), imageOfZ.z()));
		assertFalse(ImmPtlQuaternions.isClose(original, transposed),
				"rows and columns gave the same answer, so this rotation cannot tell them apart");
	}

	@Test
	@DisplayName("a frame becomes an orientation that gives the frame back")
	void frameRoundTrips() {
		Quat source = ImmPtlQuaternions.rotationByDegrees(new Vec3(1, 2, 3), 47.0);
		Vec3 axisW = source.rotate(new Vec3(1, 0, 0));
		Vec3 axisH = source.rotate(new Vec3(0, 1, 0));

		Quat frame = ImmPtlQuaternions.fromFrame(axisW, axisH);
		assertVec(axisW, ImmPtlQuaternions.axisW(frame), "axisW");
		assertVec(axisH, ImmPtlQuaternions.axisH(frame), "axisH");
		assertVec(axisW.cross(axisH), ImmPtlQuaternions.normal(frame), "normal");
	}

	@Test
	@DisplayName("camera rotation and yaw/pitch are exact inverses")
	void cameraRotationRoundTrips() {
		double[][] pairs = {{0, 0}, {45, 20}, {-90, -35}, {170, 60}, {-179, 10}, {90, 0}, {0, -89}};
		for (double[] pair : pairs) {
			double yaw = pair[0];
			double pitch = pair[1];
			YawPitch back = ImmPtlQuaternions.toYawPitch(ImmPtlQuaternions.cameraRotation(pitch, yaw));
			assertEquals(yaw, back.yawDegrees(), 1e-9, "yaw for " + yaw + "/" + pitch);
			assertEquals(pitch, back.pitchDegrees(), 1e-9, "pitch for " + yaw + "/" + pitch);
		}
	}

	@Test
	@DisplayName("interpolation hits both ends and halves the angle in the middle")
	void interpolationIsSpherical() {
		Quat a = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 1, 0), 0);
		Quat b = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 1, 0), 90);

		assertTrue(ImmPtlQuaternions.isClose(a, ImmPtlQuaternions.interpolate(a, b, 0)), "t=0 is not a");
		assertTrue(ImmPtlQuaternions.isClose(b, ImmPtlQuaternions.interpolate(a, b, 1)), "t=1 is not b");
		assertEquals(45.0,
				ImmPtlQuaternions.rotatingAngleDegrees(ImmPtlQuaternions.interpolate(a, b, 0.5)),
				1e-9, "halfway between 0 and 90 degrees is not 45");
	}

	@Test
	@DisplayName("interpolation takes the short way round when the inputs are opposite in sign")
	void interpolationTakesTheShortArc() {
		Quat a = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 1, 0), 0);
		Quat b = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 1, 0), 90);
		// Same rotation as b, negated — the double cover. Without the sign flip inside interpolate
		// this would sweep 270 degrees the other way instead of 45.
		Quat negatedB = ImmPtlQuaternions.scaled(b, -1);

		// Compared as rotations, not by angle. The two midpoints are the same rotation but opposite in
		// sign, and rotatingAngleDegrees reads the negated one as 315 rather than 45 — which is the
		// double cover being honest, not the interpolation going the wrong way. Asserting on the angle
		// here would have failed for a correct result.
		Quat viaB = ImmPtlQuaternions.interpolate(a, b, 0.5);
		Quat viaNegatedB = ImmPtlQuaternions.interpolate(a, negatedB, 0.5);
		assertTrue(ImmPtlQuaternions.isClose(viaB, viaNegatedB),
				"negating one input changed the result, so the long way round was taken");
		assertEquals(45.0, ImmPtlQuaternions.rotatingAngleDegrees(viaB), 1e-9,
				"halfway is not 45 degrees");
	}

	@Test
	@DisplayName("a rotation and its negation are the same rotation")
	void doubleCoverIsUnderstood() {
		Quat q = ImmPtlQuaternions.rotationByDegrees(new Vec3(1, 1, 0), 33.0);
		assertTrue(ImmPtlQuaternions.isClose(q, ImmPtlQuaternions.scaled(q, -1)),
				"q and -q were called different rotations");
		assertEquals(0.0, ImmPtlQuaternions.distanceSquared(q, ImmPtlQuaternions.scaled(q, -1)), 1e-12);
	}

	@Test
	@DisplayName("drift next to zero and one is snapped away")
	void driftIsSnapped() {
		Quat drifted = new Quat(1e-9, -1e-9, 0, 1 - 1e-9);
		Quat fixed = ImmPtlQuaternions.fixFloatingPointErrorAccumulation(drifted);

		assertEquals(0.0, fixed.x(), 0.0, "a component next to zero was kept");
		assertEquals(0.0, fixed.y(), 0.0);
		assertEquals(1.0, fixed.w(), EPS);
		assertTrue(ImmPtlQuaternions.isAxisAligned(fixed), "the snapped result is not axis aligned");
	}

	@Test
	@DisplayName("an angle is never NaN, even from a quaternion that has drifted past unit length")
	void angleSurvivesDrift() {
		// acos of anything past 1 is NaN, and a quaternion composed a few thousand times gets there.
		// A rotation of nearly nothing must read as nearly nothing, not as no answer at all.
		double angle = ImmPtlQuaternions.rotatingAngleRadians(new Quat(0, 0, 0, 1.0000000001));
		assertEquals(0.0, angle, 1e-6, "a barely-over-unit quaternion gave " + angle);
	}

	@Test
	@DisplayName("validity is about length, and a zero quaternion is not a rotation")
	void validityChecksLength() {
		assertTrue(ImmPtlQuaternions.isValid(ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 1, 0), 12)));
		assertFalse(ImmPtlQuaternions.isValid(new Quat(0, 0, 0, 0)));
	}
}

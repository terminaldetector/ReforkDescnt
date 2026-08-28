package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.client.portal.PortalTransform.YawPitch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link PortalTransform} directly — pure geometry, mirrors the {@code SkirtGeometryTest} idiom. */
class PortalTransformTest {
	private static final double EPS = 1e-9;

	private static void assertVec3Close(Vec3 expected, Vec3 actual, String msg) {
		assertTrue(Math.abs(expected.x() - actual.x()) < EPS
				&& Math.abs(expected.y() - actual.y()) < EPS
				&& Math.abs(expected.z() - actual.z()) < EPS,
				msg + ": expected " + expected + " but was " + actual);
	}

	@Test
	@DisplayName("transformPoint through a portal and back through its exact reverse returns the original point")
	void roundTripThroughAPortalAndItsReverseIsIdentity() {
		Vec3 portalPos = new Vec3(0, 0, 0);
		Vec3 portalNormal = new Vec3(1, 0, 0);
		Vec3 destPos = new Vec3(100, 0, 0);
		Vec3 destNormal = new Vec3(0, 1, 0);
		Vec3 point = new Vec3(5, 2, 3);

		Vec3 crossed = PortalTransform.transformPoint(point, portalPos, portalNormal, destPos, destNormal, 1.0);
		Vec3 back = PortalTransform.transformPoint(crossed, destPos, destNormal, portalPos, portalNormal, 1.0);

		assertVec3Close(point, back, "round trip should return the original point");
	}

	@Test
	@DisplayName("round trip still holds with a non-trivial scale factor")
	void roundTripHoldsWithScale() {
		Vec3 portalPos = new Vec3(1, 2, 3);
		Vec3 portalNormal = new Vec3(0, 0, 1);
		Vec3 destPos = new Vec3(-5, 8, 0);
		Vec3 destNormal = new Vec3(1, 0, 0);
		double scale = 2.5;
		Vec3 point = new Vec3(4, -1, 6);

		Vec3 crossed = PortalTransform.transformPoint(point, portalPos, portalNormal, destPos, destNormal, scale);
		Vec3 back = PortalTransform.transformPoint(crossed, destPos, destNormal, portalPos, portalNormal, 1.0 / scale);

		assertVec3Close(point, back, "round trip should hold with scale and its reciprocal");
	}

	@Test
	@DisplayName("two portals facing each other (a straight tunnel) transform as pure translation")
	void facingEachOtherIsPureTranslation() {
		Vec3 portalPos = new Vec3(1, 2, 3);
		Vec3 destPos = new Vec3(10, 20, 30);
		Vec3 portalNormal = new Vec3(0, 0, 1);
		Vec3 destNormal = new Vec3(0, 0, -1); // faces back toward the source: a straight tunnel
		Vec3 point = new Vec3(4, 5, 6);

		Vec3 result = PortalTransform.transformPoint(point, portalPos, portalNormal, destPos, destNormal, 1.0);
		Vec3 expected = destPos.plus(point.minus(portalPos));

		assertVec3Close(expected, result, "facing-each-other portals should reduce to pure translation");
	}

	@Test
	@DisplayName("rotationBetween two opposite vectors doesn't degenerate — realistic for same-facing portals")
	void oppositeNormalsRotateCorrectlyWithoutDegenerating() {
		Vec3 from = new Vec3(0, 0, 1);
		Vec3 to = new Vec3(0, 0, -1);

		Quat rotation = PortalTransform.rotationBetween(from, to);
		Vec3 rotated = rotation.rotate(from);

		assertVec3Close(to, rotated, "rotating 'from' by rotationBetween(from, to) should land exactly on 'to'");
	}

	@Test
	@DisplayName("two portals mounted facing the same absolute direction still transform a point sensibly")
	void sameFacingPortalsTransformWithoutNaN() {
		Vec3 portalPos = new Vec3(0, 0, 0);
		Vec3 destPos = new Vec3(50, 0, 0);
		Vec3 sharedNormal = new Vec3(0, 0, 1); // both portals face +Z
		Vec3 point = new Vec3(1, 0, 0);

		Vec3 result = PortalTransform.transformPoint(point, portalPos, sharedNormal, destPos, sharedNormal, 1.0);

		assertVec3Close(new Vec3(49, 0, 0), result, "same-facing portal pair should still resolve deterministically");
	}

	@Test
	@DisplayName("reflectPoint negates exactly the component along the mirror's normal, keeps the rest")
	void reflectPointNegatesOnlyTheNormalComponent() {
		Vec3 planePoint = new Vec3(0, 0, 0);
		Vec3 normal = new Vec3(0, 1, 0);
		Vec3 point = new Vec3(3, 5, 7);

		Vec3 reflected = PortalTransform.reflectPoint(point, planePoint, normal);

		assertVec3Close(new Vec3(3, -5, 7), reflected, "only the Y (normal) component should flip sign");
	}

	@Test
	@DisplayName("reflectVector matches reflectPoint's normal-only negation for a plane through the origin")
	void reflectVectorMatchesReflectPointAtOrigin() {
		Vec3 normal = new Vec3(0, 0, 1);
		Vec3 v = new Vec3(2, -3, 4);

		Vec3 reflectedVector = PortalTransform.reflectVector(v, normal);
		Vec3 reflectedPoint = PortalTransform.reflectPoint(v, new Vec3(0, 0, 0), normal);

		assertVec3Close(reflectedPoint, reflectedVector, "reflecting a vector should match reflecting the same point off the origin-plane");
	}

	@Test
	@DisplayName("reflecting twice across the same plane returns the original point")
	void doubleReflectionIsIdentity() {
		Vec3 planePoint = new Vec3(1, 1, 1);
		Vec3 normal = new Vec3(0, 1, 0);
		Vec3 point = new Vec3(9, 4, -2);

		Vec3 once = PortalTransform.reflectPoint(point, planePoint, normal);
		Vec3 twice = PortalTransform.reflectPoint(once, planePoint, normal);

		assertVec3Close(point, twice, "reflecting across the same plane twice should return the original point");
	}

	@Test
	@DisplayName("transformPoint and rotationBetween are deterministic")
	void isDeterministic() {
		Vec3 a = PortalTransform.transformPoint(new Vec3(1, 2, 3), new Vec3(0, 0, 0), new Vec3(1, 0, 0),
				new Vec3(5, 5, 5), new Vec3(0, 1, 0), 1.0);
		Vec3 b = PortalTransform.transformPoint(new Vec3(1, 2, 3), new Vec3(0, 0, 0), new Vec3(1, 0, 0),
				new Vec3(5, 5, 5), new Vec3(0, 1, 0), 1.0);
		assertEquals(a, b);
	}

	@Test
	@DisplayName("yaw 0 / pitch 0 looks along +Z, matching ShipAttitude's own convention")
	void yawZeroPitchZeroLooksAlongPositiveZ() {
		assertVec3Close(new Vec3(0, 0, 1), PortalTransform.yawPitchToVector(0, 0), "yaw=0,pitch=0");
	}

	@Test
	@DisplayName("yaw 90 looks along -X; pitch +90/-90 look straight down/up")
	void cardinalYawAndPitchLookWhereExpected() {
		assertVec3Close(new Vec3(-1, 0, 0), PortalTransform.yawPitchToVector(90, 0), "yaw=90,pitch=0");
		assertVec3Close(new Vec3(0, -1, 0), PortalTransform.yawPitchToVector(0, 90), "pitch=90 (down)");
		assertVec3Close(new Vec3(0, 1, 0), PortalTransform.yawPitchToVector(0, -90), "pitch=-90 (up)");
	}

	@Test
	@DisplayName("yawPitchToVector always returns a unit vector")
	void yawPitchToVectorIsUnitLength() {
		for (double yaw : new double[] {-170, -90, -45, 0, 30, 60, 90, 120, 179}) {
			for (double pitch : new double[] {-80, -45, -10, 0, 10, 45, 80}) {
				Vec3 v = PortalTransform.yawPitchToVector(yaw, pitch);
				assertTrue(Math.abs(v.length() - 1.0) < EPS, "not unit length at yaw=" + yaw + " pitch=" + pitch);
			}
		}
	}

	@Test
	@DisplayName("vectorToYawPitch is the exact inverse of yawPitchToVector away from the poles")
	void vectorToYawPitchRoundTrips() {
		for (double yaw : new double[] {-170, -90, -45, 0, 30, 60, 90, 120, 179}) {
			for (double pitch : new double[] {-80, -45, -10, 0, 10, 45, 80}) {
				Vec3 v = PortalTransform.yawPitchToVector(yaw, pitch);
				YawPitch back = PortalTransform.vectorToYawPitch(v);
				assertTrue(Math.abs(back.yawDegrees() - yaw) < 1e-6, "yaw round trip at yaw=" + yaw + " pitch=" + pitch
						+ ": got " + back.yawDegrees());
				assertTrue(Math.abs(back.pitchDegrees() - pitch) < 1e-6, "pitch round trip at yaw=" + yaw + " pitch=" + pitch
						+ ": got " + back.pitchDegrees());
			}
		}
	}

	@Test
	@DisplayName("vectorToYawPitch normalizes first, so scale doesn't change the answer")
	void vectorToYawPitchIgnoresScale() {
		Vec3 unit = PortalTransform.yawPitchToVector(37, -22);
		YawPitch fromUnit = PortalTransform.vectorToYawPitch(unit);
		YawPitch fromScaled = PortalTransform.vectorToYawPitch(unit.scaled(5.0));

		assertTrue(Math.abs(fromUnit.yawDegrees() - fromScaled.yawDegrees()) < 1e-9, "yaw should be scale-invariant");
		assertTrue(Math.abs(fromUnit.pitchDegrees() - fromScaled.pitchDegrees()) < 1e-9, "pitch should be scale-invariant");
	}

	@Test
	@DisplayName("a look direction reflected off a horizontal mirror flips only the pitch, matching reflectVector")
	void reflectVectorThroughYawPitchFlipsOnlyPitch() {
		double yaw = 30;
		double pitch = 40;
		Vec3 forward = PortalTransform.yawPitchToVector(yaw, pitch);

		Vec3 reflected = PortalTransform.reflectVector(forward, new Vec3(0, 1, 0));
		YawPitch result = PortalTransform.vectorToYawPitch(reflected);

		assertTrue(Math.abs(result.yawDegrees() - yaw) < 1e-9, "yaw should be unchanged by a horizontal mirror");
		assertTrue(Math.abs(result.pitchDegrees() - (-pitch)) < 1e-9, "pitch should invert through a horizontal mirror");
	}

	@Test
	@DisplayName("yawPitchToVector and vectorToYawPitch are deterministic")
	void yawPitchConversionIsDeterministic() {
		assertEquals(PortalTransform.yawPitchToVector(12, -34), PortalTransform.yawPitchToVector(12, -34));
		assertEquals(PortalTransform.vectorToYawPitch(new Vec3(0.4, 0.3, 0.5)),
				PortalTransform.vectorToYawPitch(new Vec3(0.4, 0.3, 0.5)));
	}
}

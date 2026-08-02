package com.terminaldetector.drmd;

import com.terminaldetector.drmd.flight.ShipAttitude;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guards for the attitude maths.
 *
 * <p>Every case here corresponds to a bug that shipped: the pole snap, the horizon detaching from
 * the hull, and the ceiling flip normalising a zero vector.
 */
class ShipAttitudeTest {
	private static Vec3d look(double yawDeg, double pitchDeg) {
		double y = Math.toRadians(yawDeg);
		double p = Math.toRadians(pitchDeg);
		return new Vec3d(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
	}

	@Test
	@DisplayName("level flight has zero bank at every heading")
	void levelFlightHasNoBank() {
		for (int yaw = -180; yaw <= 180; yaw += 3) {
			for (int pitch = -89; pitch <= 89; pitch += 7) {
				Vec3d f = look(yaw, pitch);
				float bank = ShipAttitude.bankDegrees(f, ShipAttitude.levelUpOf(f));
				assertEquals(0f, bank, 1e-3f,
						"bank should vanish at yaw=" + yaw + " pitch=" + pitch);
			}
		}
	}

	@Test
	@DisplayName("the zero-roll frame stays defined through the poles")
	void frameSurvivesPoles() {
		for (double pitch : new double[]{89.0, 89.9, 89.99, 90.0, -89.0, -90.0}) {
			Vec3d f = look(37, pitch);
			Vec3d right = ShipAttitude.levelRightOf(f);
			Vec3d up = ShipAttitude.levelUpOf(f);
			assertEquals(1.0, right.length(), 1e-9, "levelRight must stay unit at pitch " + pitch);
			assertEquals(1.0, up.length(), 1e-9, "levelUp must stay unit at pitch " + pitch);
			assertTrue(Math.abs(right.dotProduct(f)) < 1e-6, "levelRight must stay perpendicular");
		}
	}

	@Test
	@DisplayName("bank does not jump as the nose approaches vertical")
	void noBankSnapNearVertical() {
		// The old forward x worldUp frame collapsed here and made the read-out snap ~174 degrees a
		// couple of degrees off vertical.
		float previous = ShipAttitude.bankDegrees(look(37, 80), ShipAttitude.levelUpOf(look(37, 80)));
		for (double pitch = 80.0; pitch <= 89.9; pitch += 0.1) {
			Vec3d f = look(37, pitch);
			float bank = ShipAttitude.bankDegrees(f, ShipAttitude.levelUpOf(f));
			assertTrue(Math.abs(bank - previous) < 1.0,
					"bank jumped from " + previous + " to " + bank + " at pitch " + pitch);
			previous = bank;
		}
	}

	@Test
	@DisplayName("a rolled hull reports the roll it was given")
	void bankMatchesAppliedRoll() {
		for (int yaw = -180; yaw <= 180; yaw += 45) {
			for (int pitch : new int[]{-60, -20, 0, 20, 60}) {
				for (int roll : new int[]{-170, -90, -30, 0, 30, 90, 170}) {
					ShipAttitude att = new ShipAttitude();
					att.fromLook(look(yaw, pitch));
					att.rollLocal(roll);
					assertEquals(roll, att.bankDegrees(), 1e-2,
							"yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
				}
			}
		}
	}

	@Test
	@DisplayName("slerp stays on the unit sphere, including antipodal")
	void slerpNeverCollapses() {
		Vec3d up = new Vec3d(0, 1, 0);
		Vec3d down = new Vec3d(0, -1, 0);
		// A component lerp passes through the origin here and normalising it is a division by zero.
		for (double t = 0.0; t <= 1.0; t += 0.05) {
			Vec3d v = ShipAttitude.slerp(up, down, t);
			assertEquals(1.0, v.length(), 1e-6, "ceiling flip lost the unit length at t=" + t);
		}
		assertEquals(1.0, ShipAttitude.slerp(up, down, 0.5).length(), 1e-6);
	}

	@Test
	@DisplayName("slerp hits both endpoints")
	void slerpEndpoints() {
		Vec3d a = new Vec3d(1, 0, 0);
		Vec3d b = new Vec3d(0, 0, 1);
		assertTrue(ShipAttitude.slerp(a, b, 0.0).squaredDistanceTo(a) < 1e-12);
		assertTrue(ShipAttitude.slerp(a, b, 1.0).squaredDistanceTo(b) < 1e-12);
	}

	@Test
	@DisplayName("bank puts an arbitrary up vector on screen-up")
	void bankLandsUpOnScreenUp() {
		// What the gravity torch relies on: rolling the view by this angle stands the surface upright.
		Vec3d wallUp = new Vec3d(1, 0, 0);
		for (int yaw = -180; yaw <= 180; yaw += 15) {
			Vec3d f = look(yaw, 20);
			if (Math.abs(f.dotProduct(wallUp)) > 0.98) continue;
			float bank = ShipAttitude.bankDegrees(f, wallUp);
			double screenX = wallUp.dotProduct(ShipAttitude.levelRightOf(f));
			double screenY = wallUp.dotProduct(ShipAttitude.levelUpOf(f));
			assertEquals(Math.toDegrees(Math.atan2(screenX, screenY)), bank, 1e-3);
		}
	}
}

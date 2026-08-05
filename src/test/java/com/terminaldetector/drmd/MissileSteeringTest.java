package com.terminaldetector.drmd;

import com.terminaldetector.drmd.weapon.core.MissileSteering;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guards for the missile turn.
 *
 * <p>The reversal cases are the ones that shipped broken: a plain blend is singular when the target
 * is directly astern, and which way it fails depends on the turn rate. At the Homing missile's 140
 * deg/s it stalled — a target straight behind was never turned toward at all — and at the Guided
 * missile's 200 it snapped a full half-turn inside one tick.
 */
class MissileSteeringTest {
	/** Turn rates actually flown: seeker head, pilot's hand, Smart Missile blob. */
	private static final float SEEKER = 140f;
	private static final float PILOT = 200f;
	private static final float BLOB = 260f;

	private static final float[] ALL_RATES = {SEEKER, PILOT, BLOB};

	private static double angle(Vec3d a, Vec3d b) {
		double d = a.normalize().dotProduct(b.normalize());
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d))));
	}

	@Test
	@DisplayName("a turn never changes speed")
	void turnPreservesSpeed() {
		Vec3d vel = new Vec3d(0, 0, 30);
		for (float rate : ALL_RATES) {
			for (Vec3d want : new Vec3d[]{new Vec3d(1, 0, 0), new Vec3d(0, 1, 0),
					new Vec3d(0, 0, -1), new Vec3d(0.3, -0.8, 0.5)}) {
				assertEquals(30.0, MissileSteering.steer(vel, want, rate).length(), 1e-9,
						"speed changed while turning at " + rate);
			}
		}
	}

	@Test
	@DisplayName("steering closes the angle and holds the heading once there")
	void steeringConverges() {
		for (float rate : ALL_RATES) {
			Vec3d vel = new Vec3d(0, 0, 30);
			Vec3d want = new Vec3d(0.6, 0.3, -0.74);
			double before = angle(vel, want);
			assertTrue(angle(MissileSteering.steer(vel, want, rate), want) < before,
					"one tick must close the angle at " + rate);
			for (int i = 0; i < 60; i++) vel = MissileSteering.steer(vel, want, rate);
			assertTrue(angle(vel, want) < 0.5, "must arrive on the heading at " + rate);
		}
	}

	@Test
	@DisplayName("a target straight astern is turned toward, not stalled on or snapped to")
	void reversalIsATurn() {
		for (float rate : ALL_RATES) {
			Vec3d vel = new Vec3d(0, 0, 30);
			Vec3d want = new Vec3d(0, 0, -1);

			double turned = angle(vel, MissileSteering.steer(vel, want, rate));
			assertTrue(turned > 0.5, "stalled facing away at " + rate + " (turned " + turned + ")");
			assertTrue(turned < 175, "snapped round in one tick at " + rate + " (turned " + turned + ")");

			for (int i = 0; i < 120; i++) vel = MissileSteering.steer(vel, want, rate);
			assertTrue(angle(vel, want) < 1.0, "reversal never completed at " + rate);
		}
	}

	@Test
	@DisplayName("reversing while flying vertically also turns")
	void verticalReversalIsATurn() {
		// The off-axis nudge has to pick a different helper axis near world up, or the cross
		// product it is built from collapses and the missile is back to not turning.
		Vec3d vel = new Vec3d(0, 30, 0);
		Vec3d want = new Vec3d(0, -1, 0);
		double turned = angle(vel, MissileSteering.steer(vel, want, PILOT));
		assertTrue(turned > 0.5 && turned < 175, "vertical reversal turned " + turned + " degrees");
		for (int i = 0; i < 120; i++) vel = MissileSteering.steer(vel, want, PILOT);
		assertTrue(angle(vel, want) < 1.0, "vertical reversal never completed");
	}

	@Test
	@DisplayName("nothing to steer toward leaves the missile flying")
	void degenerateInputsAreInert() {
		Vec3d vel = new Vec3d(0, 0, 30);
		// The owner logging out mid-flight is the live case: no aim, so no correction.
		assertEquals(vel, MissileSteering.steer(vel, Vec3d.ZERO, PILOT));

		Vec3d out = MissileSteering.steer(Vec3d.ZERO, new Vec3d(1, 0, 0), PILOT);
		assertEquals(0.0, out.length(), 1e-9, "a stopped projectile stays stopped");
		assertFalse(Double.isNaN(out.x) || Double.isNaN(out.y) || Double.isNaN(out.z));
	}

	@Test
	@DisplayName("the pilot out-turns a seeker head")
	void higherRateTurnsHarder() {
		Vec3d vel = new Vec3d(0, 0, 30);
		Vec3d want = new Vec3d(1, 0, 0);
		assertTrue(angle(vel, MissileSteering.steer(vel, want, PILOT))
						> angle(vel, MissileSteering.steer(vel, want, SEEKER)),
				"flying it yourself has to buy agility over a lock");
	}
}

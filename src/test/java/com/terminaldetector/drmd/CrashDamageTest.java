package com.terminaldetector.drmd;

import com.terminaldetector.drmd.flight.CrashDamage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the curve's shape, not its exact tuned values — {@link CrashDamage} states plainly it is a
 * starting point. What must hold regardless of later tuning: nothing under the threshold hurts,
 * damage only grows with speed above it, and it never exceeds the cap.
 */
class CrashDamageTest {
	@Test
	@DisplayName("ordinary cruising, at or under the threshold, never hurts")
	void underThresholdIsSafe() {
		assertEquals(0f, CrashDamage.damageFor(0));
		assertEquals(0f, CrashDamage.damageFor(27.5));
		assertEquals(0f, CrashDamage.damageFor(CrashDamage.THRESHOLD));
	}

	@Test
	@DisplayName("damage grows monotonically with speed above the threshold")
	void monotonicAboveThreshold() {
		float last = 0f;
		for (double speed = CrashDamage.THRESHOLD; speed <= CrashDamage.THRESHOLD + 200; speed += 1.0) {
			float dmg = CrashDamage.damageFor(speed);
			assertTrue(dmg >= last, "damage must not decrease as speed increases (at " + speed + ")");
			last = dmg;
		}
	}

	@Test
	@DisplayName("damage is capped, however fast the impact")
	void neverExceedsCap() {
		assertEquals(CrashDamage.MAX_DAMAGE, CrashDamage.damageFor(1_000_000.0));
		assertTrue(CrashDamage.damageFor(CrashDamage.THRESHOLD + CrashDamage.SCALE * 1000) <= CrashDamage.MAX_DAMAGE);
	}

	@Test
	@DisplayName("a graze just past the threshold barely registers, matching the quadratic shape")
	void quadraticNearThreshold() {
		// One SCALE unit over the threshold: (1)^2 == 1 damage exactly.
		assertEquals(1f, CrashDamage.damageFor(CrashDamage.THRESHOLD + CrashDamage.SCALE), 1e-6f);
	}
}

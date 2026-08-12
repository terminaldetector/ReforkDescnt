package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.structure.StructureCrash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Calls {@link StructureCrash} directly — zero Minecraft dependency, same idiom as {@code AerisDensity}. */
class StructureCrashTest {
	@Test
	@DisplayName("fallSpeed is monotonic non-decreasing as crashTicks grows")
	void monotonicNonDecreasing() {
		double prev = StructureCrash.fallSpeed(0);
		for (int t = 1; t <= 1000; t++) {
			double cur = StructureCrash.fallSpeed(t);
			assertTrue(cur >= prev, "fallSpeed(" + t + ")=" + cur + " should be >= fallSpeed(" + (t - 1) + ")=" + prev);
			prev = cur;
		}
	}

	@Test
	@DisplayName("fallSpeed never exceeds MAX, however long the crash runs")
	void neverExceedsMax() {
		for (int t = 0; t <= 100_000; t += 137) {
			assertTrue(StructureCrash.fallSpeed(t) <= StructureCrash.MAX,
					"fallSpeed(" + t + ") exceeded MAX (" + StructureCrash.MAX + ")");
		}
		assertEquals(StructureCrash.MAX, StructureCrash.fallSpeed(1_000_000), 1e-9,
				"a long-running crash should have plateaued at MAX");
	}

	@Test
	@DisplayName("fallSpeed starts at BASE and is deterministic")
	void startsAtBaseAndIsDeterministic() {
		assertEquals(StructureCrash.BASE, StructureCrash.fallSpeed(0), 1e-9);
		for (int t = 0; t <= 200; t++) {
			assertEquals(StructureCrash.fallSpeed(t), StructureCrash.fallSpeed(t), 0.0);
		}
	}

	@Test
	@DisplayName("negative crashTicks (defensive input) clamps to BASE rather than going negative")
	void negativeTicksClampToBase() {
		assertEquals(StructureCrash.BASE, StructureCrash.fallSpeed(-5), 1e-9);
		assertEquals(StructureCrash.BASE, StructureCrash.fallSpeed(-1000), 1e-9);
	}

	@Test
	@DisplayName("nextDrop's remainder always stays in [0, 1) — never accumulates a whole block without reporting it")
	void nextDropRemainderStaysInUnitRange() {
		double acc = 0;
		for (int t = 0; t <= 500; t++) {
			StructureCrash.DescentStep step = StructureCrash.nextDrop(acc, t);
			assertTrue(step.remainder() >= 0.0 && step.remainder() < 1.0,
					"remainder " + step.remainder() + " at t=" + t + " should be in [0,1)");
			acc = step.remainder();
		}
	}

	@Test
	@DisplayName("nextDrop never reports a negative whole-block count")
	void nextDropNeverNegative() {
		double acc = 0;
		for (int t = 0; t <= 500; t++) {
			StructureCrash.DescentStep step = StructureCrash.nextDrop(acc, t);
			assertTrue(step.wholeBlocks() >= 0, "wholeBlocks should never be negative at t=" + t);
			acc = step.remainder();
		}
	}

	@Test
	@DisplayName("nextDrop's accumulated whole-block drops track the summed fall-speed curve within one block of flooring error")
	void nextDropTracksFallSpeedOverManyTicks() {
		double acc = 0;
		long totalWhole = 0;
		double expectedTotal = 0;
		int ticks = 300;
		for (int t = 0; t < ticks; t++) {
			StructureCrash.DescentStep step = StructureCrash.nextDrop(acc, t);
			totalWhole += step.wholeBlocks();
			acc = step.remainder();
			expectedTotal += StructureCrash.fallSpeed(t);
		}
		assertTrue(Math.abs(totalWhole - expectedTotal) < 1.0,
				"accumulated whole-block drops (" + totalWhole + ") should track the summed fall speed ("
						+ expectedTotal + ") within one block of flooring error");
	}

	@Test
	@DisplayName("nextDrop is deterministic for the same accumulator and tick count")
	void nextDropIsDeterministic() {
		assertEquals(StructureCrash.nextDrop(0.7, 42), StructureCrash.nextDrop(0.7, 42));
	}
}

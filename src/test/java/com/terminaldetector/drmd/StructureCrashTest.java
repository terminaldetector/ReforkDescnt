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
}

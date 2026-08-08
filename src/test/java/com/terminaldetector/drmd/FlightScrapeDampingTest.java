package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@code ServerPlayerFlightTravelMixin}'s {@code SCRAPE_KEEP_CUBIC}/{@code SCRAPE_KEEP_SMOOTH}
 * split ({@code ServerPlayerEntity} is unavailable here, same mirroring approach as
 * {@code LevelBuilderPriorityStreamTest}). Pins the actual, narratively load-bearing claim behind the
 * fix: {@code Entity.move} already zeroes whichever axis a collision sweep blocked, so what compounds
 * across repeated grazes down a non-axis-aligned corridor is this multiplier applied to whatever
 * velocity survived — not how finely the wall geometry is chamfered.
 */
class FlightScrapeDampingTest {
	private static final double SCRAPE_KEEP_CUBIC = 0.86;
	private static final double SCRAPE_KEEP_SMOOTH = 0.95;

	@Test
	@DisplayName("ten consecutive grazes against a structure leave about 22% of speed — the reported discomfort")
	void cubicCompoundsSteeply() {
		assertEquals(0.221, Math.pow(SCRAPE_KEEP_CUBIC, 10), 0.001);
	}

	@Test
	@DisplayName("the same ten grazes against natural terrain leave about 60% of speed")
	void smoothCompoundsGently() {
		assertEquals(0.599, Math.pow(SCRAPE_KEEP_SMOOTH, 10), 0.001);
	}

	@Test
	@DisplayName("SMOOTH retains more speed than CUBIC after any number of grazes, not just at ten")
	void smoothNeverCompoundsWorseThanCubic() {
		for (int grazes = 1; grazes <= 30; grazes++) {
			double cubic = Math.pow(SCRAPE_KEEP_CUBIC, grazes);
			double smooth = Math.pow(SCRAPE_KEEP_SMOOTH, grazes);
			assertTrue(smooth > cubic,
					"at " + grazes + " grazes, SMOOTH (" + smooth + ") should retain more speed than CUBIC ("
							+ cubic + ")");
		}
	}

	@Test
	@DisplayName("the split is real — SMOOTH keeps strictly more speed per graze than CUBIC")
	void singleGrazeAlreadyDiffers() {
		assertTrue(SCRAPE_KEEP_SMOOTH > SCRAPE_KEEP_CUBIC);
	}
}

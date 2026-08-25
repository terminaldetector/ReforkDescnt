package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.planet.HorizonProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code HorizonProjection} is pure math with zero imports, so unlike almost everything else in the
 * voxel-horizon renderer it can be pinned directly here without a Minecraft classpath. Added
 * alongside the Orbit-band reach boost ({@code CLIP_USE_ORBIT}): the one thing a one-line arithmetic
 * edit to that formula could quietly break — the curve's identity/slope-1 join at {@code inner} and
 * its never-exceeds-reach ceiling — would otherwise only surface in a live frame nobody here can
 * render (see {@code docs/VOXEL_HORIZON.md}'s own "never seen a real frame" caveat).
 */
class HorizonProjectionTest {
	private static final double INNER = 200.0;
	private static final double REACH_BASE = 550.0;
	private static final double REACH_ORBIT = 650.0;

	@Test
	@DisplayName("CLIP_USE_ORBIT leaves more of the far plane in play than CLIP_USE, but still under 1")
	void orbitShareIsBetweenBaseShareAndOne() {
		assertTrue(HorizonProjection.CLIP_USE > 0.0);
		assertTrue(HorizonProjection.CLIP_USE_ORBIT > HorizonProjection.CLIP_USE);
		assertTrue(HorizonProjection.CLIP_USE_ORBIT < 1.0);
	}

	@Test
	@DisplayName("at inner the curve is the identity for both the base and orbit reach")
	void identityAtInnerHoldsForBothReaches() {
		assertEquals(INNER, HorizonProjection.compress(INNER, INNER, REACH_BASE), 1e-9);
		assertEquals(INNER, HorizonProjection.compress(INNER, INNER, REACH_ORBIT), 1e-9);
	}

	@Test
	@DisplayName("just past inner the slope is one for both reaches — no step at the chunk-edge join")
	void slopeAtInnerIsOneForBothReaches() {
		double step = 1e-3;
		double dBase = HorizonProjection.compress(INNER + step, INNER, REACH_BASE) - INNER;
		double dOrbit = HorizonProjection.compress(INNER + step, INNER, REACH_ORBIT) - INNER;
		assertEquals(step, dBase, step * 0.01);
		assertEquals(step, dOrbit, step * 0.01);
	}

	@Test
	@DisplayName("compress never reaches or exceeds reach, at any true distance, for either reach value")
	void compressNeverReachesReach() {
		double[] distances = {INNER, INNER + 1, REACH_BASE, REACH_BASE * 5, REACH_ORBIT * 5,
				HorizonProjection.MAX_TRUE_RADIUS};
		for (double d : distances) {
			assertTrue(HorizonProjection.compress(d, INNER, REACH_BASE) < REACH_BASE);
			assertTrue(HorizonProjection.compress(d, INNER, REACH_ORBIT) < REACH_ORBIT);
		}
	}

	@Test
	@DisplayName("a larger reach draws the same far true distance farther out, not closer")
	void largerReachDrawsFarPointsFartherOut() {
		double farDistance = HorizonProjection.MAX_TRUE_RADIUS;
		double drawnBase = HorizonProjection.compress(farDistance, INNER, REACH_BASE);
		double drawnOrbit = HorizonProjection.compress(farDistance, INNER, REACH_ORBIT);
		assertTrue(drawnOrbit > drawnBase);
	}
}

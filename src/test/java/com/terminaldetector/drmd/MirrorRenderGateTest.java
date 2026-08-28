package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.MirrorRenderGate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link MirrorRenderGate} directly — pure logic, mirrors the {@code SkirtGeometryTest} idiom. */
class MirrorRenderGateTest {
	@Test
	@DisplayName("a nearby mirror at the primary view (depth 0) renders")
	void nearbyPrimaryViewRenders() {
		assertTrue(MirrorRenderGate.shouldRender(0, MirrorRenderGate.DEFAULT_MAX_RECURSION_DEPTH, 10.0, 256.0));
	}

	@Test
	@DisplayName("a mirror exactly at the render range renders; just past it does not")
	void rangeBoundaryIsInclusive() {
		assertTrue(MirrorRenderGate.shouldRender(0, MirrorRenderGate.DEFAULT_MAX_RECURSION_DEPTH, 256.0, 256.0));
		assertFalse(MirrorRenderGate.shouldRender(0, MirrorRenderGate.DEFAULT_MAX_RECURSION_DEPTH, 256.01, 256.0));
	}

	@Test
	@DisplayName("recursion depth past the cap never renders, regardless of distance")
	void pastMaxRecursionDepthNeverRenders() {
		assertFalse(MirrorRenderGate.shouldRender(6, 5, 0.0, 999999.0));
		assertFalse(MirrorRenderGate.shouldRender(100, 5, 0.0, 999999.0));
	}

	@Test
	@DisplayName("recursion depth exactly at the cap still renders")
	void depthExactlyAtCapRenders() {
		assertTrue(MirrorRenderGate.shouldRender(5, 5, 1.0, 256.0));
	}

	@Test
	@DisplayName("a negative distance (caller error) never renders rather than dividing by a bad value")
	void negativeDistanceNeverRenders() {
		assertFalse(MirrorRenderGate.shouldRender(0, 5, -1.0, 256.0));
	}

	@Test
	@DisplayName("render range at depth 0 or 1 is unchanged; it shrinks starting at depth 2")
	void rangeShrinksOnlyPastDepthOne() {
		assertEquals(256.0, MirrorRenderGate.renderRangeForDepth(256.0, 0));
		assertEquals(256.0, MirrorRenderGate.renderRangeForDepth(256.0, 1));
		assertEquals(128.0, MirrorRenderGate.renderRangeForDepth(256.0, 2));
		assertEquals(256.0 / 3, MirrorRenderGate.renderRangeForDepth(256.0, 3));
	}

	@Test
	@DisplayName("a mirror-in-mirror at depth 2 is rejected past the shrunk range even though the base range would allow it")
	void deeperRecursionUsesShrunkRangeInShouldRender() {
		// base range 256, depth 2 -> effective range 128; distance 150 clears the base range but not the shrunk one
		assertFalse(MirrorRenderGate.shouldRender(2, 5, 150.0, 256.0));
		assertTrue(MirrorRenderGate.shouldRender(2, 5, 100.0, 256.0));
	}

	@Test
	@DisplayName("shouldRender and renderRangeForDepth are deterministic")
	void isDeterministic() {
		assertEquals(
				MirrorRenderGate.shouldRender(1, 5, 40.0, 256.0),
				MirrorRenderGate.shouldRender(1, 5, 40.0, 256.0));
		assertEquals(
				MirrorRenderGate.renderRangeForDepth(256.0, 3),
				MirrorRenderGate.renderRangeForDepth(256.0, 3));
	}
}

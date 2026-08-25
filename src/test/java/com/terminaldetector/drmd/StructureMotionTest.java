package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.structure.StructureMotion;
import com.terminaldetector.drmd.world.structure.StructureMotion.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Calls {@link StructureMotion} directly — zero Minecraft dependency, same idiom as {@code StructureCrashTest}. */
class StructureMotionTest {
	private static final double EPS = 1e-9;

	@Test
	@DisplayName("interpolate at fraction 0.5 lands exactly on the midpoint")
	void linearMidpoint() {
		Sample prev = new Sample(0, 10, -20, 0f, 100);
		Sample curr = new Sample(10, 20, 0, 0f, 106);
		Sample mid = StructureMotion.interpolate(prev, curr, 0.5);
		assertEquals(5.0, mid.x(), EPS);
		assertEquals(15.0, mid.y(), EPS);
		assertEquals(-10.0, mid.z(), EPS);
	}

	@Test
	@DisplayName("interpolate at fraction 0 is exactly prev's position; at 1, exactly curr's")
	void boundaryFractionsAreExact() {
		Sample prev = new Sample(1, 2, 3, 45f, 100);
		Sample curr = new Sample(9, 8, 7, 90f, 106);

		Sample atZero = StructureMotion.interpolate(prev, curr, 0.0);
		assertEquals(prev.x(), atZero.x(), EPS);
		assertEquals(prev.y(), atZero.y(), EPS);
		assertEquals(prev.z(), atZero.z(), EPS);
		assertEquals(prev.yaw(), atZero.yaw(), 1e-4f);

		Sample atOne = StructureMotion.interpolate(prev, curr, 1.0);
		assertEquals(curr.x(), atOne.x(), EPS);
		assertEquals(curr.y(), atOne.y(), EPS);
		assertEquals(curr.z(), atOne.z(), EPS);
		assertEquals(curr.yaw(), atOne.yaw(), 1e-4f);
	}

	@Test
	@DisplayName("yaw blends the short way forward through the 360/0 seam, not backward through 180")
	void yawWrapsForwardThroughSeam() {
		Sample prev = new Sample(0, 0, 0, 350f, 100);
		Sample curr = new Sample(0, 0, 0, 10f, 106);
		Sample mid = StructureMotion.interpolate(prev, curr, 0.5);
		// 350 -> 360/0 -> 10 is a 20-degree forward turn; the midpoint is 0 (i.e. 360), not 180.
		float wrapped = ((mid.yaw() % 360f) + 360f) % 360f;
		assertEquals(0f, wrapped, 1e-3f, "midpoint of 350->10 the short way should be 0/360, got " + mid.yaw());
	}

	@Test
	@DisplayName("yaw blends the short way backward through the 360/0 seam, not forward through 180")
	void yawWrapsBackwardThroughSeam() {
		Sample prev = new Sample(0, 0, 0, 10f, 100);
		Sample curr = new Sample(0, 0, 0, 350f, 106);
		Sample mid = StructureMotion.interpolate(prev, curr, 0.5);
		// 10 -> 0/360 -> 350 is a 20-degree backward turn; the midpoint is 0 (i.e. 360), not 180.
		float wrapped = ((mid.yaw() % 360f) + 360f) % 360f;
		assertEquals(0f, wrapped, 1e-3f, "midpoint of 10->350 the short way should be 0/360, got " + mid.yaw());
	}

	@Test
	@DisplayName("interpolate is deterministic for the same inputs")
	void interpolateIsDeterministic() {
		Sample prev = new Sample(1, 2, 3, 15f, 100);
		Sample curr = new Sample(4, 5, 6, 200f, 108);
		assertEquals(StructureMotion.interpolate(prev, curr, 0.37), StructureMotion.interpolate(prev, curr, 0.37));
	}

	@Test
	@DisplayName("fraction is 0 exactly at prevTick and 1 exactly at currTick")
	void fractionBoundariesAreExact() {
		assertEquals(0.0, StructureMotion.fraction(100, 106, 100, 0.0), EPS);
		assertEquals(1.0, StructureMotion.fraction(100, 106, 106, 0.0), EPS);
	}

	@Test
	@DisplayName("fraction accounts for partial-tick delta between whole ticks")
	void fractionIncludesTickDelta() {
		// Render time 102.5 is 2.5 ticks past prevTick(100), over a 6-tick span to currTick(106).
		double f = StructureMotion.fraction(100, 106, 102, 0.5);
		assertEquals(2.5 / 6.0, f, EPS);
		// (102, 1.0) and (103, 0.0) both name render time 103 — the same fraction either way.
		assertEquals(StructureMotion.fraction(100, 106, 103, 0.0),
				StructureMotion.fraction(100, 106, 102, 1.0), EPS);
	}

	@Test
	@DisplayName("fraction never goes negative, even if render time is before prevTick")
	void fractionNeverNegative() {
		assertEquals(0.0, StructureMotion.fraction(100, 106, 50, 0.0), EPS);
	}

	@Test
	@DisplayName("fraction caps extrapolation at MAX_EXTRAPOLATION instead of growing unbounded")
	void fractionCapsExtrapolation() {
		double farPast = StructureMotion.fraction(100, 106, 10_000, 0.0);
		assertEquals(StructureMotion.MAX_EXTRAPOLATION, farPast, EPS);
		assertTrue(StructureMotion.MAX_EXTRAPOLATION > 1.0, "the cap must allow some coast past curr, not none");
	}
}

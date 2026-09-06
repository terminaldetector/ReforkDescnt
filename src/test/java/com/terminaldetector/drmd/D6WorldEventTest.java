package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6EventVisibility;
import com.terminaldetector.drmd.d6.D6EventVisibility.Band;
import com.terminaldetector.drmd.d6.D6WorldEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * World events, and the two things that make them affordable: state as a closed form in the clock,
 * and observability decided by what the event is rather than by a radius.
 *
 * <p>The scenarios are the design's own — six lights crossing the sky, the swarm's eight phases —
 * because the point of the model is that those come out of it without being written into it.
 */
class D6WorldEventTest {

	private static final double EPS = 1e-9;
	private static final Vec3 STILL = new Vec3(0, 0, 0);

	private static D6WorldEvent event(long start, int... phases) {
		return new D6WorldEvent(1, "test", STILL, STILL, start, phases, 5, 0);
	}

	// ---- visibility -------------------------------------------------------------------------

	@Test
	@DisplayName("the two laws cross: a drone is a shape before it is a light, an explosion after")
	void reachesCross() {
		// Angular size is linear in distance, brightness quadratic, so which reaches further depends
		// on the thing rather than on a rule about it.
		assertEquals(500, D6EventVisibility.shapeReach(5), EPS, "a five-block craft as a shape");
		assertEquals(1000, D6EventVisibility.glowReach(100, false), EPS, "its running lights at night");

		assertEquals(3000, D6EventVisibility.shapeReach(30), EPS, "a thirty-block blast as a shape");
		assertTrue(D6EventVisibility.glowReach(67_000, false) > 8_192,
				"and its light past the edge of the drawn world");
	}

	@Test
	@DisplayName("six lights crossing the sky at night: lights far, drones near — the design's example")
	void sixLightsAtNight() {
		// Nothing here encodes "show lights at a kilometre"; it falls out of size 5 and brightness 100.
		assertEquals(Band.DETAIL, D6EventVisibility.band(40, 5, 100, false), "40 blocks");
		assertEquals(Band.SHAPE, D6EventVisibility.band(300, 5, 100, false), "300 blocks");
		assertEquals(Band.GLOW, D6EventVisibility.band(800, 5, 100, false), "800 blocks");
		assertEquals(Band.NONE, D6EventVisibility.band(1200, 5, 100, false), "1200 blocks");
	}

	@Test
	@DisplayName("the same flight by day is simply not there at a kilometre")
	void daylightCostsTenTimesTheReach() {
		assertEquals(Band.NONE, D6EventVisibility.band(800, 5, 100, true), "800 blocks by day");
		// A hundred times the brightness for the same reach is ten times less reach.
		assertEquals(10.0,
				D6EventVisibility.glowReach(100, false) / D6EventVisibility.glowReach(100, true),
				EPS, "night reach over day reach");
		// Shape does not care about the sun: a silhouette is a silhouette.
		assertEquals(Band.SHAPE, D6EventVisibility.band(300, 5, 100, true), "300 blocks by day");
	}

	@Test
	@DisplayName("something that does not glow is seen by its shape or not at all")
	void unlitThings() {
		assertEquals(0, D6EventVisibility.glowReach(0, false), EPS, "no light, no reach");
		assertEquals(Band.SHAPE, D6EventVisibility.band(400, 5, 0, false), "within its shape reach");
		assertEquals(Band.NONE, D6EventVisibility.band(600, 5, 0, false), "past it");
	}

	@Test
	@DisplayName("nothing is observable past the edge of the drawn world, however bright")
	void beyondTheDrawnWorld() {
		// A hundred blocks across reads as a shape out to ten thousand, so the cut is the cap and
		// not the reach — which is what this is checking.
		double past = D6EventVisibility.MAX_OBSERVED_DISTANCE + 1;
		assertEquals(Band.NONE, D6EventVisibility.band(past, 100, 1e12, false),
				"an enormous bright thing, too far to appear on anything");
		assertEquals(Band.SHAPE, D6EventVisibility.band(D6EventVisibility.MAX_OBSERVED_DISTANCE,
				100, 1e12, false), "and the same thing just inside");
	}

	@Test
	@DisplayName("bands are ordered, so seeing a shape implies seeing the light")
	void bandsAreOrdered() {
		assertTrue(Band.DETAIL.atLeast(Band.SHAPE), "detail is more than shape");
		assertTrue(Band.SHAPE.atLeast(Band.SHAPE), "and at least itself");
		assertFalse(Band.GLOW.atLeast(Band.SHAPE), "a light is less than a shape");
		assertFalse(Band.NONE.atLeast(Band.GLOW), "nothing is least");
	}

	// ---- phases -----------------------------------------------------------------------------

	@Test
	@DisplayName("the phase is a function of the clock, so an unwatched event is still correct")
	void phaseIsAFunctionOfTime() {
		// Nothing between these calls advances anything: the event is never ticked.
		D6WorldEvent e = event(100, 200, 0, 20, 300);

		assertEquals(D6WorldEvent.NOT_STARTED, e.phaseAt(99), "before it begins");
		assertEquals(0, e.phaseAt(100), "on the first tick");
		assertEquals(0, e.phaseAt(299), "last tick of a 200-tick phase");
		assertEquals(2, e.phaseAt(300), "and straight past the zero-length one");
		assertEquals(3, e.phaseAt(320), "into the last phase");
		assertEquals(4, e.phaseAt(620), "over, reported as the phase count");
		assertEquals(4, e.phaseAt(621), "and staying over");
	}

	@Test
	@DisplayName("a zero-length phase marks a moment without ever being current")
	void zeroLengthPhases() {
		D6WorldEvent e = event(100, 200, 0, 20, 300);

		// Phase 1 begins and ends on tick 300, so it is a boundary rather than a duration — which is
		// how "the scout sends the signal" is written when the signal takes no time.
		assertEquals(300, e.phaseStartTick(1), "phase 1 begins");
		assertEquals(300, e.phaseStartTick(2), "and phase 2 begins on the same tick");
		for (long t = 99; t <= 621; t++) {
			assertTrue(e.phaseAt(t) != 1, "phase 1 is never current, including at t=" + t);
		}
	}

	@Test
	@DisplayName("progress runs 0 to 1 inside a phase and is pinned outside the event")
	void progressAcrossThePhases() {
		D6WorldEvent e = event(100, 200, 0, 20, 300);

		assertEquals(0.0, e.phaseProgress(99), EPS, "before it starts");
		assertEquals(0.0, e.phaseProgress(100), EPS, "at the start");
		assertEquals(0.5, e.phaseProgress(200), EPS, "halfway through a 200-tick phase");
		assertEquals(0.995, e.phaseProgress(299), EPS, "one tick from its end");
		assertEquals(0.0, e.phaseProgress(300), EPS, "and reset by the next phase");
		assertEquals(0.5, e.phaseProgress(310), EPS, "halfway through a 20-tick phase");
		assertEquals(1.0, e.phaseProgress(620), EPS, "once it is over");
	}

	@Test
	@DisplayName("the swarm's eight phases are eight numbers and nothing else")
	void theSwarmIsEightNumbers() {
		// Scout, contact, signal, gather, intercept, attack, destroy, withdraw.
		D6WorldEvent swarm = new D6WorldEvent(7, "swarm", STILL, STILL, 0,
				new int[] { 200, 40, 20, 300, 100, 400, 60, 240 }, 40, 200);

		assertEquals(8, swarm.phaseCount(), "phases");
		assertEquals(1360, swarm.duration(), "ticks, which is 68 seconds");
		assertEquals(1360, swarm.endTick(), "started at zero");
		assertEquals(560, swarm.phaseStartTick(4), "intercept begins after 200+40+20+300");
		assertTrue(swarm.isFinished(1360), "over on the tick after its last");
		assertFalse(swarm.isFinished(1359), "not before");
	}

	// ---- position ---------------------------------------------------------------------------

	@Test
	@DisplayName("position is a multiply and an add, clamped at both ends")
	void positionIsClosedForm() {
		D6WorldEvent e = new D6WorldEvent(2, "pass", new Vec3(0, 200, 3000), new Vec3(0, 0, -10),
				0, new int[] { 400 }, 5, 100);

		assertEquals(3000, e.positionAt(-50).z(), EPS, "before it starts, still at its origin");
		assertEquals(2000, e.positionAt(100).z(), EPS, "a hundred ticks in");
		// It stops where it ended rather than flying on: what is left behind is somewhere.
		assertEquals(-1000, e.positionAt(400).z(), EPS, "at its last tick");
		assertEquals(-1000, e.positionAt(9999).z(), EPS, "and long after");
		assertEquals(200, e.positionAt(100).y(), EPS, "no drift on an axis with no velocity");
	}

	@Test
	@DisplayName("a passing flight moves through the bands on its own, with nothing ticked")
	void bandsFollowTheFlight() {
		D6WorldEvent lights = new D6WorldEvent(3, "lights", new Vec3(0, 200, 3000), new Vec3(0, 0, -10),
				0, new int[] { 400 }, 5, 100);
		Vec3 observer = new Vec3(0, 0, 0);

		// Distances are sqrt(200^2 + z^2): 3007, 2010, 922, 447.
		assertEquals(Band.NONE, lights.bandFor(observer, 0, false), "still empty sky");
		assertEquals(Band.NONE, lights.bandFor(observer, 100, false), "two kilometres out");
		assertEquals(Band.GLOW, lights.bandFor(observer, 210, false), "six lights");
		assertEquals(Band.SHAPE, lights.bandFor(observer, 260, false), "six drones");
		// Two hundred blocks up is never close enough to read what they are, which is the point.
		assertEquals(Band.SHAPE, lights.bandFor(observer, 290, false), "overhead and still only shapes");
	}

	@Test
	@DisplayName("entities are needed exactly where a light stops being enough")
	void entitiesFollowTheShapeBand() {
		D6WorldEvent lights = new D6WorldEvent(3, "lights", new Vec3(0, 200, 3000), new Vec3(0, 0, -10),
				0, new int[] { 400 }, 5, 100);
		Vec3 observer = new Vec3(0, 0, 0);

		assertFalse(lights.needsEntities(observer, 210, false), "a glow the client can draw itself");
		assertTrue(lights.needsEntities(observer, 260, false), "form and motion, which it cannot");
	}

	// ---- construction -----------------------------------------------------------------------

	@Test
	@DisplayName("an event without phases, or with a negative one, is refused")
	void badPhasesAreRefused() {
		assertThrows(IllegalArgumentException.class,
				() -> new D6WorldEvent(1, "x", STILL, STILL, 0, new int[0], 1, 0), "no phases");
		assertThrows(IllegalArgumentException.class,
				() -> new D6WorldEvent(1, "x", STILL, STILL, 0, null, 1, 0), "no array at all");
		assertThrows(IllegalArgumentException.class,
				() -> new D6WorldEvent(1, "x", STILL, STILL, 0, new int[] { 10, -1 }, 1, 0), "negative");
	}

	@Test
	@DisplayName("the phase array is copied, so an event's history cannot be rewritten behind it")
	void phasesAreCopied() {
		int[] phases = { 100, 200 };
		D6WorldEvent e = new D6WorldEvent(1, "x", STILL, STILL, 0, phases, 1, 0);

		phases[0] = 999;

		assertEquals(100, e.phaseDuration(0), "still the duration it was built with");
		assertEquals(300, e.duration(), "and the total is unchanged");
	}
}

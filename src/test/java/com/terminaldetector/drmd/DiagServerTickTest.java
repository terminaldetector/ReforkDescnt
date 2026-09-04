package com.terminaldetector.drmd;

import com.terminaldetector.drmd.diag.DiagServerTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server-tick accounting, exercised without a server.
 *
 * <p>Worth testing rather than trusting: this is the measurement a decision gets made from — whether
 * DRMD is the reason a tick took 300ms, or a passenger in someone else's stall — and a bucket that
 * double-counts or a period that silently includes world load would send the next round of work at
 * the wrong system entirely. Every duration here is handed in rather than read off the clock, so the
 * numbers are arithmetic and not timing luck.
 */
class DiagServerTickTest {

	private static final long MS = 1_000_000L;

	@BeforeEach
	void reset() {
		DiagServerTick.clear();
	}

	private static Map<String, DiagServerTick.Area> byName() {
		return DiagServerTick.areas().stream()
				.collect(Collectors.toMap(DiagServerTick.Area::name, Function.identity()));
	}

	@Test
	@DisplayName("a tick's work is charged to the tick, and split by the system that did it")
	void workIsSplitByArea() {
		DiagServerTick.charge("a", 3 * MS);
		DiagServerTick.charge("b", 1 * MS);
		DiagServerTick.rollTick(1_000_000_000L);
		DiagServerTick.charge("a", 1 * MS);
		DiagServerTick.rollTick(1_050_000_000L);

		assertEquals(2, DiagServerTick.ticks());
		// 5ms of work over two ticks.
		assertEquals(2500, DiagServerTick.averageDrmdMicros());

		Map<String, DiagServerTick.Area> areas = byName();
		assertEquals(2000, areas.get("a").totalMicros(), "per-tick average for a");
		assertEquals(3000, areas.get("a").worstMicros(), "worst single call for a");
		assertEquals(500, areas.get("b").totalMicros());
		assertEquals(1000, areas.get("b").worstMicros());
	}

	@Test
	@DisplayName("the worst tick remembers which system dominated it, which is the whole point")
	void worstTickNamesItsCulprit() {
		DiagServerTick.charge("small", 1 * MS);
		DiagServerTick.rollTick(1_000_000_000L);
		DiagServerTick.charge("small", 1 * MS);
		DiagServerTick.charge("huge", 40 * MS);
		DiagServerTick.rollTick(1_050_000_000L);

		assertEquals(41000, DiagServerTick.worstDrmdMicros());
		assertEquals("huge", DiagServerTick.worstDrmdArea(),
				"a worst tick that cannot say what made it worst is a number with nowhere to go");
	}

	@Test
	@DisplayName("world load does not become 'worst tick' forever")
	void warmupTicksAreNotMeasured() {
		// A 9-second first tick, which is what generating the spawn area actually costs. If this
		// counted, every later stall would hide behind it.
		DiagServerTick.rollTick(1_000_000_000L);
		DiagServerTick.rollTick(10_000_000_000L);
		assertEquals(0, DiagServerTick.measuredPeriods(),
				"warm-up ticks were measured, so 'worst tick' now means startup and nothing else");
	}

	@Test
	@DisplayName("a server that keeps up reads 50ms a tick, because it sleeps out the rest")
	void healthyServerReadsFiftyMillis() {
		long t = 1_000_000_000L;
		for (int i = 0; i < 60; i++) {
			DiagServerTick.rollTick(t);
			t += 50 * MS;
		}

		assertEquals(20, DiagServerTick.measuredPeriods(), "40 warm-up ticks, 60 rolled, 20 measured");
		assertEquals(50_000, DiagServerTick.averagePeriodMicros());
		assertEquals(50_000, DiagServerTick.worstPeriodMicros());
		assertEquals(0, DiagServerTick.slowTicks(), "50ms is not over 50ms");
	}

	@Test
	@DisplayName("one stall is counted once and shows up in the worst, not averaged away")
	void oneStallIsVisible() {
		long t = 1_000_000_000L;
		for (int i = 0; i < 60; i++) {
			DiagServerTick.rollTick(t);
			t += 50 * MS;
		}
		// 20 healthy periods of 50ms, then one of 350ms: 1350ms over 21.
		t += 300 * MS;
		DiagServerTick.rollTick(t);

		assertEquals(21, DiagServerTick.measuredPeriods());
		assertEquals(350_000, DiagServerTick.worstPeriodMicros());
		assertEquals(1, DiagServerTick.slowTicks());
		assertEquals(1, DiagServerTick.stalledTicks());
		// The average alone would read 64ms and look like a mild problem; the worst is what names it.
		assertEquals(64_285, DiagServerTick.averagePeriodMicros());
	}

	@Test
	@DisplayName("a clock that ran backwards is discarded rather than recorded as a huge negative")
	void negativeDurationsAreIgnored() {
		DiagServerTick.charge("a", -5 * MS);
		DiagServerTick.rollTick(1_000_000_000L);
		assertEquals(0, DiagServerTick.averageDrmdMicros());
		assertTrue(DiagServerTick.areas().isEmpty(), "a bad measurement created a bucket anyway");
	}

	@Test
	@DisplayName("clear really clears, so one reproduction can be watched without the noise before it")
	void clearResetsEverything() {
		DiagServerTick.charge("a", 5 * MS);
		DiagServerTick.rollTick(1_000_000_000L);
		DiagServerTick.clear();

		List<DiagServerTick.Area> areas = DiagServerTick.areas();
		assertTrue(areas.isEmpty(), "areas survived a clear: " + areas);
		assertEquals(0, DiagServerTick.ticks());
		assertEquals(0, DiagServerTick.worstDrmdMicros());
		assertEquals(0, DiagServerTick.measuredPeriods());
	}
}

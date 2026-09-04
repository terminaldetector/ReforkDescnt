package com.terminaldetector.drmd.diag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the server tick actually goes — DRMD's own share of it, split by system, measured rather
 * than argued about.
 *
 * <p><b>Why this exists.</b> A log full of "Can't keep up! Running 83841ms or 1676 ticks behind" says
 * the server is drowning and nothing at all about who is holding it under. DRMD's column fill was the
 * obvious suspect, and it was the wrong one: it is capped at 6ms of a 50ms tick and cannot by itself
 * put a server 83 seconds behind. Without a number for the other side of that comparison the next step
 * is a guess, and a guess costs a whole play session to test.
 *
 * <p><b>What each number means.</b> The tick <em>period</em> is measured here rather than asked of
 * vanilla, and it reads differently depending on the server's health: a server keeping up sleeps out
 * the remainder of every tick, so the period sits at 50ms exactly; a server behind runs catch-up ticks
 * back to back with no sleep at all, so the period becomes the true cost of one tick. A worst period of
 * 300ms therefore means one tick genuinely took 300ms — and set against DRMD's own share for the same
 * tick, it says immediately whether this mod is the problem or a bystander.
 *
 * <p>Deliberately not a profiler. Five buckets, a nanosecond clock, and no allocation on the hot path:
 * the point is to name which half of the mod to look at, after which the ordinary tools apply. Two
 * timers costing a few dozen nanoseconds each against a tick budget of fifty million is a trade worth
 * making permanently rather than behind a flag that would be off in exactly the session that needed it.
 */
public final class DiagServerTick {
	private DiagServerTick() {}

	/** A tick is 50ms; a server that keeps up sleeps out the rest, so a healthy period reads as 50. */
	public static final long TICK_MILLIS = 50;

	/**
	 * Ticks ignored before the period is believed. World load, terrain generation for the spawn area
	 * and mod setup all land in the first couple of seconds and would otherwise own "worst tick"
	 * forever, hiding every real stall behind a startup cost nobody can act on.
	 */
	private static final int WARMUP_TICKS = 40;

	private static final Object LOCK = new Object();

	private static final Map<String, long[]> AREAS = new LinkedHashMap<>();
	/** Accumulating now, closed and rolled into the totals by {@link #endOfTick}. */
	private static long currentTickNanos;
	private static String currentWorstArea;
	private static long currentWorstAreaNanos;

	private static long ticks;
	private static long drmdTotalNanos;
	private static long drmdWorstNanos;
	private static String drmdWorstArea = "—";

	private static long lastTickNanos;
	private static long periodTotalNanos;
	private static long periodWorstNanos;
	private static volatile long lastPeriodNanos;
	private static long periodsCounted;
	private static long slowTicks;
	private static long stalledTicks;

	private static volatile int viewDistance;
	private static volatile int simulationDistance;
	private static volatile int loadedChunks;

	/**
	 * The settings the timings above have to be read against.
	 *
	 * <p>Sampled by the caller rather than looked up here, which is what keeps this class free of
	 * Minecraft entirely — the same split the rest of this package uses, and the reason any of it can
	 * be tested without a game bootstrap. A server 1676 ticks behind at simulation distance 31 is a
	 * different finding from the same server behind at 10, and neither number is visible in-game.
	 */
	public static void sample(int viewDistanceChunks, int simulationDistanceChunks, int chunksLoaded) {
		viewDistance = viewDistanceChunks;
		simulationDistance = simulationDistanceChunks;
		loadedChunks = chunksLoaded;
	}

	/** Start timing one system. Pair with {@link #end}; the value is opaque. */
	public static long begin() {
		return System.nanoTime();
	}

	/**
	 * Charge the time since {@code startNanos} to {@code area}.
	 *
	 * @param area a stable bucket name — {@code "worldgen.column"}, {@code "systems.players"}. Stable
	 *             for the same reason {@link DiagTrace#count} wants stable keys: a name built from
	 *             changing values makes one bucket per value and measures nothing.
	 */
	public static void end(String area, long startNanos) {
		charge(area, System.nanoTime() - startNanos);
	}

	/**
	 * Charge {@code nanos} to {@code area} directly.
	 *
	 * <p>The seam {@link #end} is built on, and the one tests measure through: a duration handed in is
	 * a duration that can be asserted about, where one read off the clock is whatever the machine felt
	 * like that microsecond.
	 */
	public static void charge(String area, long nanos) {
		long took = nanos;
		if (took < 0) return; // a clock that went backwards is not a measurement
		String safe = area == null ? "?" : area;
		synchronized (LOCK) {
			long[] slot = AREAS.computeIfAbsent(safe, k -> new long[2]);
			slot[0] += took;
			if (took > slot[1]) slot[1] = took;
			currentTickNanos += took;
			if (took > currentWorstAreaNanos) {
				currentWorstAreaNanos = took;
				currentWorstArea = safe;
			}
		}
	}

	/**
	 * Close the tick that just ended at {@code now} and start the next.
	 *
	 * <p>Called from a tick handler, and deliberately indifferent to where in the order it runs.
	 * Fabric fires handlers in registration order, which depends on class-initialisation order and is
	 * not something a diagnostic should depend on; whichever handler this lands between, every bucket
	 * closed here holds exactly one tick's work, offset by at most one tick boundary. An average over
	 * thousands of ticks does not notice that, and a worst-tick figure lands on the tick next door at
	 * worst.
	 *
	 * <p>Takes the clock rather than reading it, for the same reason {@link #charge} takes a duration:
	 * a number handed in is a number a test can assert about.
	 */
	public static void rollTick(long now) {
		synchronized (LOCK) {
			ticks++;
			drmdTotalNanos += currentTickNanos;
			if (currentTickNanos > drmdWorstNanos) {
				drmdWorstNanos = currentTickNanos;
				drmdWorstArea = currentWorstArea == null ? "—" : currentWorstArea;
			}
			currentTickNanos = 0;
			currentWorstAreaNanos = 0;
			currentWorstArea = null;

			if (lastTickNanos != 0 && ticks > WARMUP_TICKS) {
				long period = now - lastTickNanos;
				periodTotalNanos += period;
				periodsCounted++;
				if (period > periodWorstNanos) periodWorstNanos = period;
				lastPeriodNanos = period;
				long millis = period / 1_000_000L;
				if (millis > TICK_MILLIS) slowTicks++;
				if (millis > TICK_MILLIS * 2) stalledTicks++;
			}
			lastTickNanos = now;
		}
	}

	/** One bucket's total and worst, in microseconds. */
	public record Area(String name, long totalMicros, long worstMicros) {}

	public static long ticks() {
		synchronized (LOCK) {
			return ticks;
		}
	}

	/** DRMD's own average share of a tick, in microseconds — compare against {@link #averagePeriodMicros}. */
	public static long averageDrmdMicros() {
		synchronized (LOCK) {
			return ticks == 0 ? 0 : drmdTotalNanos / ticks / 1000;
		}
	}

	public static long worstDrmdMicros() {
		synchronized (LOCK) {
			return drmdWorstNanos / 1000;
		}
	}

	public static String worstDrmdArea() {
		synchronized (LOCK) {
			return drmdWorstArea;
		}
	}

	/** The measured gap between ticks — 50000µs on a server that is keeping up. */
	public static long averagePeriodMicros() {
		synchronized (LOCK) {
			return periodsCounted == 0 ? 0 : periodTotalNanos / periodsCounted / 1000;
		}
	}

	/**
	 * How long the tick before this one actually took, in microseconds — 0 until measured.
	 *
	 * <p>Read by work that can choose to stand aside. A tick handler registered after the one that
	 * calls {@link #rollTick} sees the period that ended as this tick began, which is the freshest
	 * honest answer to "is the server drowning right now" available from inside a tick.
	 */
	public static long lastPeriodMicros() {
		return lastPeriodNanos / 1000;
	}

	public static long worstPeriodMicros() {
		synchronized (LOCK) {
			return periodWorstNanos / 1000;
		}
	}

	/** Ticks that took longer than 50ms, and those that took longer than 100ms. */
	public static long slowTicks() {
		synchronized (LOCK) {
			return slowTicks;
		}
	}

	public static long stalledTicks() {
		synchronized (LOCK) {
			return stalledTicks;
		}
	}

	public static long measuredPeriods() {
		synchronized (LOCK) {
			return periodsCounted;
		}
	}

	public static int viewDistance() {
		return viewDistance;
	}

	public static int simulationDistance() {
		return simulationDistance;
	}

	public static int loadedChunks() {
		return loadedChunks;
	}

	/** Every bucket, in the order it was first timed, worst-costing first is left to the caller. */
	public static List<Area> areas() {
		synchronized (LOCK) {
			List<Area> out = new ArrayList<>(AREAS.size());
			long divisor = Math.max(1, ticks);
			for (Map.Entry<String, long[]> e : AREAS.entrySet()) {
				out.add(new Area(e.getKey(), e.getValue()[0] / divisor / 1000, e.getValue()[1] / 1000));
			}
			return out;
		}
	}

	/** Drop everything — for tests, and for watching one reproduction without the noise before it. */
	public static void clear() {
		synchronized (LOCK) {
			AREAS.clear();
			currentTickNanos = 0;
			currentWorstArea = null;
			currentWorstAreaNanos = 0;
			ticks = 0;
			drmdTotalNanos = 0;
			drmdWorstNanos = 0;
			drmdWorstArea = "—";
			lastTickNanos = 0;
			periodTotalNanos = 0;
			periodWorstNanos = 0;
			lastPeriodNanos = 0;
			periodsCounted = 0;
			slowTicks = 0;
			stalledTicks = 0;
		}
	}
}

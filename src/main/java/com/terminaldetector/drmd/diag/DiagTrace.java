package com.terminaldetector.drmd.diag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What DRMD actually did, in order — the sequence of actions leading up to whatever went wrong.
 *
 * <p><b>Why this exists beside {@link DiagProblems}.</b> The problem log answers "what broke"; it
 * cannot answer "what was happening when it broke", and that is usually the question. A portal that
 * carries nobody means one thing if the link was formed a minute earlier and another if the link was
 * never formed at all — and the two are indistinguishable from a snapshot of the end state. This is
 * the record that tells them apart.
 *
 * <p><b>Two channels, because actions differ in kind.</b> Something that happens a handful of times —
 * a link forming, a traveller carried, a mesh rebuilt — is worth a line with a timestamp. Something
 * that happens thousands of times — a chunk row filled, a frame drawn — is worth a number, and writing
 * it as lines would push every useful event out of the buffer. So {@link #record} keeps the story and
 * {@link #count} keeps the volume, and the report carries both.
 *
 * <p>Bounded and lock-guarded, for the same reasons as the problem log: it is written from the render
 * thread, the client tick, the server thread and network handlers, and a diagnostic that loses entries
 * or grows without limit is worse than the nanoseconds the lock costs.
 *
 * <p>Unlike the problem log, repeats are <em>not</em> collapsed. Two travellers carried through the
 * same portal are two events, and knowing there were two is the whole point; collapsing them would
 * turn the record of what happened back into a summary of what is true now.
 */
public final class DiagTrace {
	private DiagTrace() {}

	/**
	 * Events kept. Enough to cover several minutes of ordinary play at the rate these are recorded, so
	 * a report taken shortly after a problem still contains what led to it.
	 */
	private static final int MAX_EVENTS = 600;

	/** One thing that happened, and when. */
	public record Event(long millis, String area, String message) {}

	private static final Deque<Event> EVENTS = new ArrayDeque<>();
	private static final Map<String, Integer> COUNTERS = new LinkedHashMap<>();
	private static final Object LOCK = new Object();

	/**
	 * Record something that happened, in order.
	 *
	 * <p>For actions, not for states: "linked 12,64,-40 to 12,64,-60" belongs here, "is linked" does
	 * not — the report's own sections carry current state, and duplicating it here would spend the
	 * buffer on things already known.
	 *
	 * @param area which system acted — the same names the problem log uses, so the two read together.
	 */
	public static void record(String area, String message) {
		Event event = new Event(System.currentTimeMillis(),
				area == null ? "?" : area, message == null ? "(no message)" : message);
		synchronized (LOCK) {
			EVENTS.addLast(event);
			while (EVENTS.size() > MAX_EVENTS) EVENTS.removeFirst();
		}
	}

	/**
	 * Count something too frequent to write down.
	 *
	 * <p>A frame, a filled chunk, a carried entity in a busy world. The count answers "did this happen
	 * at all, and roughly how much", which for high-frequency work is all a reader can use anyway.
	 *
	 * @param key a stable name — {@code "portal.carried"}, {@code "horizon.rebuild"}. Stable because
	 *            a key built from changing values would make one counter per value and defeat the point.
	 */
	public static void count(String key) {
		String safe = key == null ? "?" : key;
		synchronized (LOCK) {
			COUNTERS.merge(safe, 1, Integer::sum);
		}
	}

	/** Everything recorded, oldest first — read as a story, unlike the problem log. */
	public static List<Event> events() {
		synchronized (LOCK) {
			return new ArrayList<>(EVENTS);
		}
	}

	/** Every counter, in the order its key was first seen. */
	public static Map<String, Integer> counters() {
		synchronized (LOCK) {
			return new LinkedHashMap<>(COUNTERS);
		}
	}

	public static int size() {
		synchronized (LOCK) {
			return EVENTS.size();
		}
	}

	/** Drop everything — for tests, and for watching one reproduction without the noise before it. */
	public static void clear() {
		synchronized (LOCK) {
			EVENTS.clear();
			COUNTERS.clear();
		}
	}
}

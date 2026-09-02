package com.terminaldetector.drmd.diag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The last things DRMD noticed going wrong, kept so the diagnostics report can say what happened
 * rather than what is happening now.
 *
 * <p><b>Why a buffer and not just logging.</b> A log line is written when the problem happens and read
 * hours later, in a file with tens of thousands of other lines, by someone who does not know which of
 * them are DRMD's. This is the same information, already filtered to this mod and already ordered, so
 * the report can carry it and one file answers the question.
 *
 * <p><b>Repeats are counted, not appended.</b> Most of this mod's failures are per-tick or per-frame:
 * a portal that cannot find its partner fails twenty times a second. Twenty thousand identical lines
 * would push out everything else and say nothing the first one did not, so an identical message
 * increments a count and moves to the front instead. What survives is the set of distinct problems,
 * which is what anyone reading actually wants.
 *
 * <p>Thread-safe by a plain lock: problems are recorded from the render thread, the client tick, the
 * server thread and network handlers, and a lost or torn entry in a diagnostic is worse than the
 * nanoseconds the lock costs on a path that only runs when something is already wrong.
 */
public final class DiagProblems {
	private DiagProblems() {}

	/** Distinct problems kept. Past this the oldest is dropped, since the newest are the ones in play. */
	private static final int MAX_ENTRIES = 40;

	/** One distinct problem, with when it was first and last seen and how often. */
	public record Entry(String area, String message, long firstSeenMillis, long lastSeenMillis, int count) {}

	private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
	private static final Object LOCK = new Object();

	/**
	 * Record that something went wrong.
	 *
	 * @param area    which system noticed — {@code "portal"}, {@code "horizon"}, {@code "worldgen"}.
	 *                Grouped by area so a reader can tell at a glance whether one subsystem is
	 *                responsible for everything in the list.
	 * @param message what happened, as one short line. Include the values that made it happen; a
	 *                message with no numbers in it usually cannot be acted on.
	 */
	public static void record(String area, String message) {
		String safeArea = area == null ? "?" : area;
		String safeMessage = message == null ? "(no message)" : message;
		long now = System.currentTimeMillis();
		synchronized (LOCK) {
			// Found first, modified after. Removing inside the loop happens to be safe here because the
			// iterator is abandoned immediately, but it reads as the bug it is one edit away from being.
			Entry existing = null;
			for (Entry entry : ENTRIES) {
				if (entry.area().equals(safeArea) && entry.message().equals(safeMessage)) {
					existing = entry;
					break;
				}
			}
			if (existing != null) {
				ENTRIES.remove(existing);
				ENTRIES.addFirst(new Entry(safeArea, safeMessage,
						existing.firstSeenMillis(), now, existing.count() + 1));
				return;
			}
			ENTRIES.addFirst(new Entry(safeArea, safeMessage, now, now, 1));
			while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.removeLast();
		}
	}

	/** Everything recorded, most recently seen first. */
	public static List<Entry> snapshot() {
		synchronized (LOCK) {
			return new ArrayList<>(ENTRIES);
		}
	}

	/** How many distinct problems are held. */
	public static int size() {
		synchronized (LOCK) {
			return ENTRIES.size();
		}
	}

	/** Drop everything — for tests, and for a report that wants to watch one reproduction cleanly. */
	public static void clear() {
		synchronized (LOCK) {
			ENTRIES.clear();
		}
	}
}

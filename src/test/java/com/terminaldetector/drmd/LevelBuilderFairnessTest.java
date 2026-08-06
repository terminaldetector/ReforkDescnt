package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "World generation locks onto a small area" traced to {@code LevelBuilder.drain}'s scheduling policy,
 * not to the write budget itself. A pilot flying forward keeps appending newly-approached chunks to
 * the <em>tail</em> of the queue ({@code enqueue} uses {@code QUEUE.add}), but the drain loop re-queued
 * an unfinished job with {@code QUEUE.addFirst} — putting it straight back at the <em>head</em>. That
 * job then wins every following poll too, for as long as it takes to run all four build phases to
 * completion, before the loop ever reaches anything behind it. The chunks queued first (wherever the
 * pilot started) finish completely; everything queued afterward (wherever they have flown to since)
 * waits behind the entire backlog, one job at a time — which is exactly a small, fully-built area
 * around the start point and nothing finishing ahead of the pilot.
 *
 * <p>The real {@code drain}/{@code step}/{@code Job} are private and need a live {@code ServerWorld},
 * so this mirrors only the part of the bug that is pure scheduling: given a queue, a per-tick write
 * budget, and a per-poll cap on how much of one job a single {@code step} call advances (mirroring
 * {@code MANTLE_ROWS_PER_STEP} bounding phase 0), how many ticks pass before a job appended after an
 * existing backlog gets touched at all under each re-queue policy.
 */
class LevelBuilderFairnessTest {
	private static final class Job {
		final String name;
		int remaining;
		Job(String name, int remaining) { this.name = name; this.remaining = remaining; }
	}

	/** Mirrors one drain() tick: poll the head, advance it by at most stepSize, re-queue per policy. */
	private static void tick(Deque<Job> queue, int budget, int stepSize, boolean addFirst) {
		while (budget > 0 && !queue.isEmpty()) {
			Job job = queue.poll();
			int work = Math.min(stepSize, Math.min(job.remaining, budget));
			job.remaining -= work;
			budget -= work;
			if (job.remaining > 0) {
				if (addFirst) queue.addFirst(job); else queue.addLast(job);
			}
		}
	}

	/** Ticks until `target` first loses any remaining work, i.e. gets touched at all. -1 if never within the cap. */
	private static int ticksUntilFirstTouched(Deque<Job> queue, Job target, int budget, int stepSize,
											   boolean addFirst, int tickCap) {
		int startRemaining = target.remaining;
		for (int t = 1; t <= tickCap; t++) {
			tick(queue, budget, stepSize, addFirst);
			if (target.remaining < startRemaining) return t;
		}
		return -1;
	}

	/** Same starting queue and budget under both policies — only the re-queue order differs. */
	private static final class Backlog {
		final Deque<Job> queue = new ArrayDeque<>();
		final Job late;
		Backlog() {
			for (int i = 0; i < 5; i++) queue.add(new Job("backlog" + i, 1000));
			late = new Job("late-ahead-of-pilot", 1000);
			queue.add(late);
		}
	}

	@Test
	@DisplayName("pre-fix (addFirst) makes a newly-queued chunk wait far longer than fixed (addLast) does")
	void addFirstDelaysNewlyQueuedWorkMuchLongerThanAddLast() {
		Backlog fifo = new Backlog();
		int fifoTicks = ticksUntilFirstTouched(fifo.queue, fifo.late, 300, 100, true, 100);

		Backlog robin = new Backlog();
		int robinTicks = ticksUntilFirstTouched(robin.queue, robin.late, 300, 100, false, 100);

		assertTrue(robinTicks > 0 && robinTicks <= 2,
				"with addLast every in-flight job gets a slice of the budget each pass, so a job sixth in"
						+ " line should be touched on tick 2 at the latest; got " + robinTicks);
		assertTrue(fifoTicks >= robinTicks * 5,
				"with addFirst the newly-queued chunk waits out nearly the whole backlog first (got "
						+ fifoTicks + " ticks) — at least 5x longer than round-robin's " + robinTicks
						+ "; this gap, not the exact tick count, is the bug: everything queued after an"
						+ " existing backlog sat untouched for a length of time that scales with how much"
						+ " was already queued, which for a moving pilot means everything ahead of them");
	}

	@Test
	@DisplayName("fixed: total ticks to drain the whole backlog is unchanged — this is fairness, not free capacity")
	void addLastDoesNotChangeTotalThroughput() {
		Deque<Job> fifo = new ArrayDeque<>();
		Deque<Job> robin = new ArrayDeque<>();
		for (int i = 0; i < 6; i++) {
			fifo.add(new Job("j" + i, 1000));
			robin.add(new Job("j" + i, 1000));
		}
		int ticksFifo = 0, ticksRobin = 0;
		while (fifo.stream().anyMatch(j -> j.remaining > 0)) { tick(fifo, 300, 100, true); ticksFifo++; }
		while (robin.stream().anyMatch(j -> j.remaining > 0)) { tick(robin, 300, 100, false); ticksRobin++; }
		assertEquals(ticksFifo, ticksRobin,
				"re-queue order changes who goes first, not how much total work the same budget buys");
	}
}

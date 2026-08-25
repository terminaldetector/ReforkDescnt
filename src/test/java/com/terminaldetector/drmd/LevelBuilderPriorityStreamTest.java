package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reported again after {@code LevelBuilderFairnessTest}'s {@code addLast} fix shipped, this time as
 * chunks near the pilot "visibly growing" rather than never starting at all. Round-robin fairness
 * stops any one job from starving forever, but under wide enough concurrent load it means
 * <em>every</em> queued job crawls uniformly instead — including the handful right around the pilot.
 * {@code MantleStream.STREAM_CHUNKS = 6} around one digger is a 13×13, 169-chunk neighbourhood, and a
 * pilot exploring fresh ground near the Core re-fills a batch that size every time they round a
 * corner.
 *
 * <p>{@code LevelBuilder.drain}'s fix splits that neighbourhood into a tight
 * {@code MantleStream.STREAM_CHUNKS_NEAR} priority ring (5×5 = 25 chunks at the shipped radius of 2),
 * drained with the full per-tick budget, and a wider prefetch ring out to {@code STREAM_CHUNKS}
 * (169 − 25 = 144 chunks), drained only on whatever budget the priority ring doesn't spend that tick —
 * mirroring the {@code END_QUEUE} "gets what is left" precedent already in the same file.
 *
 * <p>The real {@code drain}/{@code step}/{@code Job}/{@code QUEUE}/{@code PRESTREAM_QUEUE} are private
 * and need a live {@code ServerWorld}, so — same approach as {@code LevelBuilderFairnessTest} — this
 * mirrors only the pure scheduling shape: two FIFO queues, a shared per-tick budget spent on the first
 * before any leftover reaches the second, and a per-poll cap on how much of one job a single step
 * advances (mirroring {@code MANTLE_ROWS_PER_STEP} bounding phase 0).
 */
class LevelBuilderPriorityStreamTest {
	private static final int BUDGET = 300;
	private static final int STEP = 100;
	private static final int WORK_PER_JOB = 1000;
	/** 5×5 at the shipped STREAM_CHUNKS_NEAR = 2. */
	private static final int NEAR_COUNT = 25;
	/** 13×13 at STREAM_CHUNKS = 6, minus the inner 5×5 already counted above. */
	private static final int FAR_COUNT = 169 - NEAR_COUNT;

	private static final class Job {
		final String name;
		int remaining;
		Job(String name, int remaining) { this.name = name; this.remaining = remaining; }
	}

	/** Mirrors drainQueue: round-robin (addLast) drain of one queue, spending from a shared budget. */
	private static int drain(Deque<Job> queue, int budget, int stepSize) {
		while (budget > 0 && !queue.isEmpty()) {
			Job job = queue.poll();
			int work = Math.min(stepSize, Math.min(job.remaining, budget));
			job.remaining -= work;
			budget -= work;
			if (job.remaining > 0) queue.addLast(job);
		}
		return budget;
	}

	/** Mirrors one drain() tick: the near ring first, the prefetch ring on whatever budget is left. */
	private static void tick(Deque<Job> near, Deque<Job> far, int budget, int stepSize) {
		int left = drain(near, budget, stepSize);
		drain(far, left, stepSize);
	}

	private static Deque<Job> jobs(String prefix, int count) {
		Deque<Job> q = new ArrayDeque<>();
		for (int i = 0; i < count; i++) q.add(new Job(prefix + i, WORK_PER_JOB));
		return q;
	}

	private static boolean anyRemaining(Deque<Job> q) {
		return q.stream().anyMatch(j -> j.remaining > 0);
	}

	/** Mirrors LevelBuilder.promote: moves a job from far to near, keeping its remaining work. */
	private static void promote(Deque<Job> far, Deque<Job> near, String name) {
		for (var it = far.iterator(); it.hasNext(); ) {
			Job job = it.next();
			if (job.name.equals(name)) {
				it.remove();
				near.addLast(job);
				return;
			}
		}
	}

	private static int remainingOf(Deque<Job> q, String name) {
		return q.stream().filter(j -> j.name.equals(name)).findFirst().map(j -> j.remaining).orElse(-1);
	}

	@Test
	@DisplayName("the near ring finishes just as fast whether or not a much larger far ring is also queued")
	void nearRingIgnoresFarRingSize() {
		Deque<Job> solo = jobs("near", NEAR_COUNT);
		int soloTicks = 0;
		while (anyRemaining(solo)) { drain(solo, BUDGET, STEP); soloTicks++; }

		Deque<Job> near = jobs("near", NEAR_COUNT);
		Deque<Job> far = jobs("far", FAR_COUNT);
		int contendedTicks = 0;
		while (anyRemaining(near)) { tick(near, far, BUDGET, STEP); contendedTicks++; }

		assertEquals(soloTicks, contendedTicks,
				"the near ring gets first claim on the full budget every tick, so 144 queued prefetch"
						+ " chunks should not add a single tick to how long the 25 near ones take");
	}

	@Test
	@DisplayName("without the split, the same near jobs are diluted by the far jobs and take far longer")
	void undividedQueueDilutesTheNearJobs() {
		Deque<Job> isolated = jobs("near", NEAR_COUNT);
		int isolatedTicks = 0;
		while (anyRemaining(isolated)) { drain(isolated, BUDGET, STEP); isolatedTicks++; }

		Deque<Job> combined = jobs("near", NEAR_COUNT);
		for (Job f : jobs("far", FAR_COUNT)) combined.add(f);
		int dilutedTicks = 0;
		while (combined.stream().anyMatch(j -> j.name.startsWith("near") && j.remaining > 0)) {
			drain(combined, BUDGET, STEP);
			dilutedTicks++;
		}

		assertTrue(dilutedTicks > isolatedTicks * 3,
				"one undivided 169-job queue should make the 25 near jobs take well over 3x as long to"
						+ " finish as they do alone (isolated=" + isolatedTicks + ", diluted=" + dilutedTicks
						+ ") — this dilution, not starvation, is the bug the priority ring fixes");
	}

	@Test
	@DisplayName("the split changes who goes first, not the total ticks to drain everything")
	void splitDoesNotChangeCombinedTotalWork() {
		Deque<Job> near = jobs("near", NEAR_COUNT);
		Deque<Job> far = jobs("far", FAR_COUNT);
		int splitTicks = 0;
		while (anyRemaining(near) || anyRemaining(far)) { tick(near, far, BUDGET, STEP); splitTicks++; }

		Deque<Job> combined = jobs("near", NEAR_COUNT);
		for (Job f : jobs("far", FAR_COUNT)) combined.add(f);
		int combinedTicks = 0;
		while (anyRemaining(combined)) { drain(combined, BUDGET, STEP); combinedTicks++; }

		assertEquals(combinedTicks, splitTicks,
				"the same total work at the same total per-tick budget must take the same number of"
						+ " ticks to fully drain either way — the split only reorders who finishes first");
	}

	@Test
	@DisplayName("promoting a chunk a pilot has closed on keeps its progress instead of restarting it")
	void promotionKeepsProgressInsteadOfRestarting() {
		// An otherwise-empty far ring isolates the one property this test checks: promote() must move
		// the job itself, not a fresh one. (Whether far ever gets budget at all when near is busy is
		// the pre-existing, unrelated "near always goes first" behaviour the other tests above cover.)
		Deque<Job> far = new ArrayDeque<>();
		far.add(new Job("target", WORK_PER_JOB));
		Deque<Job> near = new ArrayDeque<>();

		drain(far, BUDGET, STEP);
		int progressBeforePromotion = WORK_PER_JOB - remainingOf(far, "target");
		assertTrue(progressBeforePromotion > 0, "the target should have made progress while far, given budget");

		promote(far, near, "target");
		assertEquals(-1, remainingOf(far, "target"), "promoted job must leave the far queue entirely");
		assertEquals(WORK_PER_JOB - progressBeforePromotion, remainingOf(near, "target"),
				"promotion must carry over exactly the work already done — not restart, not lose it");
	}

	@Test
	@DisplayName("a promoted chunk finishes as fast as the other near jobs from the moment it is promoted")
	void promotedJobFinishesAtNearRingSpeed() {
		Deque<Job> far = new ArrayDeque<>();
		far.add(new Job("target", WORK_PER_JOB));
		drain(far, STEP, STEP); // one small step of real far-ring progress before the pilot closes in
		int remainingAtPromotion = remainingOf(far, "target");
		assertTrue(remainingAtPromotion > 0 && remainingAtPromotion < WORK_PER_JOB);

		Deque<Job> near = jobs("near", NEAR_COUNT);
		promote(far, near, "target");

		// From here the target must be indistinguishable from a fresh near job with the same
		// remaining work, drained alongside the same near-ring peers in the same arrival order —
		// not still paying the diluted far-ring rate it was on before promotion.
		Deque<Job> controlNear = jobs("near", NEAR_COUNT);
		controlNear.addLast(new Job("control", remainingAtPromotion));
		int controlTicks = 0;
		while (remainingOf(controlNear, "control") > 0) { drain(controlNear, BUDGET, STEP); controlTicks++; }

		int promotedTicks = 0;
		while (remainingOf(near, "target") > 0) { drain(near, BUDGET, STEP); promotedTicks++; }

		assertEquals(controlTicks, promotedTicks,
				"once promoted, the target must take exactly as long as a same-arrival-order near job"
						+ " with the same remaining work — same peers, same round-robin, same ticks");
	}
}

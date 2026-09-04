package com.terminaldetector.drmd.world.level;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.diag.DiagProblems;
import com.terminaldetector.drmd.diag.DiagTrace;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the Nether / mantle / End <em>levels</em> into the expanded Overworld column.
 *
 * <p>Bedrock is never a world border — vanilla bedrock → diggable plasma-resistant granite.
 * Dig path: granite crust → mixed mantle → continuous netherrack = seamless Core (HL2-style).
 *
 * <p>Heavy fills stream via {@link MantleStream} and drain in Y-slices against a tick budget.
 */
public final class LevelBuilder {
	/**
	 * Block writes the drain may spend per tick, shared across every queued chunk.
	 *
	 * <p>2800 was tuned against the pre-rescale band heights: 176 mantle rows, 22 of floor relief, 16
	 * of ceiling. The height-budget rescale ({@code WorldLevels}) grew all three — 260 / 35 / 26 — for
	 * a total row count ×1.5 what it was. A budget that stayed at 2800 would still finish every chunk
	 * eventually, but 1.5× the writes per chunk against the same writes-per-tick means each one takes
	 * 1.5× as many ticks to fully build; a pilot who used to outrun the stream only on a very fast
	 * approach now outruns it on an ordinary one, and what they fly into reads as terrain that failed
	 * to load rather than terrain still being built. Scaled by the same ×1.5 so a chunk takes the same
	 * real time to finish as it did before the bands grew. Not benchmarked against a live server —
	 * reasoned from the row-count ratio, the same basis the original number was presumably tuned from.
	 */
	private static final int BUDGET_PER_TICK = 24_000;
	/**
	 * Hard stop on how long one tick may spend filling, whatever the write budget says.
	 *
	 * <p>This is what makes raising the budget safe rather than hopeful. A write count is a guess about
	 * how fast the machine is; a deadline is a fact about how long the tick has actually taken. With
	 * both, a fast machine spends its whole budget and a slow one stops early — instead of the write
	 * count quietly turning into a stall on hardware nobody tested.
	 *
	 * <p>Six milliseconds of a fifty-millisecond tick. Chosen against measurement, not taste: the old
	 * 4,200 writes were filling about one chunk a second against a queue that reached 395, which is
	 * roughly six minutes of backlog and exactly why terrain never appeared below a flying pilot.
	 */
	private static final long MAX_FILL_NANOS = 6_000_000L;
	/**
	 * Most writes one step may do before the tick deadline is re-checked.
	 *
	 * <p>A deadline tested only between steps is only as tight as the longest step. With the budget
	 * raised to 24,000 a single step could spend all of it, and one tick was measured at 52ms against a
	 * 6ms deadline. Capping the step bounds the overshoot to roughly what these writes cost times this
	 * number, which is a couple of milliseconds rather than a lost tick.
	 */
	private static final int WRITES_PER_STEP = 1_024;
	/**
	 * Tick duration above which this fill starts standing aside, in microseconds.
	 *
	 * <p>Twice a tick's own fifty milliseconds. Below it the server is keeping up and this fill is
	 * spending headroom that exists; above it the server is running catch-up ticks back to back and
	 * every millisecond taken here is a millisecond vanilla does not get for generating and sending
	 * the chunks the pilot is actually waiting to see.
	 */
	private static final long YIELD_ABOVE_MICROS = 100_000L;
	/**
	 * Tick duration above which the fill skips the tick entirely.
	 *
	 * <p>Four ticks' worth. A server this far behind is not going to be helped by a smaller share; the
	 * useful move is to give the tick back whole and resume the moment it recovers. Yielding is
	 * self-correcting in both directions: if this fill was the reason ticks were long, they shorten and
	 * it resumes; if it was not, it costs terrain that could not have reached the client anyway.
	 */
	private static final long SKIP_ABOVE_MICROS = 200_000L;

	/** Ticks this fill handed back because the server was already behind — reported, not silent. */
	private static long yieldedTicks;
	private static long shortenedTicks;

	/**
	 * How long this tick may spend filling, given how long the last one took.
	 *
	 * <p>Returns 0 to skip the tick. The measurement comes from {@code DiagServerTick}, whose own tick
	 * handler is registered first and so closes the previous tick before this one runs — the freshest
	 * honest number available from inside a tick.
	 */
	private static long fillNanosForThisTick() {
		long lastTickMicros = com.terminaldetector.drmd.diag.DiagServerTick.lastPeriodMicros();
		// Zero means not measured yet: the first forty ticks are world load, where the full budget is
		// wanted rather than withheld.
		if (lastTickMicros == 0 || lastTickMicros <= YIELD_ABOVE_MICROS) return MAX_FILL_NANOS;
		if (lastTickMicros >= SKIP_ABOVE_MICROS) {
			yieldedTicks++;
			return 0L;
		}
		shortenedTicks++;
		return MAX_FILL_NANOS / 2;
	}

	/** Ticks skipped outright because the previous tick took four ticks' worth of time or more. */
	public static long yieldedTicks() {
		return yieldedTicks;
	}

	/** Ticks run at half the deadline because the previous tick took two to four ticks' worth. */
	public static long shortenedTicks() {
		return shortenedTicks;
	}

	private static final int MAX_QUEUE = 512;
	private static final int MANTLE_PROBE_Y = -120;
	/** Mantle Y-rows per drain step (16×16 each). */
	private static final int MANTLE_ROWS_PER_STEP = 4;
	/** End-band chunks examined per tick, island or not. */
	private static final int END_JOBS_PER_TICK = 64;

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	/**
	 * Look-ahead ring beyond {@link MantleStream#STREAM_CHUNKS_NEAR}, out to
	 * {@link MantleStream#STREAM_CHUNKS} — drains only on whatever budget {@link #QUEUE} doesn't spend
	 * this tick. Same idea as {@link #END_QUEUE} getting the column's leftovers: a wide prefetch radius
	 * is worth streaming ahead of a digging pilot, but not at the cost of the handful of chunks
	 * actually around them right now taking just as long to finish as the whole 169-chunk neighbourhood
	 * would.
	 */
	private static final Deque<Job> PRESTREAM_QUEUE = new ArrayDeque<>();
	private static final Set<Long> PRESTREAM_QUEUED = new HashSet<>();

	/**
	 * End-band work on its own queue.
	 *
	 * <p>A pilot climbing into the band needs its islands and nothing else. Putting them on the
	 * column queue would start each chunk at phase 0 — the whole mantle fill, tens of thousands of
	 * writes underneath a player who is nine hundred blocks above it — and the islands would arrive
	 * only after all of that had drained.
	 */
	private static final Deque<EndJob> END_QUEUE = new ArrayDeque<>();
	private static final Set<Long> END_QUEUED = new HashSet<>();

	/** phase 0=mantle (cursorY), 1=nether floor, 2=nether ceiling, 3=shaft/end finish */
	private record Job(ServerWorld world, int chunkX, int chunkZ, int phase, int cursorY) {}

	private record EndJob(ServerWorld world, int chunkX, int chunkZ) {}

	private LevelBuilder() {}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(LevelBuilder::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Timed against the tick it runs in, not only against its own budget: this fill was the
			// obvious suspect for a stalling server and needs a number set beside the tick's own.
			long started = com.terminaldetector.drmd.diag.DiagServerTick.begin();
			drain(server);
			com.terminaldetector.drmd.diag.DiagServerTick.end("worldgen.column", started);
		});
		DescentMod.LOGGER.info("Level builder online — diggable mantle / Core band (no bedrock border)");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (world.getBottomY() > WorldLevels.NETHER_FLOOR) return;

		rewriteBedrock(chunk);
		ensureCrustPlug(chunk);

		if (!com.terminaldetector.drmd.world.WorldFeatures.NETHER_BAND) return;
		if (!MantleStream.shouldBuildFull(world, chunk.getPos().x, chunk.getPos().z)) return;
		enqueue(world, chunk.getPos().x, chunk.getPos().z);
	}

	private static void enqueue(ServerWorld world, int chunkX, int chunkZ) {
		if (QUEUE.size() >= MAX_QUEUE) return;
		long key = ChunkPos.toLong(chunkX, chunkZ);
		if (QUEUED.contains(key)) return;
		if (PRESTREAM_QUEUED.contains(key)) {
			promote(key);
			return;
		}
		if (world.isChunkLoaded(chunkX, chunkZ) && mantleBuilt(world.getChunk(chunkX, chunkZ))) return;
		QUEUED.add(key);
		QUEUE.add(new Job(world, chunkX, chunkZ, 0, WorldLevels.ABYSS_TOP - 1));
	}

	/**
	 * A pilot closing distance on a chunk still sitting in the prefetch ring used to get nothing: the
	 * old {@link #enqueue} bailed out the instant it saw the chunk already tracked in
	 * {@link #PRESTREAM_QUEUED}, so a chunk that entered the near ring stayed on the diluted budget for
	 * the rest of its build — exactly the crawl the near/far split exists to prevent, just for chunks
	 * unlucky enough to have queued as "far" a moment before the pilot arrived. Moves the job itself
	 * (not a fresh one) onto the priority queue, so whatever phase/cursorY progress it already made on
	 * the prefetch budget carries over instead of being thrown away and restarted.
	 */
	private static void promote(long key) {
		for (var it = PRESTREAM_QUEUE.iterator(); it.hasNext(); ) {
			Job job = it.next();
			if (ChunkPos.toLong(job.chunkX(), job.chunkZ()) == key) {
				it.remove();
				PRESTREAM_QUEUED.remove(key);
				QUEUED.add(key);
				QUEUE.add(job);
				return;
			}
		}
		// Tracked but not actually sitting in the deque right now (mid-poll within this same tick's
		// drainQueue call) — nothing to move; drainQueue's own re-add at the end of this tick will
		// still land it in PRESTREAM_QUEUE, but the very next enqueue() for it (next tick, if the
		// pilot is still this close) will find it in the deque and promote it then.
		PRESTREAM_QUEUED.remove(key);
	}

	/**
	 * Same as {@link #enqueue}, but onto the low-priority prefetch ring ({@link #PRESTREAM_QUEUE}).
	 *
	 * <p>Never promotes a chunk already sitting in the priority {@link #QUEUE} — if it's already there,
	 * it is already getting the better deal this call would have given it. A chunk enqueued here that
	 * the pilot later closes on stays in this ring rather than jumping the priority one: worst case, it
	 * finishes exactly as slowly as every chunk did before this split existed, never slower.
	 */
	private static void enqueuePrestream(ServerWorld world, int chunkX, int chunkZ) {
		if (PRESTREAM_QUEUE.size() >= MAX_QUEUE) return;
		long key = ChunkPos.toLong(chunkX, chunkZ);
		if (QUEUED.contains(key) || PRESTREAM_QUEUED.contains(key)) return;
		if (world.isChunkLoaded(chunkX, chunkZ) && mantleBuilt(world.getChunk(chunkX, chunkZ))) return;
		PRESTREAM_QUEUED.add(key);
		PRESTREAM_QUEUE.add(new Job(world, chunkX, chunkZ, 0, WorldLevels.ABYSS_TOP - 1));
	}

	/**
	 * Background stream for {@link com.terminaldetector.drmd.world.layer.SeamWarmup}: enqueue the
	 * mantle/Nether build around a chunk without waiting for CHUNK_LOAD.
	 */
	public static void streamAround(ServerWorld world, int chunkX, int chunkZ, int radius) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		int r = Math.max(0, Math.min(radius, 10));
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				enqueue(world, chunkX + dx, chunkZ + dz);
			}
		}
	}

	/**
	 * Same, for the End band alone — used while a pilot is inside it or climbing toward the seam.
	 *
	 * <p>CHUNK_LOAD covers chunks a pilot flies into. It cannot cover the chunks that were already
	 * loaded when they turned upward, and those are the ones under a climb, so without this pass the
	 * band opens as empty sky and fills in only where the pilot has not been yet.
	 */
	public static void streamEndBand(ServerWorld world, int chunkX, int chunkZ, int radius) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (!com.terminaldetector.drmd.world.WorldFeatures.END_BAND) return;
		int r = Math.max(0, Math.min(radius, 10));
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				enqueueEndBand(world, chunkX + dx, chunkZ + dz);
			}
		}
	}

	private static void enqueueEndBand(ServerWorld world, int chunkX, int chunkZ) {
		if (END_QUEUE.size() >= MAX_QUEUE) return;
		long key = ChunkPos.toLong(chunkX, chunkZ);
		if (!END_QUEUED.add(key)) return;
		END_QUEUE.add(new EndJob(world, chunkX, chunkZ));
	}

	private static boolean mantleBuilt(WorldChunk chunk) {
		BlockPos probe = new BlockPos(
				chunk.getPos().getStartX() + 8, MANTLE_PROBE_Y, chunk.getPos().getStartZ() + 8);
		BlockState st = chunk.getBlockState(probe);
		return !st.isAir() && !st.isOf(Blocks.CAVE_AIR) && !st.isOf(Blocks.VOID_AIR);
	}

	/**
	 * Spend up to {@code budget} draining one queue. Shared by {@link #QUEUE} and
	 * {@link #PRESTREAM_QUEUE} so the two tiers can only ever differ in <em>how much</em> budget they
	 * get, never in how fairly they spend it.
	 *
	 * <p>Re-queues with {@code addLast}, not {@code addFirst}: a chunk re-queued mid-build goes to the
	 * back of its own queue's line, not straight back to the front. {@code addFirst} let the
	 * head-of-queue job monopolize every tick's whole budget until it finished all four phases — worth
	 * it for the very first chunk queued, but every chunk queued after it (which, for a moving pilot,
	 * means everything ahead of them) sat completely untouched behind that one job, then the next, one
	 * at a time. A queue anywhere near {@link #MAX_QUEUE} deep made that a multi-minute wait before
	 * generation ahead of the pilot ever got a single write — reading as generation stuck around only
	 * the small area that was queued first. {@code addLast} instead round-robins the budget across
	 * every in-flight job each tick, so a chunk newly queued under load still starts making progress on
	 * the tick it is added, not after the whole backlog ahead of it finishes.
	 *
	 * @return the budget left over after this queue either empties or the budget runs out
	 */
	/** Ticks between reports while the fill stays saturated — 200 is ten seconds, not every tick. */
	private static final int SATURATION_REPORT_TICKS = 200;

	private static int saturatedTicks;
	private static int worstQueueDepth;
	private static int chunksFilled;
	private static int deadlineStops;
	private static int lastWritesSpent;
	private static long lastFillNanos;
	private static long worstFillNanos;

	/** Chunks whose column fill finished this session. */
	public static int chunksFilled() {
		return chunksFilled;
	}

	/** Ticks that stopped on the time deadline rather than on the write budget. */
	public static int deadlineStops() {
		return deadlineStops;
	}

	public static int lastWritesSpent() {
		return lastWritesSpent;
	}

	/** Microseconds the last tick's fill took — reported in units a tick budget is judged in. */
	public static long lastFillMicros() {
		return lastFillNanos / 1000L;
	}

	public static long worstFillMicros() {
		return worstFillNanos / 1000L;
	}

	/**
	 * What one tick's fill actually cost.
	 *
	 * <p>Recorded rather than assumed, because the write budget alone cannot say whether it is the
	 * limit. Spending the whole budget well inside the deadline means the budget can go up; hitting
	 * the deadline first means the machine is the limit and raising it would only stall the tick.
	 * Which of those is happening was, until this existed, purely a matter of opinion.
	 */
	private static void recordFillCost(int writesSpent, long tookNanos) {
		lastWritesSpent = writesSpent;
		lastFillNanos = tookNanos;
		if (tookNanos > worstFillNanos) worstFillNanos = tookNanos;
	}

	/** Chunks waiting to be filled right now. */
	public static int queueDepth() {
		return QUEUE.size() + PRESTREAM_QUEUE.size();
	}

	/** The deepest the queue has been this session. */
	public static int worstQueueDepth() {
		return worstQueueDepth;
	}

	/** How many consecutive ticks the whole write budget has been spent with work still waiting. */
	public static int saturatedTicks() {
		return saturatedTicks;
	}

	/**
	 * Notice when the column fill cannot keep up, and say so once every ten seconds rather than every
	 * tick.
	 *
	 * <p>This exists to separate two failures that look identical from inside the game — a hole in the
	 * ground below you — and that no amount of reading the code from outside can tell apart:
	 *
	 * <ul>
	 *   <li><b>Saturated with a deep queue</b> means the budget is the limit: the chunks are queued and
	 *       the writes are simply not fast enough, so terrain trails a moving pilot.
	 *   <li><b>An empty queue with terrain still missing</b> means the opposite — the chunks were never
	 *       queued at all, so the streaming window ({@code MantleStream.STREAM_CHUNKS}) is smaller than
	 *       what the pilot can see, and raising the budget would change nothing.
	 * </ul>
	 *
	 * <p>Both numbers go in the diagnostics report for exactly that reason. Guessing which one is in
	 * play has cost more time on this project than fixing either would have.
	 */
	/**
	 * The queue's depth, without judging it.
	 *
	 * <p>Split out for the yielded tick, where the backlog is real but the write budget is not what is
	 * holding it: resetting the saturation counter there would report "not saturated" every time the
	 * server was too busy to let this run, which is the opposite of what happened.
	 */
	private static void recordQueueDepth() {
		int depth = queueDepth();
		if (depth > worstQueueDepth) worstQueueDepth = depth;
	}

	private static void recordBacklog(int budgetLeft) {
		recordQueueDepth();
		int depth = queueDepth();

		if (budgetLeft > 0 || depth == 0) {
			saturatedTicks = 0;
			return;
		}
		saturatedTicks++;
		if (saturatedTicks % SATURATION_REPORT_TICKS != 0) return;
		DiagProblems.record("worldgen", "column fill saturated for " + (saturatedTicks / 20) + "s — "
				+ depth + " chunks still queued (worst " + worstQueueDepth + "), budget "
				+ BUDGET_PER_TICK + " writes/tick");
	}

	private static int drainQueue(Deque<Job> queue, Set<Long> queued, int budget, long deadlineNanos) {
		while (budget > 0 && !queue.isEmpty()) {
			// Two limits, and the one that bites first wins. The write budget is what is affordable on a
			// fast machine; the deadline is what is affordable on this one.
			if (System.nanoTime() >= deadlineNanos) {
				deadlineStops++;
				break;
			}
			Job job = queue.poll();
			long key = ChunkPos.toLong(job.chunkX, job.chunkZ);
			queued.remove(key);
			if (!job.world.isChunkLoaded(job.chunkX, job.chunkZ)) continue;
			// Capped, so the deadline below is checked often enough to mean anything. Handing a step the
			// whole remaining budget let one call run for 52ms against a 6ms deadline — measured, in the
			// first report that had the numbers — because the deadline is only tested between steps.
			StepResult step = step(job, Math.min(budget, WRITES_PER_STEP));
			budget -= step.written;
			if (!step.done) {
				queued.add(key);
				queue.add(step.next);
			} else {
				chunksFilled++;
				DiagTrace.count("worldgen.chunkFilled");
				// A line every fifty, not every chunk: the trace should show the rate over time without
				// spending its whole buffer on one system.
				if (chunksFilled % 50 == 0) {
					DiagTrace.record("worldgen", chunksFilled + " chunks filled, " + queueDepth() + " still queued");
				}
			}
		}
		return budget;
	}

	private static void drain(net.minecraft.server.MinecraftServer server) {
		ServerWorld ow = server.getOverworld();
		if (ow != null && com.terminaldetector.drmd.world.WorldFeatures.NETHER_BAND) {
			for (Digger d : nearbyDiggers(server)) {
				for (int dx = -MantleStream.STREAM_CHUNKS; dx <= MantleStream.STREAM_CHUNKS; dx++) {
					for (int dz = -MantleStream.STREAM_CHUNKS; dz <= MantleStream.STREAM_CHUNKS; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) <= MantleStream.STREAM_CHUNKS_NEAR) {
							enqueue(ow, d.cx + dx, d.cz + dz);
						} else {
							enqueuePrestream(ow, d.cx + dx, d.cz + dz);
						}
					}
				}
			}
		}

		int budget = BUDGET_PER_TICK;
		long startedNanos = System.nanoTime();
		long fillNanos = fillNanosForThisTick();
		if (fillNanos <= 0) {
			// Nothing filled, but the tick still gets its bookkeeping: a queue that stops moving because
			// the server is drowning must not read in the report as a queue that stopped for no reason.
			recordQueueDepth();
			recordFillCost(0, 0);
			clearWriteChunk();
			return;
		}
		long deadlineNanos = startedNanos + fillNanos;
		budget = drainQueue(QUEUE, QUEUED, budget, deadlineNanos);
		budget = drainQueue(PRESTREAM_QUEUE, PRESTREAM_QUEUED, budget, deadlineNanos);
		recordBacklog(budget);

		// End band gets what is left. One island is a single indivisible step, so it runs on the
		// remaining budget rather than reserving its own — the column keeps priority when a pilot is
		// digging and flying at once.
		//
		// Bounded by count as well as by budget: most chunks in the band have no island, cost
		// nothing, and would let one tick walk the entire queue however long the stream made it.
		int endJobs = 0;
		while (budget > 0 && endJobs++ < END_JOBS_PER_TICK && !END_QUEUE.isEmpty()
				&& System.nanoTime() < deadlineNanos) {
			EndJob job = END_QUEUE.poll();
			END_QUEUED.remove(ChunkPos.toLong(job.chunkX, job.chunkZ));
			if (!job.world.isChunkLoaded(job.chunkX, job.chunkZ)) continue;
			long seed = job.world.getSeed()
					^ (((long) job.chunkX) * 341873128712L)
					^ (((long) job.chunkZ) * 132897987541L);
			budget -= buildEndLevel(job.world, job.chunkX, job.chunkZ, seed, Random.create(seed));
		}

		recordFillCost(BUDGET_PER_TICK - budget, System.nanoTime() - startedNanos);
		clearWriteChunk();
	}

	private record Digger(int cx, int cz) {}
	private record StepResult(int written, boolean done, Job next) {}

	private static List<Digger> nearbyDiggers(net.minecraft.server.MinecraftServer server) {
		List<Digger> list = new ArrayList<>(4);
		for (var p : server.getPlayerManager().getPlayerList()) {
			if (p.getWorld().getRegistryKey() != World.OVERWORLD) continue;
			// Diggers below industrial, or anyone approaching the Nether Core seam (SeamWarmup).
			if (p.getY() > WorldLevels.INDUSTRIAL_TOP + 24
					&& !com.terminaldetector.drmd.world.layer.SeamWarmup.nearNetherSeam(p.getY())) {
				continue;
			}
			list.add(new Digger(p.getBlockX() >> 4, p.getBlockZ() >> 4));
		}
		return list;
	}

	private static StepResult step(Job job, int budget) {
		long seed = job.world.getSeed()
				^ (((long) job.chunkX) * 341873128712L)
				^ (((long) job.chunkZ) * 132897987541L);
		Random random = Random.create(seed);

		if (job.phase == 0) {
			int written = 0;
			int y = job.cursorY;
			int rows = 0;
			while (y >= WorldLevels.NETHER_CEILING && rows < MANTLE_ROWS_PER_STEP && written < budget) {
				written += fillMantleRow(job.world, job.chunkX, job.chunkZ, y, random);
				y--;
				rows++;
			}
			if (y >= WorldLevels.NETHER_CEILING) {
				return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 0, y));
			}
			return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 1, 0));
		}

		// The band is written floor first, then ceiling, then everything else. Relief costs several
		// times what the two flat slabs did, and one chunk that overruns the tick budget by that much
		// is a visible hitch — splitting it lets the drain stop between the two halves.
		//
		// world.getSeed(), not the chunk-mixed `seed` above: that one is right for `random` (block
		// variety should differ chunk to chunk) and wrong for a height field two neighbouring chunks
		// have to agree on. Feeding the mixed seed to NetherRelief was exactly the bug that shipped —
		// see buildNetherFloor's doc.
		if (job.phase == 1) {
			int written = buildNetherFloor(job.world, job.chunkX, job.chunkZ, job.world.getSeed(), random);
			return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 2, 0));
		}

		if (job.phase == 2) {
			int written = buildNetherCeiling(job.world, job.chunkX, job.chunkZ, job.world.getSeed(), random);
			return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 3, 0));
		}

		int written = 0;
		if (com.terminaldetector.drmd.world.WorldFeatures.END_BAND) {
			written += buildEndLevel(job.world, job.chunkX, job.chunkZ, seed, random);
		}
		if (WorldLevels.isShaftChunk(job.chunkX, job.chunkZ)) {
			written += cutDescentShaft(job.world, job.chunkX, job.chunkZ);
		}
		return new StepResult(written, true, job);
	}

	private static int fillMantleRow(ServerWorld world, int chunkX, int chunkZ, int y, Random random) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
		int top = WorldLevels.ABYSS_TOP;
		int bottom = WorldLevels.NETHER_CEILING;
		int span = Math.max(1, top - bottom);
		float t = (float) (top - y) / (float) span;
		boolean shaft = WorldLevels.isShaftChunk(chunkX, chunkZ);
		int scx = baseX + 8;
		int scz = baseZ + 8;
		int r = WorldLevels.SHAFT_RADIUS;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;
				if (shaft) {
					int odx = x - scx;
					int odz = z - scz;
					if (odx * odx + odz * odz <= r * r) continue;
				}
				BlockState state;
				if (t < 0.12f) {
					state = random.nextFloat() < 0.7f
							? ModWorldBlocks.PLASMA_GRANITE.getDefaultState()
							: Blocks.GRANITE.getDefaultState();
				} else if (t < 0.45f) {
					float mix = (t - 0.12f) / 0.33f;
					if (random.nextFloat() < mix * 0.55f) {
						state = pickNetherGround(random);
					} else if (random.nextFloat() < 0.35f) {
						state = ModWorldBlocks.PLASMA_GRANITE.getDefaultState();
					} else {
						state = Blocks.GRANITE.getDefaultState();
					}
				} else if (t < 0.75f) {
					state = random.nextFloat() < 0.65f
							? pickNetherGround(random)
							: Blocks.BLACKSTONE.getDefaultState();
				} else {
					state = pickNetherGround(random);
				}
				if (t > 0.55f && random.nextInt(80) == 0) {
					state = Blocks.LAVA.getDefaultState();
				}
				written += set(world, pos.set(x, y, z), state);
			}
		}
		return written;
	}

	private static void ensureCrustPlug(WorldChunk chunk) {
		int baseX = chunk.getPos().getStartX();
		int baseZ = chunk.getPos().getStartZ();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		// Skip if the crust seam is already solid — avoids rewriting on every reload.
		pos.set(baseX + 8, WorldLevels.ABYSS_TOP - 2, baseZ + 8);
		BlockState probe = chunk.getBlockState(pos);
		if (!probe.isAir() && !probe.isOf(Blocks.CAVE_AIR) && !probe.isOf(Blocks.VOID_AIR)
				&& !probe.isOf(Blocks.BEDROCK)
				&& (probe.isOf(ModWorldBlocks.PLASMA_GRANITE) || probe.isOf(Blocks.GRANITE)
				|| probe.isOf(Blocks.STONE) || probe.isOf(Blocks.DEEPSLATE))) {
			return;
		}
		Random random = Random.create((baseX * 31L) ^ baseZ);
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				for (int y = WorldLevels.ABYSS_TOP - 1; y >= WorldLevels.ABYSS_TOP - 6; y--) {
					pos.set(baseX + dx, y, baseZ + dz);
					BlockState cur = chunk.getBlockState(pos);
					if (!cur.isAir() && !cur.isOf(Blocks.CAVE_AIR) && !cur.isOf(Blocks.BEDROCK)
							&& !cur.isOf(Blocks.VOID_AIR)) {
						continue;
					}
					BlockState st = random.nextFloat() < 0.65f
							? ModWorldBlocks.PLASMA_GRANITE.getDefaultState()
							: Blocks.GRANITE.getDefaultState();
					chunk.setBlockState(pos, st, false);
				}
			}
		}
	}

	/**
	 * Replace unbreakable bedrock with diggable plasma granite.
	 *
	 * <p>Must stay cheap: this runs on every {@code CHUNK_LOAD}, including during
	 * "Preparing spawn area". Scanning the whole −512…−56 band (~450 Y × 256) per
	 * chunk freezes the join at 100% and trips the watchdog. Bedrock only exists in
	 * thin floor/cap bands — rewrite those only.
	 */
	private static int rewriteBedrock(WorldChunk chunk) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int minY = chunk.getBottomY();
		int heightTop = minY + chunk.getHeight();
		int written = 0;
		// Column-floor bedrock.
		written += rewriteBedrockBand(chunk, pos, minY, Math.min(heightTop, minY + 8));
		// Old −64 seam / crust leftovers.
		written += rewriteBedrockBand(chunk, pos,
				Math.max(minY, WorldLevels.ABYSS_TOP - 8),
				Math.min(heightTop, WorldLevels.ABYSS_TOP + 2));
		return written;
	}

	private static int rewriteBedrockBand(WorldChunk chunk, BlockPos.Mutable pos, int y0, int y1) {
		if (y0 >= y1) return 0;
		int written = 0;
		int startX = chunk.getPos().getStartX();
		int startZ = chunk.getPos().getStartZ();
		for (int y = y0; y < y1; y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					pos.set(startX + x, y, startZ + z);
					if (chunk.getBlockState(pos).isOf(Blocks.BEDROCK)) {
						chunk.setBlockState(pos, ModWorldBlocks.PLASMA_GRANITE.getDefaultState(), false);
						written++;
					}
				}
			}
		}
		return written;
	}

	/**
	 * The floor of the Nether band: ground that rises and falls, with lava in what it does not reach.
	 *
	 * <p>Relief is the whole point. A flat slab under a flat ceiling is a room a hundred and eighty
	 * blocks tall, and from a cockpit that reads as nothing at all — there is no scale in it and
	 * nothing to fly around. Heights come from {@link NetherRelief}, which is a pure function of
	 * world position, so the chunk built now and its neighbour built ten seconds later meet without
	 * a step — <strong>provided both are given the same seed</strong>. That is why this takes
	 * {@code worldSeed} rather than the chunk-mixed {@code seed} that {@link #step} already has in
	 * scope for its {@link Random}: passing the mixed one here was the actual shipped bug — every
	 * chunk sampled the height field under a different seed, so neighbours agreed on nothing and
	 * every chunk border rendered as a cliff. Seen from altitude across a wide stream of chunks, a
	 * grid of cliffs one next to the other reads as vertical stripes.
	 */
	private static int buildNetherFloor(ServerWorld world, int chunkX, int chunkZ, long worldSeed, Random random) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
		int lava = NetherRelief.lavaLevel();
		boolean shaft = WorldLevels.isShaftChunk(chunkX, chunkZ);
		int scx = baseX + 8;
		int scz = baseZ + 8;
		int sr = WorldLevels.SHAFT_RADIUS;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;
				// The descent shafts have to survive the fill, or the way down ends in the ceiling.
				boolean inShaft = shaft
						&& (x - scx) * (x - scx) + (z - scz) * (z - scz) <= sr * sr;

				// Sealed base — the column's floor, not a border: still diggable granite.
				written += set(world, pos.set(x, WorldLevels.NETHER_FLOOR, z),
						ModWorldBlocks.PLASMA_GRANITE.getDefaultState());

				int top = NetherRelief.floorTop(worldSeed, x, z);
				if (!inShaft) {
					for (int y = WorldLevels.NETHER_FLOOR + 1; y <= top; y++) {
						written += set(world, pos.set(x, y, z), pickNetherGround(random));
					}
				}
				// Lava fills the low ground. Above the sea the same columns are the coast.
				if (!inShaft) {
					for (int y = Math.max(top + 1, WorldLevels.NETHER_FLOOR + 1); y <= lava; y++) {
						written += set(world, pos.set(x, y, z), Blocks.LAVA.getDefaultState());
					}
				}
				// A crust of magma where ground meets sea reads as heat rather than as a bathtub edge.
				if (!inShaft && top >= lava - 1 && top <= lava + 2 && random.nextInt(4) == 0) {
					written += set(world, pos.set(x, top, z), Blocks.MAGMA_BLOCK.getDefaultState());
				}
			}
		}
		return written;
	}

	/**
	 * The ceiling, hanging down to meet the floor in places.
	 *
	 * <p>Where the two nearly touch the band pinches into a corridor, which is what gives a pilot
	 * something to thread. Glowstone goes on the underside in clusters rather than one block at a
	 * time — scattered singles are invisible from any distance worth flying at.
	 */
	private static int buildNetherCeiling(ServerWorld world, int chunkX, int chunkZ, long worldSeed, Random random) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
		boolean shaft = WorldLevels.isShaftChunk(chunkX, chunkZ);
		int scx = baseX + 8;
		int scz = baseZ + 8;
		int sr = WorldLevels.SHAFT_RADIUS;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;
				boolean inShaft = shaft
						&& (x - scx) * (x - scx) + (z - scz) * (z - scz) <= sr * sr;

				written += set(world, pos.set(x, WorldLevels.NETHER_CEILING, z),
						ModWorldBlocks.PLASMA_GRANITE.getDefaultState());
				if (inShaft) continue;

				int bottom = NetherRelief.ceilingBottom(worldSeed, x, z);
				for (int y = WorldLevels.NETHER_CEILING - 1; y >= bottom; y--) {
					written += set(world, pos.set(x, y, z),
							random.nextInt(5) == 0 ? Blocks.BLACKSTONE.getDefaultState()
									: Blocks.BASALT.getDefaultState());
				}
				// Clustered by position, not per block: one lattice cell in a few lights up whole.
				if (NetherRelief.value(worldSeed ^ 0x91E10DA5L, x, z, 9) > 0.86f) {
					written += set(world, pos.set(x, bottom, z), Blocks.GLOWSTONE.getDefaultState());
				}
			}
		}
		return written;
	}

	private static BlockState pickNetherGround(Random random) {
		int roll = random.nextInt(10);
		if (roll < 5) return Blocks.NETHERRACK.getDefaultState();
		if (roll < 8) return Blocks.BLACKSTONE.getDefaultState();
		return Blocks.MAGMA_BLOCK.getDefaultState();
	}

	/**
	 * One End-band island for this chunk, if the seed puts one here.
	 *
	 * <p>The shape lives in {@link com.terminaldetector.drmd.world.gen2.EndIslandGenerator} and is
	 * shared with the chunk-load pass, so a pilot who flies into the band sees the same archipelago
	 * the background stream would have built for them. The returned figure is the drain budget's
	 * charge for the island — its footprint, which tracks the write count closely enough to keep one
	 * island from eating a whole tick's allowance unnoticed.
	 */
	private static int buildEndLevel(ServerWorld world, int chunkX, int chunkZ, long seed, Random random) {
		BlockPos origin = com.terminaldetector.drmd.world.gen2.EndIslandGenerator
				.originFor(chunkX, chunkZ, seed);
		if (origin == null) return 0;
		if (world.isOutOfHeightLimit(origin)) return 0;
		if (com.terminaldetector.drmd.world.gen2.EndIslandGenerator.built(world, origin)) return 0;
		var island = com.terminaldetector.drmd.world.gen2.EndIslandGenerator
				.generate(world, origin, random);
		return island == null ? 0 : island.sizeX * island.sizeZ;
	}

	/**
	 * At {@code WorldLevels.SHAFT_RADIUS} = 3, a whole-block circle test is a third of the tunnel's
	 * own radius away from a real one at every corner — a 6DoF ship reads that as a snag, not a
	 * curve. {@link com.terminaldetector.drmd.world.micro.TunnelCarving} rounds the boundary this
	 * pass leaves solid down to quarter-cell precision, in one pass over the whole shaft height
	 * rather than split across ticks the way the mantle phases above are: a shaft chunk is already
	 * one of the heavier single jobs this drain accepts unbudgeted (the whole-block carve below is
	 * itself ~18k writes in one call), and shafts are sparse — one in {@code SHAFT_CHUNK_SPACING}²
	 * = 64 chunks — so this roughly doubles an already-accepted, already-infrequent cost rather than
	 * introducing a new one.
	 */
	private static int cutDescentShaft(ServerWorld world, int chunkX, int chunkZ) {
		int cx = (chunkX << 4) + 8;
		int cz = (chunkZ << 4) + 8;
		int r = WorldLevels.SHAFT_RADIUS;
		int yTop = -40;
		int yBottom = WorldLevels.NETHER_FLOOR + WorldLevels.NETHER_FLOOR_THICKNESS + 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
		for (int y = yTop; y >= yBottom; y--) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > r * r) continue;
					written += set(world, pos.set(cx + dx, y, cz + dz), Blocks.AIR.getDefaultState());
				}
			}
		}
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > (r + 1) * (r + 1) || d2 <= r * r) continue;
				written += set(world, pos.set(cx + dx, -40, cz + dz), Blocks.SEA_LANTERN.getDefaultState());
			}
		}
		written += com.terminaldetector.drmd.world.micro.TunnelCarving.carveBoundaryRing(
				world, cx, cz, r, yTop, yBottom);
		return written;
	}

	/** One chunk, remembered across a row. Cleared every tick, so it never outlives an unload. */
	private static WorldChunk writeChunk;
	private static ServerWorld writeChunkWorld;
	private static long writeChunkKey = Long.MIN_VALUE;

	/**
	 * Write one block of terrain, as cheaply as this can be done without changing what a client sees.
	 *
	 * <p>This used to go through {@code world.setBlockState(NOTIFY_LISTENERS | FORCE_STATE)}, which
	 * looks the chunk up again for every single block — and a row is 256 blocks of the same chunk. The
	 * first live report measured the whole fill at 2.15 microseconds a write, which is where a budget
	 * of 24,000 a tick turned into 3,000.
	 *
	 * <p>So: the chunk is found once and reused, and the write goes straight to it. Clients are still
	 * told, explicitly, through the same call {@code World.setBlockState} would have made — that is a
	 * set insert per block and a packet per section per tick, not a packet per block, so keeping it
	 * costs almost nothing and losing it would leave freshly filled terrain invisible.
	 *
	 * <p>What is <em>not</em> saved here, stated so the next measurement is read correctly: heightmaps
	 * and lighting still run inside the chunk's own write, and those are the likely remainder. If the
	 * next report still shows microseconds per write, that is where to go — section-level writes with
	 * one lighting pass at the end — and this change will have told us so.
	 */
	private static int set(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.isOutOfHeightLimit(pos)) return 0;
		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		long key = ChunkPos.toLong(chunkX, chunkZ);
		if (writeChunk == null || writeChunkWorld != world || writeChunkKey != key) {
			// Only a chunk the server already has. getChunk would generate a missing one on the spot,
			// which is a whole chunk of work inside a tick that was budgeted for a few thousand writes —
			// a job only ever runs for a loaded chunk, so a write landing outside one is incidental and
			// skipping it is cheaper and more honest than forcing the world to grow to receive it.
			writeChunkWorld = world;
			writeChunkKey = key;
			writeChunk = world.isChunkLoaded(chunkX, chunkZ) ? world.getChunk(chunkX, chunkZ) : null;
		}
		if (writeChunk == null) return 0;
		writeChunk.setBlockState(pos, state, false);
		world.getChunkManager().markForUpdate(pos);
		return 1;
	}

	/**
	 * Drop the remembered chunk at the end of a tick.
	 *
	 * <p>A static reference to a chunk is a reference the chunk manager cannot unload around. One tick
	 * is long enough for the cache to pay for itself and short enough that it never holds one open.
	 */
	private static void clearWriteChunk() {
		writeChunk = null;
		writeChunkWorld = null;
		writeChunkKey = Long.MIN_VALUE;
	}
}

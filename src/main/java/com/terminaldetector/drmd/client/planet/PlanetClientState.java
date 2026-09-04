package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.diag.DiagProblems;
import com.terminaldetector.drmd.diag.DiagTrace;
import com.terminaldetector.drmd.world.store.MemorySectionStorage;
import com.terminaldetector.drmd.world.store.SectionKey;
import com.terminaldetector.drmd.world.store.SurfaceSection;
import com.terminaldetector.drmd.world.store.SurfaceStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Seed, scars, catalogued landmarks and the built field for the voxel horizon. */
public final class PlanetClientState {
	public static final PlanetClientState INSTANCE = new PlanetClientState();

	private volatile long seed;
	private volatile boolean hasSeed;
	/**
	 * Concurrent because the build reads it from a worker thread while the network thread writes it.
	 * It was a plain {@code HashSet}, which was already a race the moment the build stopped being
	 * something only the render thread did.
	 */
	private final Set<Long> scars = ConcurrentHashMap.newKeySet();
	private volatile List<HorizonLandmark> landmarks = List.of();
	/** The field currently drawable. May be one rebuild out of date — see the accessor below. */
	private volatile PlanetSurfaceMesh mesh;
	/** The data under the field changed, so rebuild even if the geometry still fits the eye. */
	private volatile boolean dirty;
	/**
	 * Bumped whenever the data changes. A build carries the value it started with and publishes only
	 * if it still matches — otherwise it finished describing a world that has moved on, and
	 * {@link #dirty} is still set, so the next frame asks again.
	 */
	private final AtomicInteger generation = new AtomicInteger();
	private final AtomicBoolean building = new AtomicBoolean();

	/**
	 * One thread, daemon, below normal priority.
	 *
	 * <p>One because there is one field: a second concurrent build would describe the same eye and be
	 * thrown away. Daemon so it can never hold the game open. Named so it is obvious in a profiler
	 * whose thread this is — the same reason the engine in {@code docs/source-audit/algorithm-map.md}
	 * names its own {@code region-gen} pool.
	 */
	private static final ExecutorService BUILDER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "drmd-horizon");
		thread.setDaemon(true);
		thread.setPriority(Thread.NORM_PRIORITY - 2);
		return thread;
	});
	/** Sections the server has sent — the world as actually seen, where it has been seen. */
	private final SurfaceStore observed = new SurfaceStore(new MemorySectionStorage());

	private PlanetClientState() {}

	/**
	 * Server snapshot: the seed, which cells a reactor burned, and what has been built.
	 *
	 * <p>The surface itself follows from the seed, so this arrives once on join and again only when
	 * one of the other two changes — where the map it replaces streamed six hundred cells of height
	 * and tint every player tick.
	 */
	public void apply(long seed, List<Long> scarCells, List<HorizonLandmark> marks) {
		boolean changed = false;
		if (this.seed != seed || !hasSeed) {
			this.seed = seed;
			this.hasSeed = true;
			changed = true;
		}
		if (scars.size() != scarCells.size() || !scars.containsAll(scarCells)) {
			scars.clear();
			scars.addAll(scarCells);
			changed = true;
		}
		if (!landmarks.equals(marks)) {
			landmarks = List.copyOf(marks);
			changed = true;
		}
		if (changed) invalidate();
	}

	/**
	 * The data under the field changed.
	 *
	 * <p>Deliberately does <em>not</em> drop the field. Dropping it left the horizon blank until the
	 * next build finished, which was invisible while that build happened inside the frame and would
	 * be a hole in the sky now that it does not. The old field stays on screen — one rebuild out of
	 * date on a backdrop kilometres away is not something a pilot can see — and the flag makes the
	 * next frame ask for a new one.
	 */
	private void invalidate() {
		dirty = true;
		generation.incrementAndGet();
	}

	/** A section arrived. Anything already built is stale, because the ground under it changed. */
	public void acceptSection(long key, byte[] data) {
		SurfaceSection section = SurfaceSection.fromBytes(data);
		if (section == null) return;
		observed.put(key, section);
		invalidate();
	}

	/**
	 * Observed height at a position, or {@link SurfaceSection#NO_HEIGHT} where nothing was ever
	 * seen — which is where the procedural map takes over.
	 */
	public short observedHeight(double worldX, double worldZ, int level) {
		return observed.heightAt((int) Math.floor(worldX), (int) Math.floor(worldZ), level);
	}

	public int observedColour(double worldX, double worldZ, int level) {
		return observed.colourAt((int) Math.floor(worldX), (int) Math.floor(worldZ), level);
	}

	/** Which stored level has cells closest to the size the horizon is drawing. */
	public static int levelForCell(int cellBlocks) {
		int level = 0;
		while (level < SectionKey.MAX_LEVEL && SectionKey.cellSize(level + 1) <= cellBlocks) level++;
		return level;
	}

	public void clear() {
		hasSeed = false;
		seed = 0L;
		scars.clear();
		landmarks = List.of();
		observed.reset();
		// The one case where the field really does go: the world it described is gone.
		mesh = null;
		invalidate();
	}

	public boolean hasSeed() {
		return hasSeed;
	}

	public long seed() {
		return seed;
	}

	/**
	 * The field around this eye — whatever is built right now, and never a wait.
	 *
	 * <p>This used to build the field inside the call, on the render thread, in the frame that asked
	 * for it. Measured on a live flight: fourteen rebuilds in a session, worst 26ms, which at sixty
	 * frames a second is a dropped frame every time the eye moves far enough. Rebuilds fire every 64
	 * blocks flown, so at this project's speeds that is several a second.
	 *
	 * <p>Now the caller gets the field that exists and the new one is built on {@code drmd-horizon}.
	 * The shape of this is taken from the streaming engine read in PHASE 0 — see
	 * {@code docs/source-audit/algorithm-map.md}: the thread that asks is never the thread that
	 * builds, and it is answered immediately with the best thing available rather than blocked until
	 * the right one exists.
	 *
	 * <p><b>Returns null before the first field exists.</b> That is the honest answer for the first
	 * frames of a world and callers must handle it; the alternative — building the first one inline —
	 * would put the whole 26ms back into exactly the frame where the game is already busiest.
	 */
	public PlanetSurfaceMesh mesh(double camX, double eyeY, double camZ, double inner, double reach) {
		PlanetSurfaceMesh current = mesh;
		if (current == null || dirty || current.stale(camX, eyeY, camZ, inner, reach)) {
			requestBuild(camX, eyeY, camZ, inner, reach);
		}
		return current;
	}

	/**
	 * Start a build unless one is already running.
	 *
	 * <p>One at a time, not one per request: the eye moves every frame, so queueing a build per frame
	 * would spend the worker on fields that were obsolete before they started. Whichever build is
	 * running was started for a nearby eye, and if it lands stale the next frame simply asks again.
	 */
	private void requestBuild(double camX, double eyeY, double camZ, double inner, double reach) {
		if (!hasSeed) return;
		if (!building.compareAndSet(false, true)) return;

		int startedGeneration = generation.get();
		long seedNow = seed;
		// Snapshot rather than share: both are read on the worker while the network thread may be
		// writing them. scars is a concurrent set, so copyOf sees a consistent-enough view; landmarks
		// is already immutable and only ever replaced wholesale.
		Set<Long> scarsNow = Set.copyOf(scars);
		List<HorizonLandmark> marksNow = nearestLandmarks(camX, camZ);

		BUILDER.execute(() -> {
			try {
				long startedNanos = System.nanoTime();
				PlanetSurfaceMesh built = PlanetSurfaceMesh.build(
						seedNow, camX, eyeY, camZ, inner, reach, scarsNow, marksNow);
				recordBuild(built, (System.nanoTime() - startedNanos) / 1_000_000L, camX, eyeY, camZ);

				// Clear the flag, publish, then check whether the world moved under us — in that
				// order, and not the obvious one. Checking first and clearing after loses an
				// invalidation that lands in between, and the horizon then stops updating until the
				// eye happens to move far enough to make the field stale on geometry alone.
				//
				// The field itself is published either way. A backdrop built from data one packet out
				// of date is not something a pilot can see; a missing backdrop is.
				dirty = false;
				mesh = built;
				if (generation.get() != startedGeneration) dirty = true;
			} catch (Throwable failure) {
				// Throwable rather than Exception, and recorded rather than logged: a build that dies
				// silently on a worker thread reads in-game as "the horizon just stopped updating",
				// which is the hardest kind of failure to attribute.
				DiagProblems.record("horizon", "rebuild failed on the worker thread: " + failure);
			} finally {
				building.set(false);
			}
		});
	}

	/** Timings and traces for one finished build. Called on the worker, so every field it writes is volatile. */
	private void recordBuild(PlanetSurfaceMesh built, long tookMillis, double camX, double eyeY, double camZ) {
		DiagTrace.count("horizon.rebuild");
		rebuilds++;
		// Counted always, written down only when it is worth reading. A rebuild fires every 64 blocks
		// of flight, which at this project's speeds is several a second: tracing all of them would
		// fill the buffer with routine work and push out the portal and flight events the trace
		// exists for. The first few show the horizon coming alive; after that only the slow ones
		// carry information.
		if (rebuilds <= 3 || tookMillis >= TRACE_REBUILD_MILLIS) {
			DiagTrace.record("horizon", "rebuilt " + built.quads + " quads in " + tookMillis + "ms at "
					+ Math.round(camX) + "," + Math.round(eyeY) + "," + Math.round(camZ));
		}
		lastRebuildMillis = tookMillis;
		if (tookMillis > worstRebuildMillis) worstRebuildMillis = tookMillis;
		// The threshold that used to mean "a dropped frame" now means "slow", because this no longer
		// runs in a frame. Kept because it is still the number that says whether the worker can keep
		// up with the eye, and lowering it would only hide that.
		if (tookMillis >= REBUILD_SLOW_MILLIS) {
			DiagProblems.record("horizon",
					"rebuild took " + tookMillis + "ms for " + built.quads + " quads");
		}
	}

	/**
	 * A rebuild at or over this many milliseconds is worth recording.
	 *
	 * <p>It used to mean "a dropped frame", because the build happened inside one. It now means the
	 * worker is falling behind the eye, which is a milder problem and still the one worth watching.
	 */
	private static final int REBUILD_SLOW_MILLIS = 20;
	/**
	 * A rebuild at or over this many milliseconds earns a line in the trace.
	 *
	 * <p>Lower than the stutter threshold on purpose: half a frame is not yet a problem but is already
	 * the thing to watch, and the trace is where a trend shows up before it becomes a complaint.
	 */
	private static final int TRACE_REBUILD_MILLIS = 8;

	// Written on the worker, read by the diagnostics report on the render thread.
	private volatile int rebuilds;
	private volatile long lastRebuildMillis = -1;
	private volatile long worstRebuildMillis = -1;

	public int rebuilds() { return rebuilds; }
	/** Milliseconds the last rebuild took, or -1 if the field has never been built. */
	public long lastRebuildMillis() { return lastRebuildMillis; }
	public long worstRebuildMillis() { return worstRebuildMillis; }
	/** The field currently built, or null before the first one — for diagnostics only. */
	public PlanetSurfaceMesh currentMesh() { return mesh; }
	/** Whether a rebuild is running right now — the row that explains a field lagging the eye. */
	public boolean building() { return building.get(); }

	/**
	 * Landmarks by distance from the eye.
	 *
	 * <p>Sorted here rather than in the builder so the budget spends itself on the ones a pilot can
	 * actually pick out: past a certain range two towers are the same two pixels.
	 */
	private List<HorizonLandmark> nearestLandmarks(double camX, double camZ) {
		if (landmarks.isEmpty()) return List.of();
		List<HorizonLandmark> sorted = new ArrayList<>(landmarks);
		sorted.sort((a, b) -> Double.compare(distanceSq(a, camX, camZ), distanceSq(b, camX, camZ)));
		return sorted;
	}

	private static double distanceSq(HorizonLandmark mark, double camX, double camZ) {
		double dx = mark.x() - camX;
		double dz = mark.z() - camZ;
		return dx * dx + dz * dz;
	}
}

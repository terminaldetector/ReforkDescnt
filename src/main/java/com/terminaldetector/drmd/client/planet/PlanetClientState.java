package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.diag.DiagProblems;
import com.terminaldetector.drmd.diag.DiagTrace;
import com.terminaldetector.drmd.world.store.MemorySectionStorage;
import com.terminaldetector.drmd.world.store.SectionKey;
import com.terminaldetector.drmd.world.store.SurfaceSection;
import com.terminaldetector.drmd.world.store.SurfaceStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Seed, scars, catalogued landmarks and the built field for the voxel horizon. */
public final class PlanetClientState {
	public static final PlanetClientState INSTANCE = new PlanetClientState();

	private long seed;
	private boolean hasSeed;
	private final Set<Long> scars = new HashSet<>();
	private List<HorizonLandmark> landmarks = List.of();
	private PlanetSurfaceMesh mesh;
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
		if (changed) mesh = null;
	}

	/** A section arrived. Anything already built is stale, because the ground under it changed. */
	public void acceptSection(long key, byte[] data) {
		SurfaceSection section = SurfaceSection.fromBytes(data);
		if (section == null) return;
		observed.put(key, section);
		mesh = null;
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
		mesh = null;
	}

	public boolean hasSeed() {
		return hasSeed;
	}

	public long seed() {
		return seed;
	}

	/**
	 * The field around this eye, rebuilt only when the last one no longer fits.
	 *
	 * <p>Timed, because this is now the horizon's whole cost. Drawing it is free between rebuilds — the
	 * field lives on the GPU — so a stutter while flying can only be this call, and guessing at that
	 * from a chair is exactly what the diagnostics report exists to stop.
	 */
	public PlanetSurfaceMesh mesh(double camX, double eyeY, double camZ, double inner, double reach) {
		PlanetSurfaceMesh current = mesh;
		if (current == null || current.stale(camX, eyeY, camZ, inner, reach)) {
			long startedNanos = System.nanoTime();
			current = PlanetSurfaceMesh.build(seed, camX, eyeY, camZ, inner, reach,
					scars, nearestLandmarks(camX, camZ));
			mesh = current;

			long tookMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
			DiagTrace.record("horizon", "rebuilt " + current.quads + " quads in " + tookMillis + "ms at "
					+ Math.round(camX) + "," + Math.round(eyeY) + "," + Math.round(camZ));
			DiagTrace.count("horizon.rebuild");
			rebuilds++;
			lastRebuildMillis = tookMillis;
			if (tookMillis > worstRebuildMillis) worstRebuildMillis = tookMillis;
			if (tookMillis >= REBUILD_STUTTER_MILLIS) {
				// Recorded rather than logged per rebuild: this fires while flying, so it would otherwise
				// be thousands of lines. DiagProblems counts repeats and keeps the worst visible.
				DiagProblems.record("horizon",
						"rebuild took " + tookMillis + "ms for " + current.quads + " quads — a dropped frame");
			}
		}
		return current;
	}

	/** A rebuild at or over this many milliseconds has cost a frame at 60fps, so it is worth recording. */
	private static final int REBUILD_STUTTER_MILLIS = 20;

	private int rebuilds;
	private long lastRebuildMillis = -1;
	private long worstRebuildMillis = -1;

	public int rebuilds() { return rebuilds; }
	/** Milliseconds the last rebuild took, or -1 if the field has never been built. */
	public long lastRebuildMillis() { return lastRebuildMillis; }
	public long worstRebuildMillis() { return worstRebuildMillis; }
	/** The field currently built, or null before the first one — for diagnostics only. */
	public PlanetSurfaceMesh currentMesh() { return mesh; }

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

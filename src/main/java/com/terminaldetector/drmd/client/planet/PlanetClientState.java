package com.terminaldetector.drmd.client.planet;

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

	public void clear() {
		hasSeed = false;
		seed = 0L;
		scars.clear();
		landmarks = List.of();
		mesh = null;
	}

	public boolean hasSeed() {
		return hasSeed;
	}

	public long seed() {
		return seed;
	}

	/** The field around this eye, rebuilt only when the last one no longer fits. */
	public PlanetSurfaceMesh mesh(double camX, double eyeY, double camZ, double inner, double reach) {
		PlanetSurfaceMesh current = mesh;
		if (current == null || current.stale(camX, eyeY, camZ, inner, reach)) {
			current = PlanetSurfaceMesh.build(seed, camX, eyeY, camZ, inner, reach,
					scars, nearestLandmarks(camX, camZ));
			mesh = current;
		}
		return current;
	}

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

package com.terminaldetector.drmd.client.planet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Seed, scars and the built surface for the planet under the End. */
public final class PlanetClientState {
	public static final PlanetClientState INSTANCE = new PlanetClientState();

	private long seed;
	private boolean hasSeed;
	private final Set<Long> scars = new HashSet<>();
	private PlanetSurfaceMesh mesh;

	private PlanetClientState() {}

	/**
	 * Server snapshot: the seed, and which cells a reactor burned.
	 *
	 * <p>The whole surface follows from the seed, so this arrives once on join and again only when
	 * the scar set changes — where the map it replaces streamed six hundred cells of height and tint
	 * every player tick.
	 */
	public void apply(long seed, List<Long> scarCells) {
		if (this.seed != seed || !hasSeed) {
			this.seed = seed;
			this.hasSeed = true;
			this.mesh = null;
		}
		if (scars.size() != scarCells.size() || !scars.containsAll(scarCells)) {
			scars.clear();
			scars.addAll(scarCells);
			this.mesh = null;
		}
	}

	public void clear() {
		hasSeed = false;
		seed = 0L;
		scars.clear();
		mesh = null;
	}

	public boolean hasSeed() {
		return hasSeed;
	}

	public long seed() {
		return seed;
	}

	/** The field around this position, rebuilt only when the last one no longer fits. */
	public PlanetSurfaceMesh mesh(double camX, double camZ, double inner, double outer) {
		PlanetSurfaceMesh current = mesh;
		if (current == null || current.stale(camX, camZ, inner, outer)) {
			current = PlanetSurfaceMesh.build(seed, camX, camZ, inner, outer, scars);
			mesh = current;
		}
		return current;
	}
}

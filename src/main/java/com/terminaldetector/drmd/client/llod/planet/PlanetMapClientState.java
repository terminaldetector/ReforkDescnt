package com.terminaldetector.drmd.client.llod.planet;

import com.terminaldetector.drmd.world.llod.planet.PlanetCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client cache of planetary map viewport (explored + procedural fog). */
public final class PlanetMapClientState {
	public static final PlanetMapClientState INSTANCE = new PlanetMapClientState();

	private List<PlanetCell> cells = List.of();
	private int originCx, originCz;
	private long seed;

	private PlanetMapClientState() {}

	public void set(List<PlanetCell> cells, int originCx, int originCz, long seed) {
		this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
		this.originCx = originCx;
		this.originCz = originCz;
		this.seed = seed;
	}

	public List<PlanetCell> cells() { return cells; }
	public int originCx() { return originCx; }
	public int originCz() { return originCz; }
	public long seed() { return seed; }

	public void clear() {
		cells = List.of();
	}
}

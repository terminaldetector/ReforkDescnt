package com.terminaldetector.drmd.world;

/**
 * Which parts of the world the mod currently builds.
 *
 * <p>The column, the megastructures and the 6DoF core were all written before any of it had run in
 * a real game, and when it finally did, the world content turned out to cost more than it was worth
 * while the flight model itself still needed work. Rather than delete that content, it is parked
 * here: one flag each, off by default, everything behind them left intact and compiling.
 *
 * <p>Flip a flag back to {@code true} to bring a piece back. Nothing else needs changing — the
 * dimension is still the full column, so the parked bands have somewhere to exist the moment they
 * are wanted again.
 */
public final class WorldFeatures {
	/**
	 * The Nether band at the bottom of the column: bedrock floor, basalt crust, lava seas, a capped
	 * ceiling and the pillars between them.
	 *
	 * <p>Parked. It is by far the most expensive thing the level builder does — roughly two thousand
	 * block writes in every single chunk, each one costing a lighting update — and it is the part of
	 * the column furthest from what is being worked on now.
	 */
	public static final boolean NETHER_BAND = false;

	/**
	 * The End band at the top: stone shards, the island shelf, and the reactor arena among them.
	 *
	 * <p>Kept. It is sparse — only scattered shards rather than a slab in every chunk — and it is
	 * where the reactor fight lives.
	 */
	public static final boolean END_BAND = true;

	/**
	 * Megastructures, industrial complexes, the megacity and the landmark seeding around spawn.
	 *
	 * <p>Parked. These are the largest single pieces of work the server does, and every one of them
	 * writes into chunks that are not loaded yet, so each forces terrain generation underneath
	 * itself before it can start.
	 */
	public static final boolean MACRO_WORLDGEN = false;

	private WorldFeatures() {}
}

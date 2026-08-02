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
	 * Custom End-band islands at the top of the Overworld column.
	 *
	 * <p>Parked: the upper layer should match stock Minecraft (empty high sky / no End-stone
	 * archipelago). The reactor fight still lives in the vanilla End dimension.
	 */
	public static final boolean END_BAND = false;

	/**
	 * Megastructures, industrial complexes, the megacity and the landmark seeding around spawn.
	 *
	 * <p>Parked. These are the largest single pieces of work the server does, and every one of them
	 * writes into chunks that are not loaded yet, so each forces terrain generation underneath
	 * itself before it can start.
	 */
	public static final boolean MACRO_WORLDGEN = false;

	/**
	 * Surface campaign without full macro WG2: spawn lunar hub + hub satellites, and
	 * biome plates elsewhere — {@code drmd:megacity}, {@code drmd:technogenic_sea},
	 * {@code drmd:scorched_lands} (not at spawn).
	 */
	public static final boolean SURFACE_DISTRICTS = true;

	/**
	 * Compile-time force for psychedelic fractal stock worlds.
	 * Prefer {@code config/drmd-server.properties} → {@code psychedelicWorlds=true}
	 * so worlds can opt in at generation without rebuilding the mod.
	 */
	public static final boolean PSYCHEDELIC_WORLDS = false;

	private WorldFeatures() {}
}

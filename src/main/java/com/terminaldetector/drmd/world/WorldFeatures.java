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
	 * The Nether / Core band + diggable mantle under −64.
	 *
	 * <p>On: streamed near diggers via {@code MantleStream} (HL2 fragment load). Bedrock is rewritten
	 * to plasma-resistant granite everywhere; full mantle/cavern fills only near pilots below industrial.
	 */
	public static final boolean NETHER_BAND = true;

	/**
	 * Custom End-band islands at the top of the Overworld column.
	 *
	 * <p>Parked: the upper layer should match stock Minecraft (empty high sky / no End-stone
	 * archipelago). The reactor fight still lives in the vanilla End dimension.
	 */
	public static final boolean END_BAND = false;

	/**
	 * Distant MacroWorld → LLOD silhouette cubes. Parked: sky presence is real Klondike voxels +
	 * Spark-style skybox ring, not mesh shells at 192–96k.
	 */
	public static final boolean MACRO_LLOD = false;

	/**
	 * Sparse Klondike floating islands (real blocks) in the sky band — replaces ARCH/RING/continent
	 * LLOD zoo for the Orbit layer.
	 */
	public static final boolean KLONDIKE_ISLANDS = true;

	/**
	 * Techno-ring satellites + junk plates at R≈2048. Parked so the Spark skybox ring is the ring
	 * (not competing abstract debris).
	 */
	public static final boolean ORBIT_JUNK = false;

	/**
	 * Megastructures / industrial complexes on CHUNK_LOAD (queued via landmark drain — not inline).
	 *
	 * <p>Was parked after spawn freezes; generators now go through {@code DescentSession} /
	 * {@code server.execute} with live-gen gating so distant flyover content returns.
	 */
	public static final boolean MACRO_WORLDGEN = true;

	/**
	 * Surface campaign without full macro WG2: spawn lunar hub + hub satellites, and
	 * biome plates elsewhere — {@code drmd:megacity}, {@code drmd:technogenic_sea},
	 * {@code drmd:scorched_lands}, {@code drmd:iron_guild} (not at spawn).
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

package com.terminaldetector.drmd.world;

/** Effective feature switches read by world-generation hooks. */
public final class WorldFeatures {
	/** Deep mantle and core band. */
	public static boolean NETHER_BAND = true;

	/** End band and reactor region. */
	public static boolean END_BAND = true;

	/** Sparse floating islands in the sky band. */
	public static boolean KLONDIKE_ISLANDS = true;

	/** Sparse orbit debris and techno-ring structures. */
	public static boolean ORBIT_JUNK = true;

	/** Large industrial structures and landmarks. */
	public static boolean MACRO_WORLDGEN = true;

	/** Surface biome districts and events. */
	public static boolean SURFACE_DISTRICTS = true;

	/** Compile-time compatibility switch; prefer {@code worldKind=PSYCHEDELIC}. */
	public static final boolean PSYCHEDELIC_WORLDS = false;

	private WorldFeatures() {}
}

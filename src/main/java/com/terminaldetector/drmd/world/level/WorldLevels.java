package com.terminaldetector.drmd.world.level;

/**
 * Tall Overworld bands — three layers form one parallelepiped in scale; display via hooks.
 *
 * <p>{@code overworld.json} keeps −512…1024. We do not build three solid cubes, and nothing marks
 * where one band ends: the boundaries are not teleported across and not drawn, so a climb through
 * the column is one continuous flight. Sky colour is a continuous function of altitude
 * ({@link com.terminaldetector.drmd.client.sky.LevelSky}); bedrock → plasma granite; mantle via
 * {@link MantleStream}.
 *
 * <pre>
 *   UPPER   Sky + Orbit + End     320 … 1024
 *   MIDDLE  Surface                40 …  320
 *   LOWER   Industrial + Abyss + Core  −512 … 40
 * </pre>
 */
public final class WorldLevels {
	private WorldLevels() {}

	/** Matches the dimension type override. Nothing may be written outside this. */
	public static final int WORLD_BOTTOM = -512;
	public static final int WORLD_TOP = 1024;

	public static final int NETHER_FLOOR = -420;
	public static final int NETHER_CEILING = -240;
	public static final int ABYSS_TOP = -64;
	public static final int INDUSTRIAL_TOP = 40;
	public static final int SURFACE_TOP = 320;
	public static final int SKY_TOP = 640;
	public static final int ORBITAL_TOP = 880;

	/** Thickness of the solid slabs that cap the Nether level. */
	public static final int NETHER_FLOOR_THICKNESS = 4;
	public static final int NETHER_CEILING_THICKNESS = 3;

	/** End-level islands hang in this band. */
	public static final int END_ISLAND_MIN = 900;
	public static final int END_ISLAND_MAX = 1000;

	/** One descent shaft every N chunks on each axis, so the levels stay reachable by flying. */
	public static final int SHAFT_CHUNK_SPACING = 8;
	public static final int SHAFT_RADIUS = 3;

	public enum Level {
		NETHER("Nether Level", WORLD_BOTTOM, NETHER_CEILING),
		ABYSS("Abyss", NETHER_CEILING, ABYSS_TOP),
		INDUSTRIAL("Industrial Depth", ABYSS_TOP, INDUSTRIAL_TOP),
		SURFACE("Surface Corridor", INDUSTRIAL_TOP, SURFACE_TOP),
		SKY("Sky Archipelago", SURFACE_TOP, SKY_TOP),
		ORBITAL("Orbital Belt", SKY_TOP, ORBITAL_TOP),
		END("End Level", ORBITAL_TOP, WORLD_TOP);

		public final String label;
		public final int yMin;
		public final int yMax;

		Level(String label, int yMin, int yMax) {
			this.label = label;
			this.yMin = yMin;
			this.yMax = yMax;
		}

		/** A comfortable arrival altitude when travelling to this level. */
		public int travelY() {
			return switch (this) {
				case NETHER -> NETHER_FLOOR + 30;
				case ABYSS -> (NETHER_CEILING + ABYSS_TOP) / 2;
				case INDUSTRIAL -> 0;
				case SURFACE -> 96;
				case SKY -> 420;
				case ORBITAL -> 720;
				case END -> END_ISLAND_MIN + 20;
			};
		}
	}

	public static Level at(double y) {
		if (y >= ORBITAL_TOP) return Level.END;
		if (y >= SKY_TOP) return Level.ORBITAL;
		if (y >= SURFACE_TOP) return Level.SKY;
		if (y >= INDUSTRIAL_TOP) return Level.SURFACE;
		if (y >= ABYSS_TOP) return Level.INDUSTRIAL;
		if (y >= NETHER_CEILING) return Level.ABYSS;
		return Level.NETHER;
	}

	public static Level byName(String name) {
		if (name == null) return Level.SURFACE;
		for (Level l : Level.values()) {
			if (l.name().equalsIgnoreCase(name)) return l;
		}
		return Level.SURFACE;
	}

	/** True where {@link LevelBuilder} should cut a shaft so the levels connect by flight. */
	public static boolean isShaftChunk(int chunkX, int chunkZ) {
		return Math.floorMod(chunkX, SHAFT_CHUNK_SPACING) == 0
				&& Math.floorMod(chunkZ, SHAFT_CHUNK_SPACING) == 0;
	}
}

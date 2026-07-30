package com.terminaldetector.drmd.world;

/**
 * World Generation 2.0 — vertical multi-scale universe.
 * Continuous flight from deep reactor complexes to End-space without loading screens.
 */
public final class WorldRules {
	private WorldRules() {}

	// ── Target vertical continuum (spec) ──
	public static final int Y_DEPTH_REACTORS = -50_000;
	public static final int Y_SURFACE = 0;
	public static final int Y_SKY_MIN = 5_000;
	public static final int Y_SKY_MAX = 20_000;
	public static final int Y_ORBITAL_MIN = 20_000;
	public static final int Y_ORBITAL_MAX = 60_000;
	public static final int Y_END_SPACE = 100_000;

	/** Practical Overworld band until custom dimension height is enabled. */
	public static final int GEN_Y_MIN = -64;
	public static final int GEN_Y_MAX = 320;
	public static final int INDUSTRIAL_Y_MIN = -56;
	public static final int INDUSTRIAL_Y_MAX = 40;
	public static final int SKY_PRACTICAL_MIN = 180;
	public static final int SKY_PRACTICAL_MAX = 300;

	public enum Layer {
		DEPTH_REACTORS("Depth Reactors / Nether-analog", Y_DEPTH_REACTORS, Y_SURFACE - 1),
		SURFACE("Classic Overworld", Y_SURFACE, Y_SKY_MIN - 1),
		SKY_ARCHIPELAGO("Celestial archipelagos", Y_SKY_MIN, Y_SKY_MAX),
		ORBITAL("Orbital megastructures", Y_ORBITAL_MIN, Y_ORBITAL_MAX),
		END_SPACE("End-space continuum", Y_END_SPACE, Y_END_SPACE + 50_000);

		public final String label;
		public final int yMin, yMax;

		Layer(String label, int yMin, int yMax) {
			this.label = label;
			this.yMin = yMin;
			this.yMax = yMax;
		}

		public static Layer at(double y) {
			if (y >= Y_END_SPACE) return END_SPACE;
			if (y >= Y_ORBITAL_MIN) return ORBITAL;
			if (y >= Y_SKY_MIN) return SKY_ARCHIPELAGO;
			if (y >= Y_SURFACE) return SURFACE;
			return DEPTH_REACTORS;
		}
	}

	/** Streaming / Voxel LLOD bands aligned with LlodLevel. */
	public enum StreamLevel {
		LLOD0,   // far silhouette — thousands of voxels
		LLOD1,   // large forms
		LLOD2,   // region proxies
		CHUNK,   // vanilla blocks
		LOCAL    // full local detail
	}

	public enum Architecture {
		SPHERE, RING, CYLINDER, TORUS, HONEYCOMB, ASTEROID, STATION, TUNNEL_NET,
		RIFT, CANYON, ARCH, FLOATING_CONTINENT, SPIRAL_RANGE, INVERTED_ISLAND
	}

	public enum SpacePrimitive {
		VERTICAL_SHAFT, SPIRAL, SPHERICAL_CHAMBER, INTERSECTION, LOOP, MULTILAYER_ROOM
	}

	public enum ComplexStyle {
		ABANDONED_RESEARCH, ANCIENT_POWER, AUTO_FACTORY, SMELTERY, CRYSTAL_REACTOR, TECH_RUINS
	}

	public enum ModuleType {
		REACTOR, HABITATION, STORAGE, POWER_SPINE, REPAIR_HANGAR,
		COMMAND, COOLING, EVAC_TUNNEL
	}

	/** Map speculative Y into practical Overworld height for prototype gen. */
	public static int practicalY(Layer layer, int seedMod) {
		return switch (layer) {
			case DEPTH_REACTORS -> INDUSTRIAL_Y_MIN + Math.floorMod(seedMod, 20);
			case SURFACE -> 64 + Math.floorMod(seedMod, 40);
			case SKY_ARCHIPELAGO -> SKY_PRACTICAL_MIN + Math.floorMod(seedMod, 80);
			case ORBITAL -> SKY_PRACTICAL_MAX - 20 + Math.floorMod(seedMod, 15);
			case END_SPACE -> SKY_PRACTICAL_MAX - 5;
		};
	}

	/**
	 * Resolve Descent "biome layer" from practical Overworld Y
	 * (atmosphere bands + industrial / sky generation bands).
	 */
	public static Layer practicalLayer(double y) {
		if (y < INDUSTRIAL_Y_MAX) return Layer.DEPTH_REACTORS;
		if (y < SKY_PRACTICAL_MIN) return Layer.SURFACE;
		if (y < SKY_PRACTICAL_MAX - 15) return Layer.SKY_ARCHIPELAGO;
		if (y < SKY_PRACTICAL_MAX - 2) return Layer.ORBITAL;
		return Layer.END_SPACE;
	}

	/** Short HUD / biome label for a practical layer. */
	public static String biomeLabel(Layer layer) {
		return switch (layer) {
			case DEPTH_REACTORS -> "Industrial Depth";
			case SURFACE -> "Surface Corridor";
			case SKY_ARCHIPELAGO -> "Sky Archipelago";
			case ORBITAL -> "Orbital Belt";
			case END_SPACE -> "Near-End Space";
		};
	}
}

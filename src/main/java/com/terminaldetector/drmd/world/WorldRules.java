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

	/**
	 * Practical build band. The custom dimension height is enabled by default
	 * ({@code resourcepacks/advanced_column/data/minecraft/dimension_type/overworld.json}, see
	 * {@link DrmdBuiltinPacks}) — on unless a world was created in
	 * {@link DrmdServerConfig.WorldModLevel#VANILLA} — so these mirror
	 * {@link com.terminaldetector.drmd.world.level.WorldLevels} rather than vanilla's −64…320.
	 */
	public static final int GEN_Y_MIN = com.terminaldetector.drmd.world.level.WorldLevels.WORLD_BOTTOM;
	public static final int GEN_Y_MAX = com.terminaldetector.drmd.world.level.WorldLevels.WORLD_TOP;
	/** Industrial complexes stay inside the vanilla stone shell so they cut into real terrain. */
	public static final int INDUSTRIAL_Y_MIN = -56;
	public static final int INDUSTRIAL_Y_MAX = com.terminaldetector.drmd.world.level.WorldLevels.INDUSTRIAL_TOP;
	public static final int SKY_PRACTICAL_MIN = com.terminaldetector.drmd.world.level.WorldLevels.SURFACE_TOP;
	public static final int SKY_PRACTICAL_MAX = com.terminaldetector.drmd.world.level.WorldLevels.ORBITAL_TOP;

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

	public enum Architecture {
		SPHERE, RING, CYLINDER, TORUS, HONEYCOMB, ASTEROID, STATION, TUNNEL_NET,
		RIFT, CANYON, ARCH, FLOATING_CONTINENT, SPIRAL_RANGE, INVERTED_ISLAND
	}

	public enum SpacePrimitive {
		VERTICAL_SHAFT, SPIRAL, SPHERICAL_CHAMBER, INTERSECTION, LOOP, MULTILAYER_ROOM
	}

	public enum ComplexStyle {
		ABANDONED_RESEARCH, ANCIENT_POWER, AUTO_FACTORY, SMELTERY, CRYSTAL_REACTOR, TECH_RUINS,
		/** Underwater 6DoF dungeon under technogenic sea locators. */
		SUBSEA_LOCATOR,
		/** Torus / ring cavity — megacity & sea satellites. */
		HOLLOW_RING,
		/** Tall shaft stack with stacked chambers. */
		VERTICAL_SPIRE,
		/** Asymmetric drifting lab nodes. */
		DRIFT_LAB,
		/** Locator-linked signal chambers + panel skin. */
		SIGNAL_ARRAY,
		/** Irregular warped cavern web. */
		WARPED_CAVERN,
		/** Thin orbital shell sphere with open poles. */
		ORBITAL_SHELL
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
			case SKY_ARCHIPELAGO -> SKY_PRACTICAL_MIN + Math.floorMod(seedMod, 240);
			case ORBITAL -> com.terminaldetector.drmd.world.level.WorldLevels.SKY_TOP
					+ Math.floorMod(seedMod, 200);
			case END_SPACE -> com.terminaldetector.drmd.world.level.WorldLevels.END_ISLAND_MIN
					+ Math.floorMod(seedMod, 80);
		};
	}

	/**
	 * Resolve Descent "biome layer" from practical Overworld Y
	 * (atmosphere bands + industrial / sky generation bands).
	 */
	public static Layer practicalLayer(double y) {
		return switch (com.terminaldetector.drmd.world.level.WorldLevels.at(y)) {
			case NETHER, ABYSS, INDUSTRIAL -> Layer.DEPTH_REACTORS;
			case SURFACE -> Layer.SURFACE;
			case SKY -> Layer.SKY_ARCHIPELAGO;
			case ORBITAL -> Layer.ORBITAL;
			case END -> Layer.END_SPACE;
		};
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

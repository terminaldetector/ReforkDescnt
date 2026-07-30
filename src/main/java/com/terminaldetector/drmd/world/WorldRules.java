package com.terminaldetector.drmd.world;

/**
 * Minecraft 6DoF — World Design Specification (core rules).
 *
 * Philosophy: there is no absolute "up" or "down".
 * The player builds space, not buildings. Volume (cavities) is gameplay.
 * This is not "Minecraft with flight" — it is a 3D voxel sandbox designed for 6DoF.
 */
public final class WorldRules {
	private WorldRules() {}

	// ── Height layers (continuous, no loading screens) ──
	public static final int LOWER_WORLD = -50_000;
	public static final int SURFACE = 0;
	public static final int HIGH_ISLANDS_MIN = 10_000;
	public static final int HIGH_ISLANDS_MAX = 40_000;
	public static final int SPACE = 50_000;
	public static final int END_LAYER = 100_000;

	/** Practical gen band used until custom dimension/chunkgen widens world height. */
	public static final int GEN_Y_MIN = -64;
	public static final int GEN_Y_MAX = 320;
	public static final int INDUSTRIAL_Y_MIN = -56;
	public static final int INDUSTRIAL_Y_MAX = 40;

	public enum Layer {
		LOWER_WORLD,
		SURFACE,
		HIGH_ISLANDS,
		SPACE,
		END
	}

	public static Layer layerAt(double y) {
		if (y >= END_LAYER) return Layer.END;
		if (y >= SPACE) return Layer.SPACE;
		if (y >= HIGH_ISLANDS_MIN) return Layer.HIGH_ISLANDS;
		if (y >= SURFACE) return Layer.SURFACE;
		return Layer.LOWER_WORLD;
	}

	/** Architecture vocabulary — preferred shapes over houses. */
	public enum Architecture {
		SPHERE, RING, CYLINDER, TORUS, HONEYCOMB, ASTEROID, STATION, TUNNEL_NET
	}

	/** Level-design primitives — every point needs ≥3 movement directions. */
	public enum SpacePrimitive {
		VERTICAL_SHAFT, SPIRAL, SPHERICAL_CHAMBER, INTERSECTION, LOOP, MULTILAYER_ROOM
	}

	/** Industrial Underground complex styles. */
	public enum ComplexStyle {
		ABANDONED_RESEARCH,
		ANCIENT_POWER,
		AUTO_FACTORY,
		SMELTERY,
		CRYSTAL_REACTOR,
		TECH_RUINS
	}

	/** Modular complex parts. */
	public enum ModuleType {
		REACTOR, HABITATION, STORAGE, POWER_SPINE, REPAIR_HANGAR,
		COMMAND, COOLING, EVAC_TUNNEL
	}
}

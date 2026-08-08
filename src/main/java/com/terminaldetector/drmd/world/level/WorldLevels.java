package com.terminaldetector.drmd.world.level;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Tall Overworld bands — three layers form one parallelepiped in scale; display via hooks.
 *
 * <p>{@code overworld.json} keeps −784…1888 — 2672 blocks, up from the original 1536, and still well
 * inside the engine's absolute ceiling (min_y ≥ −2032, height ≤ 4064, min_y+height ≤ 2032). We do not
 * build three solid cubes, and nothing marks where one band ends: the boundaries are not teleported
 * across and not drawn, so a climb through the column is one continuous flight. Sky colour is a
 * continuous function of altitude ({@link com.terminaldetector.drmd.client.sky.LevelSky}); bedrock →
 * plasma granite; mantle via {@link MantleStream}.
 *
 * <p><strong>Industrial and Surface do not grow with the rest.</strong> Vanilla's own terrain
 * generator is untouched by this mod (no {@code noise_settings} override) and always shapes ground
 * between Y −64 and 320 regardless of how tall the dimension is declared — that is where sea level,
 * villages and biomes actually sit. {@code ABYSS_TOP}(−64) and {@code SURFACE_TOP}(320) are pinned to
 * those exact numbers for that reason: moving them would not move a single block of real terrain,
 * only misplace the sky-tint plateau ({@link com.terminaldetector.drmd.client.sky.LevelSky}) and the
 * industrial/surface flavour split relative to ground that stays exactly where it always was. Every
 * other band is entirely this mod's own generated content, with nothing external pinning it, so those
 * five are free to grow — and very nearly doubled.
 *
 * <pre>
 *   END        Overworld End band, islands + reactor arena   1580 … 1888
 *   ORBITAL    Orbital Belt                                  1020 … 1580
 *   SKY        Sky Archipelago, Klondike islands               320 … 1020
 *   SURFACE    Ordinary play — vanilla ground, pinned           40 …  320
 *   INDUSTRIAL Underground flavour, pinned                     −64 …   40
 *   ABYSS      Solid mantle, descent shafts                   −324 …  −64
 *   NETHER     Relief band + base plug                        −784 … −324
 * </pre>
 */
public final class WorldLevels {
	private WorldLevels() {}

	/** Matches the dimension type override. Nothing may be written outside this. */
	public static final int WORLD_BOTTOM = -784;
	public static final int WORLD_TOP = 1888;

	public static final int NETHER_FLOOR = -684;
	public static final int NETHER_CEILING = -324;
	/** Pinned to vanilla's real generation floor — see the class doc. Do not rescale. */
	public static final int ABYSS_TOP = -64;
	public static final int INDUSTRIAL_TOP = 40;
	/** Pinned to vanilla's real generation ceiling — see the class doc. Do not rescale. */
	public static final int SURFACE_TOP = 320;
	public static final int SKY_TOP = 1020;
	public static final int ORBITAL_TOP = 1580;

	/** Thickness of the solid slabs that cap the Nether level. */
	public static final int NETHER_FLOOR_THICKNESS = 4;
	public static final int NETHER_CEILING_THICKNESS = 3;

	/** End-level islands hang in this band. */
	public static final int END_ISLAND_MIN = ORBITAL_TOP + 40;
	public static final int END_ISLAND_MAX = ORBITAL_TOP + 240;

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

		/**
		 * A comfortable arrival altitude when travelling to this level.
		 *
		 * <p>Every case is an offset from a band edge rather than an absolute Y, on purpose:
		 * {@code ORBITAL -> 720} was exactly such an absolute once, chosen when {@code ORBITAL}
		 * spanned 640…880 — comfortably 80 above its own floor. When the band moved to 1020…1580 the
		 * literal did not move with it, so {@code /d6 level orbital} would have landed inside the new
		 * Sky band instead, four hundred blocks under where it was supposed to arrive —
		 * {@code WorldLevelsTest.travelAltitudesAreInsideTheirBand} exists to catch exactly that.
		 * Absolute Y survives only on {@code SURFACE}, which is deliberately not band-relative: it
		 * targets vanilla's own sea level, which does not move when these bands do.
		 */
		public int travelY() {
			return switch (this) {
				case NETHER -> NETHER_FLOOR + 30;
				case ABYSS -> (NETHER_CEILING + ABYSS_TOP) / 2;
				case INDUSTRIAL -> (ABYSS_TOP + INDUSTRIAL_TOP) / 2;
				case SURFACE -> 96;
				case SKY -> SURFACE_TOP + 100;
				case ORBITAL -> SKY_TOP + 80;
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

	/**
	 * True when the running server's Overworld is this mod's tall column ({@link #WORLD_BOTTOM}..
	 * {@link #WORLD_TOP}), false for a genuinely vanilla-height one (Advanced vs Vanilla
	 * {@code DrmdServerConfig.WorldModLevel}). Always resolves through the Overworld regardless of
	 * which world {@code anyWorld} is — it is the only {@code dimension_type} this mod ever
	 * overrides, so a Nether/End world's own height says nothing about which mode is active.
	 *
	 * <p>Deliberately re-derived from the dimension registry on every call rather than cached or
	 * locked per-world the way {@code psychedelic}/{@code infiniteMegacity} are in
	 * {@code DescentWorldState}: those need a lock because they are pure bookkeeping with no
	 * independent ground truth, but column height already has an authoritative source of truth —
	 * the dimension registry, fixed forever at world creation — so there is nothing to lock, and
	 * re-deriving self-corrects if a second, differently configured world is loaded into the same
	 * running game, which a config-time-only flag could not.
	 */
	public static boolean isAdvancedColumn(ServerWorld anyWorld) {
		ServerWorld overworld = anyWorld.getServer().getWorld(World.OVERWORLD);
		if (overworld == null) return true;
		return overworld.getBottomY() == WORLD_BOTTOM && overworld.getHeight() == WORLD_TOP - WORLD_BOTTOM;
	}
}

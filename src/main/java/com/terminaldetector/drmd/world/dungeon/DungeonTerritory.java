package com.terminaldetector.drmd.world.dungeon;

import com.terminaldetector.drmd.world.WorldRules;

/**
 * Ownership map: biome plates / surface events → which 6DoF dungeon styles belong there.
 *
 * <p>Spawn hub is onboarding only — it does not own a dungeon campaign. Territory content is
 * discovered when the matching plate/event chunk loads.
 */
public final class DungeonTerritory {
	public enum Kind {
		/** Lunar pad at world spawn — no dungeon package. */
		SPAWN_HUB,
		/** Sparse random UFO / outpost / ruin events. */
		SURFACE_EVENT,
		/** {@code drmd:megacity} plate. */
		MEGACITY,
		/** {@code drmd:technogenic_sea} plate. */
		TECHNOGENIC_SEA,
		/** {@code drmd:scorched_lands} plate. */
		SCORCHED_LANDS
	}

	private DungeonTerritory() {}

	/** Primary under-plate / event dungeon style. */
	public static WorldRules.ComplexStyle primaryStyle(Kind kind, long salt) {
		return switch (kind) {
			case SPAWN_HUB -> WorldRules.ComplexStyle.ABANDONED_RESEARCH; // unused at hub
			case SURFACE_EVENT -> pick(salt, WorldRules.ComplexStyle.ABANDONED_RESEARCH,
					WorldRules.ComplexStyle.DRIFT_LAB, WorldRules.ComplexStyle.WARPED_CAVERN,
					WorldRules.ComplexStyle.AUTO_FACTORY, WorldRules.ComplexStyle.CRYSTAL_REACTOR);
			case MEGACITY -> pick(salt, WorldRules.ComplexStyle.TECH_RUINS,
					WorldRules.ComplexStyle.HOLLOW_RING, WorldRules.ComplexStyle.VERTICAL_SPIRE,
					WorldRules.ComplexStyle.ORBITAL_SHELL);
			case TECHNOGENIC_SEA -> pick(salt, WorldRules.ComplexStyle.SUBSEA_LOCATOR,
					WorldRules.ComplexStyle.SIGNAL_ARRAY, WorldRules.ComplexStyle.HOLLOW_RING);
			case SCORCHED_LANDS -> pick(salt, WorldRules.ComplexStyle.SMELTERY,
					WorldRules.ComplexStyle.ANCIENT_POWER, WorldRules.ComplexStyle.WARPED_CAVERN);
		};
	}

	/** Optional satellite dungeon style (second node on a plate). */
	public static WorldRules.ComplexStyle satelliteStyle(Kind kind, long salt) {
		return switch (kind) {
			case MEGACITY -> pick(salt ^ 0xC171L, WorldRules.ComplexStyle.DRIFT_LAB,
					WorldRules.ComplexStyle.AUTO_FACTORY, WorldRules.ComplexStyle.SIGNAL_ARRAY);
			case TECHNOGENIC_SEA -> pick(salt ^ 0x7EC4L, WorldRules.ComplexStyle.SIGNAL_ARRAY,
					WorldRules.ComplexStyle.DRIFT_LAB, WorldRules.ComplexStyle.CRYSTAL_REACTOR);
			case SCORCHED_LANDS -> pick(salt ^ 0x5C04L, WorldRules.ComplexStyle.ABANDONED_RESEARCH,
					WorldRules.ComplexStyle.SMELTERY);
			default -> primaryStyle(kind, salt ^ 1L);
		};
	}

	public static String label(Kind kind) {
		return switch (kind) {
			case SPAWN_HUB -> "Spawn hub (pad only)";
			case SURFACE_EVENT -> "Surface event";
			case MEGACITY -> "Megacity plate";
			case TECHNOGENIC_SEA -> "Technogenic sea";
			case SCORCHED_LANDS -> "Scorched lands";
		};
	}

	/**
	 * Vitality bias by territory — technogenic plates skew living;
	 * scorched / surface events more often dead or disguised.
	 */
	public static DungeonVitality vitality(Kind kind, long salt) {
		int roll = (int) Math.floorMod(salt >> 9, 100L);
		return switch (kind) {
			case TECHNOGENIC_SEA -> roll < 45 ? DungeonVitality.ALIVE
					: roll < 75 ? DungeonVitality.SEMI_ALIVE : DungeonVitality.DEAD;
			case MEGACITY -> roll < 35 ? DungeonVitality.ALIVE
					: roll < 70 ? DungeonVitality.SEMI_ALIVE : DungeonVitality.DEAD;
			case SCORCHED_LANDS -> roll < 15 ? DungeonVitality.ALIVE
					: roll < 45 ? DungeonVitality.SEMI_ALIVE : DungeonVitality.DEAD;
			case SURFACE_EVENT -> roll < 20 ? DungeonVitality.ALIVE
					: roll < 50 ? DungeonVitality.SEMI_ALIVE : DungeonVitality.DEAD;
			case SPAWN_HUB -> DungeonVitality.DEAD;
		};
	}

	@SafeVarargs
	private static WorldRules.ComplexStyle pick(long salt, WorldRules.ComplexStyle... styles) {
		if (styles.length == 0) return WorldRules.ComplexStyle.TECH_RUINS;
		return styles[(int) Math.floorMod(salt, styles.length)];
	}
}

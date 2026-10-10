package com.terminaldetector.drmd.world;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server / worldgen options loaded at mod init, and editable from the new "DRMD World Generation"
 * screen off the vanilla Create World menu ({@code DrmdWorldGenScreen}, opened via a button injected
 * by {@code CreateWorldScreenMixin}) instead of by hand-editing this file.
 *
 * <p>World-generation choices are global defaults and are locked into a save when seeded.
 * {@code cubicSnapshots} is a separate runtime opt-in: it writes supplemental 16³ observations and
 * does not replace the authoritative vanilla chunk engine.
 */
public final class DrmdServerConfig {
	private static final String FILE = DescentMod.MOD_ID + "-server.properties";
	private static FeatureSettings configuredFeatures = FeatureSettings.fromWorldFeatures();

	/** What a freshly created world generates as. */
	public enum WorldKind {
		/** The regular Descent campaign: spawn hub, biome plates, macro structures. */
		STOCK,
		/** Fractal void worlds with a weightless start ({@link com.terminaldetector.drmd.world.psychedelic.PsychedelicWorldgen}). */
		PSYCHEDELIC,
		/** Dense city tiled without limit in every direction — a bot/swarm-AI test arena. */
		INFINITE_MEGACITY
	}

	/** How much of the world this mod is allowed to change. */
	public enum WorldModLevel {
		/** Today's full tall column (-784..1888) and every layer/band in it. */
		ADVANCED,
		/**
		 * Genuinely vanilla-height Overworld, real unmodified Nether/End with normal portals — DRMD
		 * content layered on top with minimal world changes. Suppresses every effective
		 * {@link WorldFeatures} flag while retaining its configured value, and — once
		 * {@code DrmdBuiltinPacks} exists — controls whether the tall-column {@code dimension_type}
		 * override loads at all, which only takes effect on the next game launch.
		 */
		VANILLA
	}

	/** Feature choices retained in the config even while Vanilla mode temporarily suppresses them. */
	public enum WorldFeatureSetting {
		NETHER_BAND,
		END_BAND,
		KLONDIKE_ISLANDS,
		ORBIT_JUNK,
		MACRO_WORLDGEN,
		SURFACE_DISTRICTS
	}

	static record FeatureSettings(boolean netherBand, boolean endBand, boolean klondikeIslands,
								 boolean orbitJunk, boolean macroWorldgen, boolean surfaceDistricts) {
		static FeatureSettings fromWorldFeatures() {
			return new FeatureSettings(WorldFeatures.NETHER_BAND, WorldFeatures.END_BAND,
					WorldFeatures.KLONDIKE_ISLANDS, WorldFeatures.ORBIT_JUNK,
					WorldFeatures.MACRO_WORLDGEN, WorldFeatures.SURFACE_DISTRICTS);
		}

		static FeatureSettings disabled() {
			return new FeatureSettings(false, false, false, false, false, false);
		}

		boolean enabled(WorldFeatureSetting feature) {
			return switch (feature) {
				case NETHER_BAND -> netherBand;
				case END_BAND -> endBand;
				case KLONDIKE_ISLANDS -> klondikeIslands;
				case ORBIT_JUNK -> orbitJunk;
				case MACRO_WORLDGEN -> macroWorldgen;
				case SURFACE_DISTRICTS -> surfaceDistricts;
			};
		}

		FeatureSettings with(WorldFeatureSetting feature, boolean enabled) {
			return switch (feature) {
				case NETHER_BAND -> new FeatureSettings(enabled, endBand, klondikeIslands, orbitJunk, macroWorldgen, surfaceDistricts);
				case END_BAND -> new FeatureSettings(netherBand, enabled, klondikeIslands, orbitJunk, macroWorldgen, surfaceDistricts);
				case KLONDIKE_ISLANDS -> new FeatureSettings(netherBand, endBand, enabled, orbitJunk, macroWorldgen, surfaceDistricts);
				case ORBIT_JUNK -> new FeatureSettings(netherBand, endBand, klondikeIslands, enabled, macroWorldgen, surfaceDistricts);
				case MACRO_WORLDGEN -> new FeatureSettings(netherBand, endBand, klondikeIslands, orbitJunk, enabled, surfaceDistricts);
				case SURFACE_DISTRICTS -> new FeatureSettings(netherBand, endBand, klondikeIslands, orbitJunk, macroWorldgen, enabled);
			};
		}
	}

	/**
	 * When true, new Descent stock seeds become psychedelic fractal void worlds
	 * with a weightless start point (see {@link com.terminaldetector.drmd.world.psychedelic.PsychedelicWorldgen}).
	 *
	 * @deprecated superseded by {@link #worldKind}; kept so a {@code psychedelicWorlds=true} line in an
	 * existing {@code drmd-server.properties} still works after this field stopped being the only one.
	 */
	@Deprecated
	public static boolean psychedelicWorlds = false;

	public static WorldKind worldKind = WorldKind.STOCK;

	public static WorldModLevel worldModLevel = WorldModLevel.ADVANCED;

	/** Persist passive XYZ cube snapshots beside vanilla terrain; this does not replace vanilla chunks. */
	public static boolean cubicSnapshots = Boolean.getBoolean("drmd.cubicSnapshots");

	private static boolean loaded;

	private DrmdServerConfig() {}

	public static void load() {
		if (loaded) return;
		loaded = true;
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		if (!Files.exists(path)) {
			saveDefaults(path);
			return;
		}
		Properties props = new Properties();
		try (var in = Files.newInputStream(path)) {
			props.load(in);
		} catch (IOException e) {
			DescentMod.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
			return;
		}
		applyFrom(props);
		DescentMod.LOGGER.info("DRMD server config — worldKind={} worldModLevel={} netherBand={} endBand={} "
						+ "klondikeIslands={} macroWorldgen={} surfaceDistricts={} orbitJunk={}",
				worldKind, worldModLevel, WorldFeatures.NETHER_BAND, WorldFeatures.END_BAND, WorldFeatures.KLONDIKE_ISLANDS,
				WorldFeatures.MACRO_WORLDGEN, WorldFeatures.SURFACE_DISTRICTS, WorldFeatures.ORBIT_JUNK);
	}

	/**
	 * Parse {@code props} into the static fields and apply the six configured feature choices,
	 * suppressed when the selected world height is Vanilla.
	 */
	private static void applyFrom(Properties props) {
		psychedelicWorlds = Boolean.parseBoolean(props.getProperty("psychedelicWorlds", "false"));
		String kind = props.getProperty("worldKind");
		if (kind != null) {
			worldKind = parseKind(kind, psychedelicWorlds ? WorldKind.PSYCHEDELIC : WorldKind.STOCK);
		} else {
			// No worldKind line at all: an old properties file, or a hand-written one. Fall back to
			// the legacy boolean so it keeps meaning what it always meant.
			worldKind = psychedelicWorlds ? WorldKind.PSYCHEDELIC : WorldKind.STOCK;
		}
		worldModLevel = parseModLevel(props.getProperty("worldModLevel"), WorldModLevel.ADVANCED);
		cubicSnapshots = parseCubicSnapshots(props, Boolean.getBoolean("drmd.cubicSnapshots"));
		configuredFeatures = parseFeatureSettings(props, FeatureSettings.fromWorldFeatures());

		// Forced here, not only at seedWorld: MultiNoiseBiomeSourceMixin reads SURFACE_DISTRICTS
		// during spawn-chunk pregeneration, which runs before SERVER_STARTED ever fires.
		forceFeaturesFor(worldModLevel != WorldModLevel.VANILLA);
	}

	/**
	 * Applies the configured {@link WorldFeatures} unless {@code advancedColumn} is false. The saved
	 * choices are retained while a world suppresses them. Shared by {@link #applyFrom}, the settings
	 * screen save paths, and {@code DescentMod}'s {@code SERVER_STARTED} handler,
	 * which calls this with the actually-loaded world's own ground truth
	 * ({@code WorldLevels.isAdvancedColumn}) so a loaded save always wins over whatever this config
	 * guessed at mod-init time — e.g. an existing Advanced save opened after the config was flipped
	 * to Vanilla for the *next* world, or vice versa.
	 */
	public static void forceFeaturesFor(boolean advancedColumn) {
		applyFeatures(effectiveFeaturesFor(configuredFeatures, advancedColumn));
	}

	static FeatureSettings parseFeatureSettings(Properties props, FeatureSettings fallback) {
		return new FeatureSettings(
				parseBool(props, "netherBand", fallback.netherBand()),
				parseBool(props, "endBand", fallback.endBand()),
				parseBool(props, "klondikeIslands", fallback.klondikeIslands()),
				parseBool(props, "orbitJunk", fallback.orbitJunk()),
				parseBool(props, "macroWorldgen", fallback.macroWorldgen()),
				parseBool(props, "surfaceDistricts", fallback.surfaceDistricts()));
	}

	static FeatureSettings effectiveFeaturesFor(FeatureSettings configured, boolean advancedColumn) {
		return advancedColumn ? configured : FeatureSettings.disabled();
	}

	private static void applyFeatures(FeatureSettings settings) {
		WorldFeatures.NETHER_BAND = settings.netherBand();
		WorldFeatures.END_BAND = settings.endBand();
		WorldFeatures.KLONDIKE_ISLANDS = settings.klondikeIslands();
		WorldFeatures.ORBIT_JUNK = settings.orbitJunk();
		WorldFeatures.MACRO_WORLDGEN = settings.macroWorldgen();
		WorldFeatures.SURFACE_DISTRICTS = settings.surfaceDistricts();
	}

	/** Returns the user's saved choice, independent of any world-specific suppression. */
	public static boolean configuredFeature(WorldFeatureSetting feature) {
		load();
		return configuredFeatures.enabled(feature);
	}

	private static boolean parseBool(Properties props, String key, boolean fallback) {
		String v = props.getProperty(key);
		return v == null ? fallback : Boolean.parseBoolean(v);
	}

	static boolean parseCubicSnapshots(Properties props, boolean legacyJvmFallback) {
		return parseBool(props, "cubicSnapshots", legacyJvmFallback);
	}

	/** {@code WorldKind.valueOf} without throwing on a stale/typo'd value from a hand-edited file. */
	static WorldKind parseKind(String raw, WorldKind fallback) {
		try {
			return WorldKind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			DescentMod.LOGGER.warn("Unknown worldKind '{}' in {}, defaulting to {}", raw, FILE, fallback);
			return fallback;
		}
	}

	/** {@code WorldModLevel.valueOf} without throwing on a stale/typo'd/missing value. */
	static WorldModLevel parseModLevel(String raw, WorldModLevel fallback) {
		if (raw == null) return fallback;
		try {
			return WorldModLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			DescentMod.LOGGER.warn("Unknown worldModLevel '{}' in {}, defaulting to {}", raw, FILE, fallback);
			return fallback;
		}
	}

	private static void saveDefaults(Path path) {
		Properties props = new Properties();
		props.setProperty("worldKind", WorldKind.STOCK.name());
		props.setProperty("worldModLevel", WorldModLevel.ADVANCED.name());
		props.setProperty("cubicSnapshots", String.valueOf(cubicSnapshots));
		props.setProperty("psychedelicWorlds", "false");
		props.setProperty("netherBand", String.valueOf(configuredFeatures.netherBand()));
		props.setProperty("endBand", String.valueOf(configuredFeatures.endBand()));
		props.setProperty("klondikeIslands", String.valueOf(configuredFeatures.klondikeIslands()));
		props.setProperty("orbitJunk", String.valueOf(configuredFeatures.orbitJunk()));
		props.setProperty("macroWorldgen", String.valueOf(configuredFeatures.macroWorldgen()));
		props.setProperty("surfaceDistricts", String.valueOf(configuredFeatures.surfaceDistricts()));
		writeFile(path, props);
	}

	/**
	 * Persist world-generation selections. World type affects the next seeded save, height mode changes
	 * the built-in data-pack selection on next launch, and cubic snapshots begin on the next server tick.
	 */
	public static void saveWorldSelection(WorldKind kind, WorldModLevel modLevel, boolean cubicSnapshots) {
		load();
		worldKind = kind;
		worldModLevel = modLevel;
		DrmdServerConfig.cubicSnapshots = cubicSnapshots;
		psychedelicWorlds = kind == WorldKind.PSYCHEDELIC;
		forceFeaturesFor(worldModLevel != WorldModLevel.VANILLA);
		persist();
	}

	public static void saveFeature(WorldFeatureSetting feature, boolean enabled) {
		load();
		configuredFeatures = configuredFeatures.with(feature, enabled);
		forceFeaturesFor(worldModLevel != WorldModLevel.VANILLA);
		persist();
	}

	private static void persist() {
		Properties props = new Properties();
		props.setProperty("worldKind", worldKind.name());
		props.setProperty("worldModLevel", worldModLevel.name());
		props.setProperty("cubicSnapshots", String.valueOf(cubicSnapshots));
		props.setProperty("psychedelicWorlds", String.valueOf(psychedelicWorlds));
		props.setProperty("netherBand", String.valueOf(configuredFeatures.netherBand()));
		props.setProperty("endBand", String.valueOf(configuredFeatures.endBand()));
		props.setProperty("klondikeIslands", String.valueOf(configuredFeatures.klondikeIslands()));
		props.setProperty("orbitJunk", String.valueOf(configuredFeatures.orbitJunk()));
		props.setProperty("macroWorldgen", String.valueOf(configuredFeatures.macroWorldgen()));
		props.setProperty("surfaceDistricts", String.valueOf(configuredFeatures.surfaceDistricts()));
		writeFile(FabricLoader.getInstance().getConfigDir().resolve(FILE), props);
	}

	private static void writeFile(Path path, Properties props) {
		try {
			Files.createDirectories(path.getParent());
			try (var out = Files.newOutputStream(path)) {
				props.store(out, """
						DRMD 6DOF server / worldgen options — editable from the DRMD World Generation
						screen off the vanilla Create World menu, or by hand here.
						worldKind = STOCK | PSYCHEDELIC | INFINITE_MEGACITY
						worldModLevel = ADVANCED | VANILLA
						""");
			}
		} catch (IOException e) {
			DescentMod.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
		}
	}

	/** Effective stock psychedelic flag (config or compile-time WorldFeatures). */
	public static boolean psychedelicEnabled() {
		load();
		return worldKind == WorldKind.PSYCHEDELIC || WorldFeatures.PSYCHEDELIC_WORLDS;
	}

	/** Effective infinite-megacity flag. No compile-time equivalent — this mode is new. */
	public static boolean infiniteMegacityEnabled() {
		load();
		return worldKind == WorldKind.INFINITE_MEGACITY;
	}

	/** True when the configured level is {@link WorldModLevel#VANILLA}. */
	public static boolean vanillaModLevel() {
		load();
		return worldModLevel == WorldModLevel.VANILLA;
	}
}

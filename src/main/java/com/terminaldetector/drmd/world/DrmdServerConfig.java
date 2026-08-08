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
 * <p>These settings are global — one {@code config/drmd-server.properties}, not one per world — which
 * is what "GUI-editable before creating a world" could reach without also making every
 * {@link WorldFeatures} read site (chunk-load listeners, {@code DescentSession}, the biome-source
 * mixin) world-aware. The choice a player makes here becomes the default for the *next* world created
 * or first loaded; {@code DescentSession.seedWorld} locks the resolved {@link WorldKind} into that
 * world's own {@link DescentWorldState} at first seed, exactly as it already did for the one existing
 * toggle ({@code psychedelic}), so a later change here never reaches back into an existing save.
 */
public final class DrmdServerConfig {
	private static final String FILE = DescentMod.MOD_ID + "-server.properties";

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
		 * content layered on top with minimal world changes. Forces every {@link WorldFeatures} flag
		 * off (see {@link #applyFrom}) regardless of their individually-saved values, and — once
		 * {@code DrmdBuiltinPacks} exists — controls whether the tall-column {@code dimension_type}
		 * override loads at all, which only takes effect on the next game launch.
		 */
		VANILLA
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
	 * Parse {@code props} into the static fields and push the six {@link WorldFeatures} flags —
	 * shared by {@link #load()} and {@link #save}, so writing from the new screen takes effect
	 * immediately without a restart.
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
		WorldFeatures.NETHER_BAND = parseBool(props, "netherBand", WorldFeatures.NETHER_BAND);
		WorldFeatures.END_BAND = parseBool(props, "endBand", WorldFeatures.END_BAND);
		WorldFeatures.KLONDIKE_ISLANDS = parseBool(props, "klondikeIslands", WorldFeatures.KLONDIKE_ISLANDS);
		WorldFeatures.ORBIT_JUNK = parseBool(props, "orbitJunk", WorldFeatures.ORBIT_JUNK);
		WorldFeatures.MACRO_WORLDGEN = parseBool(props, "macroWorldgen", WorldFeatures.MACRO_WORLDGEN);
		WorldFeatures.SURFACE_DISTRICTS = parseBool(props, "surfaceDistricts", WorldFeatures.SURFACE_DISTRICTS);

		// Forced here, not only at seedWorld: MultiNoiseBiomeSourceMixin reads SURFACE_DISTRICTS
		// during spawn-chunk pregeneration, which runs before SERVER_STARTED ever fires.
		forceFeaturesFor(worldModLevel != WorldModLevel.VANILLA);
	}

	/**
	 * Forces every {@link WorldFeatures} flag off unless {@code advancedColumn} — shared by
	 * {@link #applyFrom}, {@link #save}, and {@code DescentMod}'s {@code SERVER_STARTED} handler,
	 * which calls this with the actually-loaded world's own ground truth
	 * ({@code WorldLevels.isAdvancedColumn}) so a loaded save always wins over whatever this config
	 * guessed at mod-init time — e.g. an existing Advanced save opened after the config was flipped
	 * to Vanilla for the *next* world, or vice versa.
	 */
	public static void forceFeaturesFor(boolean advancedColumn) {
		if (advancedColumn) return;
		WorldFeatures.NETHER_BAND = false;
		WorldFeatures.END_BAND = false;
		WorldFeatures.KLONDIKE_ISLANDS = false;
		WorldFeatures.ORBIT_JUNK = false;
		WorldFeatures.MACRO_WORLDGEN = false;
		WorldFeatures.SURFACE_DISTRICTS = false;
	}

	private static boolean parseBool(Properties props, String key, boolean fallback) {
		String v = props.getProperty(key);
		return v == null ? fallback : Boolean.parseBoolean(v);
	}

	/** {@code WorldKind.valueOf} without throwing on a stale/typo'd value from a hand-edited file. */
	private static WorldKind parseKind(String raw, WorldKind fallback) {
		try {
			return WorldKind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			DescentMod.LOGGER.warn("Unknown worldKind '{}' in {}, defaulting to {}", raw, FILE, fallback);
			return fallback;
		}
	}

	/** {@code WorldModLevel.valueOf} without throwing on a stale/typo'd/missing value. */
	private static WorldModLevel parseModLevel(String raw, WorldModLevel fallback) {
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
		props.setProperty("psychedelicWorlds", "false");
		props.setProperty("netherBand", String.valueOf(WorldFeatures.NETHER_BAND));
		props.setProperty("endBand", String.valueOf(WorldFeatures.END_BAND));
		props.setProperty("klondikeIslands", String.valueOf(WorldFeatures.KLONDIKE_ISLANDS));
		props.setProperty("orbitJunk", String.valueOf(WorldFeatures.ORBIT_JUNK));
		props.setProperty("macroWorldgen", String.valueOf(WorldFeatures.MACRO_WORLDGEN));
		props.setProperty("surfaceDistricts", String.valueOf(WorldFeatures.SURFACE_DISTRICTS));
		writeFile(path, props);
	}

	/**
	 * Persist the current in-memory settings, called from {@code DrmdWorldGenScreen} on close.
	 * Applies immediately (no restart needed) and rewrites the file — except {@code modLevel} itself,
	 * whose effect on the built-in resource pack's activation type only lands on the next game launch
	 * (see the {@link WorldModLevel#VANILLA} doc and {@code DrmdWorldGenScreen}'s class doc).
	 */
	public static void save(WorldKind kind, WorldModLevel modLevel, boolean netherBand, boolean endBand,
							 boolean klondikeIslands, boolean orbitJunk, boolean macroWorldgen,
							 boolean surfaceDistricts) {
		load(); // ensure `loaded`, so a save before any world exists still sticks
		worldKind = kind;
		worldModLevel = modLevel;
		psychedelicWorlds = kind == WorldKind.PSYCHEDELIC;
		WorldFeatures.NETHER_BAND = netherBand;
		WorldFeatures.END_BAND = endBand;
		WorldFeatures.KLONDIKE_ISLANDS = klondikeIslands;
		WorldFeatures.ORBIT_JUNK = orbitJunk;
		WorldFeatures.MACRO_WORLDGEN = macroWorldgen;
		WorldFeatures.SURFACE_DISTRICTS = surfaceDistricts;
		forceFeaturesFor(worldModLevel != WorldModLevel.VANILLA);

		Properties props = new Properties();
		props.setProperty("worldKind", worldKind.name());
		props.setProperty("worldModLevel", worldModLevel.name());
		props.setProperty("psychedelicWorlds", String.valueOf(psychedelicWorlds));
		props.setProperty("netherBand", String.valueOf(WorldFeatures.NETHER_BAND));
		props.setProperty("endBand", String.valueOf(WorldFeatures.END_BAND));
		props.setProperty("klondikeIslands", String.valueOf(WorldFeatures.KLONDIKE_ISLANDS));
		props.setProperty("orbitJunk", String.valueOf(WorldFeatures.ORBIT_JUNK));
		props.setProperty("macroWorldgen", String.valueOf(WorldFeatures.MACRO_WORLDGEN));
		props.setProperty("surfaceDistricts", String.valueOf(WorldFeatures.SURFACE_DISTRICTS));
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

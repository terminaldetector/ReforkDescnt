package com.terminaldetector.drmd.world;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server / worldgen options loaded at mod init.
 *
 * <p>{@code psychedelicWorlds} is the stock toggle for fractal void campaigns —
 * set before creating or first-loading a world ({@code config/drmd-server.properties}).
 */
public final class DrmdServerConfig {
	private static final String FILE = DescentMod.MOD_ID + "-server.properties";

	/**
	 * When true, new Descent stock seeds become psychedelic fractal void worlds
	 * with a weightless start point (see {@link com.terminaldetector.drmd.world.psychedelic.PsychedelicWorldgen}).
	 */
	public static boolean psychedelicWorlds = false;

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
		psychedelicWorlds = Boolean.parseBoolean(props.getProperty("psychedelicWorlds", "false"));
		DescentMod.LOGGER.info("DRMD server config — psychedelicWorlds={}", psychedelicWorlds);
	}

	private static void saveDefaults(Path path) {
		Properties props = new Properties();
		props.setProperty("psychedelicWorlds", "false");
		try {
			Files.createDirectories(path.getParent());
			try (var out = Files.newOutputStream(path)) {
				props.store(out, """
						DRMD 6DOF server / worldgen options
						psychedelicWorlds=true — stock fractal void worlds (10–20 fractal kinds), weightless spawn
						""");
			}
		} catch (IOException e) {
			DescentMod.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
		}
	}

	/** Effective stock psychedelic flag (config or compile-time WorldFeatures). */
	public static boolean psychedelicEnabled() {
		load();
		return psychedelicWorlds || WorldFeatures.PSYCHEDELIC_WORLDS;
	}
}

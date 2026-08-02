package com.terminaldetector.drmd.client.config;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client-side mod options, reachable from the pause menu.
 *
 * <p>Deliberately a flat properties file rather than a config library: these are a dozen scalars
 * that only the local client reads, and a dependency for that would cost more than it saves.
 * Toggles save immediately; the settings screen also writes on close.
 */
public final class DescentConfig {
	/** Draw the 3D cockpit frame in first person. */
	public static boolean cockpit = true;
	/** Cockpit opacity, 0.2..1.0. */
	public static float cockpitOpacity = 1.0f;
	/** Draw the instrument panel inside the cockpit. */
	public static boolean cockpitInstruments = true;
	/** Draw the flat telemetry HUD. */
	public static boolean hud = true;
	/** Blend sky and fog by level band. */
	public static boolean levelSky = true;
	/** Spark-style planet + dark ring + neon-green halo (skybox, not R=2048 junk). */
	public static boolean orbitalBeltSky = true;
	/** Ship roll rate, degrees per second. */
	public static float rollRate = 175f;
	/** Mouse gain for ship attitude, relative to vanilla look. */
	public static float lookGain = 1.0f;
	/** Camera lag / vibration / FOV stretch under thrust. */
	public static boolean cameraShake = true;
	/** Draw the first-person weapon clusters. */
	public static boolean weaponView = true;
	/**
	 * Fall-aftermath view — corkscrew pitch bias toward planet after orbital reactor detonation
	 * so pilots can inspect meteor scars on the surface.
	 */
	public static boolean fallAftermath = false;

	private static final String FILE = DescentMod.MOD_ID + ".properties";
	private static boolean loaded;

	private DescentConfig() {}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	/** First load at client init — no-op if already loaded. */
	public static void load() {
		if (loaded) return;
		reload();
	}

	/** Always re-read {@code drmd.properties} into the static fields. */
	public static void reload() {
		loaded = true;
		Path p = path();
		if (!Files.exists(p)) return;
		Properties props = new Properties();
		try (var in = Files.newInputStream(p)) {
			props.load(in);
		} catch (IOException e) {
			DescentMod.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
			return;
		}
		cockpit = bool(props, "cockpit", cockpit);
		cockpitOpacity = clamp(num(props, "cockpitOpacity", cockpitOpacity), 0.2f, 1f);
		cockpitInstruments = bool(props, "cockpitInstruments", cockpitInstruments);
		hud = bool(props, "hud", hud);
		levelSky = bool(props, "levelSky", levelSky);
		orbitalBeltSky = bool(props, "orbitalBeltSky", orbitalBeltSky);
		rollRate = clamp(num(props, "rollRate", rollRate), 40f, 400f);
		lookGain = clamp(num(props, "lookGain", lookGain), 0.25f, 3f);
		cameraShake = bool(props, "cameraShake", cameraShake);
		weaponView = bool(props, "weaponView", weaponView);
		fallAftermath = bool(props, "fallAftermath", fallAftermath);
	}

	public static void save() {
		Properties props = new Properties();
		props.setProperty("cockpit", Boolean.toString(cockpit));
		props.setProperty("cockpitOpacity", Float.toString(cockpitOpacity));
		props.setProperty("cockpitInstruments", Boolean.toString(cockpitInstruments));
		props.setProperty("hud", Boolean.toString(hud));
		props.setProperty("levelSky", Boolean.toString(levelSky));
		props.setProperty("orbitalBeltSky", Boolean.toString(orbitalBeltSky));
		props.setProperty("rollRate", Float.toString(rollRate));
		props.setProperty("lookGain", Float.toString(lookGain));
		props.setProperty("cameraShake", Boolean.toString(cameraShake));
		props.setProperty("weaponView", Boolean.toString(weaponView));
		props.setProperty("fallAftermath", Boolean.toString(fallAftermath));
		try {
			Files.createDirectories(path().getParent());
			try (var out = Files.newOutputStream(path())) {
				props.store(out, "DRMD 6DOF client options");
			}
		} catch (IOException e) {
			DescentMod.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
		}
	}

	private static boolean bool(Properties p, String key, boolean def) {
		String v = p.getProperty(key);
		return v == null ? def : Boolean.parseBoolean(v);
	}

	private static float num(Properties p, String key, float def) {
		String v = p.getProperty(key);
		if (v == null) return def;
		try {
			return Float.parseFloat(v);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}

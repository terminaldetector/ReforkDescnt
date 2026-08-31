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
	/**
	 * Draw the planet below the End band — the surface map, scaled toward the camera.
	 *
	 * <p>Off means the band looks down on empty sky, as it did before the floor existed.
	 */
	public static boolean planetFloor = true;
	/**
	 * Draw the Sky UFO hull as an interpolated virtual mesh instead of leaving it invisible while its
	 * real blocks are hidden mid-flight — the escape hatch for this render rewrite's single biggest
	 * live-client-only unknown (does the interpolation actually read as smooth, does the flat-colour
	 * mesh sit acceptably next to real terrain). Off means a materialized, flying UFO simply has no
	 * visible hull at all, same as toggling {@link #planetFloor} off leaves the End band looking at
	 * empty sky.
	 */
	public static boolean skyUfoVirtualHull = true;
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
	/**
	 * Native see-through mirror reflection (Phase R1a of the portal-rendering plan) — a recursive,
	 * camera-reflected re-render of the world, drawn into an off-screen target and blitted back through
	 * a scissor clipped to the mirror.
	 *
	 * <p>The reflection is clipped to the mirror's screen rectangle, so it appears roughly on the mirror
	 * rather than over the whole view. Roughly, because a rectangle is not the mirror's outline: head-on
	 * the two nearly coincide, at a steep angle the box is bigger than the face and the reflection spills
	 * past its edges. The exact shape needs a custom shader DRMD has no infrastructure for yet, and the
	 * blit also ignores depth, so something standing between you and the mirror will not hide it.
	 *
	 * <p>What to look for: the world seen from behind the mirror, mirrored left-to-right, turning the
	 * right way as you move. Wrong position or orientation points at {@code MirrorReflectionRenderer}'s
	 * matrix reconstruction; a black or unchanged mirror points at the off-screen render itself; a
	 * correct image in the wrong place on screen points at {@code MirrorScreenBounds}.
	 *
	 * <p>Defaults off, unlike every other toggle here, and stays off until deliberately switched on:
	 * this is the first feature in the project where passing CI says nothing about whether it works.
	 * Off means mirrors behave exactly as before (bounce lasers, nothing else).
	 */
	public static boolean mirrorReflection = false;

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
		planetFloor = bool(props, "planetFloor", planetFloor);
		skyUfoVirtualHull = bool(props, "skyUfoVirtualHull", skyUfoVirtualHull);
		rollRate = clamp(num(props, "rollRate", rollRate), 40f, 400f);
		lookGain = clamp(num(props, "lookGain", lookGain), 0.25f, 3f);
		cameraShake = bool(props, "cameraShake", cameraShake);
		weaponView = bool(props, "weaponView", weaponView);
		fallAftermath = bool(props, "fallAftermath", fallAftermath);
		mirrorReflection = bool(props, "mirrorReflection", mirrorReflection);
	}

	public static void save() {
		Properties props = new Properties();
		props.setProperty("cockpit", Boolean.toString(cockpit));
		props.setProperty("cockpitOpacity", Float.toString(cockpitOpacity));
		props.setProperty("cockpitInstruments", Boolean.toString(cockpitInstruments));
		props.setProperty("hud", Boolean.toString(hud));
		props.setProperty("levelSky", Boolean.toString(levelSky));
		props.setProperty("planetFloor", Boolean.toString(planetFloor));
		props.setProperty("skyUfoVirtualHull", Boolean.toString(skyUfoVirtualHull));
		props.setProperty("rollRate", Float.toString(rollRate));
		props.setProperty("lookGain", Float.toString(lookGain));
		props.setProperty("cameraShake", Boolean.toString(cameraShake));
		props.setProperty("weaponView", Boolean.toString(weaponView));
		props.setProperty("fallAftermath", Boolean.toString(fallAftermath));
		props.setProperty("mirrorReflection", Boolean.toString(mirrorReflection));
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

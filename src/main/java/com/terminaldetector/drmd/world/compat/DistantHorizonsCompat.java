package com.terminaldetector.drmd.world.compat;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Soft-dep on <a href="https://modrinth.com/mod/distanthorizons">Distant Horizons</a>.
 *
 * <p>DRMD no longer draws its own far-field voxel LLOD. Extreme view distance without the
 * CPU/GPU cost of real chunks belongs to DH (LOD meshes outside vanilla render distance).
 * Install DH + Fabric API (+ Sodium recommended) alongside this mod.
 */
public final class DistantHorizonsCompat {
	private static Boolean present;

	private DistantHorizonsCompat() {}

	public static boolean isPresent() {
		if (present == null) {
			present = FabricLoader.getInstance().isModLoaded("distanthorizons")
					|| FabricLoader.getInstance().isModLoaded("distant_horizons")
					|| FabricLoader.getInstance().isModLoaded("DistantHorizons");
		}
		return present;
	}

	public static void logStatus() {
		if (isPresent()) {
			DescentMod.LOGGER.info("Distant Horizons detected — far LODs delegated to DH (DRMD voxel LLOD off)");
		} else {
			DescentMod.LOGGER.info(
					"Distant Horizons not installed — install modrinth.com/mod/distanthorizons for max view distance");
		}
	}
}

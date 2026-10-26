package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/** Scorched / bombed lands — ash trees, ruined villages, small crater towns. */
public final class ScorchedLandsRegions {
	public static final BiomePlateRegions PLATE = new BiomePlateRegions(
			Identifier.of(DescentMod.MOD_ID, "scorched_lands"),
			3584, 180, 1700, 5, 0x5C04C4EDL);

	private ScorchedLandsRegions() {}

	public static void bind(long seed, int sx, int sz) { PLATE.bind(seed, sx, sz); }
	public static void clear() { PLATE.clear(); }
	public static void setBiomeEntry(RegistryEntry<Biome> e) { PLATE.setBiomeEntry(e); }
	public static RegistryEntry<Biome> biomeEntry() { return PLATE.biomeEntry(); }
	public static boolean isBound() { return PLATE.isBound(); }
	public static boolean isInBiome(int x, int z) { return PLATE.isInBiome(x, z); }
	public static BlockPos anchorAt(int x, int z) { return PLATE.anchorAt(x, z); }
	public static BlockPos findNearest(int x, int z) { return PLATE.findNearest(x, z); }
	public static String describeNearest(int x, int z) { return PLATE.describeNearest(x, z); }
}

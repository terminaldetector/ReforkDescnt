package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/** Spark-like technogenic sea — locators on the water, underwater 6DoF dungeons. */
public final class TechnogenicSeaRegions {
	public static final BiomePlateRegions PLATE = new BiomePlateRegions(
			Identifier.of(DescentMod.MOD_ID, "technogenic_sea"),
			4096, 220, 1800, 5, 0x7EC4A001L);

	private TechnogenicSeaRegions() {}

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

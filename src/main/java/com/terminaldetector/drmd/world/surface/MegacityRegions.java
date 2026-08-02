package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * Megacity as a <em>world biome region</em> — not a spawn landmark.
 *
 * <p>Seed picks sparse plate anchors on a large grid, far from world origin / spawn.
 * {@link com.terminaldetector.drmd.mixin.MultiNoiseBiomeSourceMixin} returns
 * {@code drmd:megacity} inside the plate radius; structures enqueue when a chunk in that
 * region loads ({@link MegacityBiomeWorldgen}).
 */
public final class MegacityRegions {
	public static final Identifier BIOME_ID = Identifier.of(DescentMod.MOD_ID, "megacity");
	public static final RegistryKey<Biome> BIOME_KEY = RegistryKey.of(RegistryKeys.BIOME, BIOME_ID);

	/** Grid cell size — one candidate city slot per cell. */
	public static final int CELL = 3072;
	/** Biome / plate footprint radius (structure ~112; biome slightly larger). */
	public static final int BIOME_RADIUS = 160;
	/** Never place a plate this close to world spawn (blocks). */
	public static final int MIN_SPAWN_DIST = 1600;
	/** ~1/6 cells host a megacity plate. */
	private static final int CELL_CHANCE = 6;

	private static volatile long worldSeed = Long.MIN_VALUE;
	private static volatile int spawnX;
	private static volatile int spawnZ;
	private static volatile RegistryEntry<Biome> biomeEntry;

	private MegacityRegions() {}

	public static void bind(long seed, int worldSpawnX, int worldSpawnZ) {
		worldSeed = seed;
		spawnX = worldSpawnX;
		spawnZ = worldSpawnZ;
	}

	public static void clear() {
		worldSeed = Long.MIN_VALUE;
		biomeEntry = null;
	}

	public static void setBiomeEntry(RegistryEntry<Biome> entry) {
		biomeEntry = entry;
	}

	public static RegistryEntry<Biome> biomeEntry() {
		return biomeEntry;
	}

	public static long worldSeed() {
		return worldSeed;
	}

	public static boolean isBound() {
		return worldSeed != Long.MIN_VALUE;
	}

	/** True when block XZ lies inside any megacity plate biome. */
	public static boolean isInBiome(int blockX, int blockZ) {
		return anchorAt(blockX, blockZ) != null;
	}

	/**
	 * Anchor covering this position, or null if outside every plate.
	 * Used by chunk-load structure enqueue.
	 */
	public static BlockPos anchorAt(int blockX, int blockZ) {
		if (!isBound()) return null;
		BlockPos a = nearestAnchor(blockX, blockZ);
		if (a == null) return null;
		if (distSq(blockX, blockZ, a) > (long) BIOME_RADIUS * BIOME_RADIUS) return null;
		return a;
	}

	/** Nearest valid megacity anchor to a point (may be far). */
	public static BlockPos findNearest(int fromX, int fromZ) {
		if (!isBound()) return null;
		BlockPos best = null;
		long bestD = Long.MAX_VALUE;
		int cellX = Math.floorDiv(fromX, CELL);
		int cellZ = Math.floorDiv(fromZ, CELL);
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				BlockPos a = anchorInCell(cellX + dx, cellZ + dz);
				if (a == null) continue;
				long d = distSq(fromX, fromZ, a);
				if (d < bestD) {
					bestD = d;
					best = a;
				}
			}
		}
		return best;
	}

	private static BlockPos nearestAnchor(int blockX, int blockZ) {
		int cellX = Math.floorDiv(blockX, CELL);
		int cellZ = Math.floorDiv(blockZ, CELL);
		BlockPos best = null;
		long bestD = Long.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos a = anchorInCell(cellX + dx, cellZ + dz);
				if (a == null) continue;
				long d = distSq(blockX, blockZ, a);
				if (d < bestD) {
					bestD = d;
					best = a;
				}
			}
		}
		return best;
	}

	/** Deterministic plate center for a grid cell, or null if this cell is empty / too near spawn. */
	public static BlockPos anchorInCell(int cellX, int cellZ) {
		if (!isBound()) return null;
		long h = hash(worldSeed, cellX, cellZ);
		if (Math.floorMod(h, CELL_CHANCE) != 0) return null;
		int margin = BIOME_RADIUS + 32;
		int span = CELL - margin * 2;
		if (span < 16) span = 16;
		int ox = margin + (int) Math.floorMod(h >> 8, span);
		int oz = margin + (int) Math.floorMod(h >> 24, span);
		int ax = cellX * CELL + ox;
		int az = cellZ * CELL + oz;
		long ds = distSq(ax, az, spawnX, spawnZ);
		if (ds < (long) MIN_SPAWN_DIST * MIN_SPAWN_DIST) return null;
		// Also keep clear of world origin for worlds whose spawn is still settling.
		if (distSq(ax, az, 0, 0) < (long) MIN_SPAWN_DIST * MIN_SPAWN_DIST / 2) return null;
		return new BlockPos(ax, 0, az);
	}

	private static long hash(long seed, int cx, int cz) {
		long x = seed ^ ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L);
		x ^= x >>> 33;
		x *= 0xff51afd7ed558ccdL;
		x ^= x >>> 33;
		x *= 0xc4ceb9fe1a85ec53L;
		x ^= x >>> 33;
		return x;
	}

	private static long distSq(int x, int z, BlockPos a) {
		return distSq(x, z, a.getX(), a.getZ());
	}

	private static long distSq(int x, int z, int x2, int z2) {
		long dx = x - x2;
		long dz = z - z2;
		return dx * dx + dz * dz;
	}

	public static int biomeRadius() {
		return BIOME_RADIUS;
	}

	public static String describeNearest(int fromX, int fromZ) {
		BlockPos a = findNearest(fromX, fromZ);
		if (a == null) return "no megacity plate in range";
		double d = Math.sqrt(distSq(fromX, fromZ, a));
		return String.format("megacity plate @ %d %d (%.0f m) · biome drmd:megacity r=%d",
				a.getX(), a.getZ(), d, BIOME_RADIUS);
	}
}

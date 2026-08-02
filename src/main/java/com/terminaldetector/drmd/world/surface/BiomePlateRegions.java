package com.terminaldetector.drmd.world.surface;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * Shared sparse seed-grid biome plates (megacity / technogenic sea / scorched lands).
 */
public final class BiomePlateRegions {
	private final Identifier biomeId;
	private final RegistryKey<Biome> biomeKey;
	private final int cell;
	private final int radius;
	private final int minSpawnDist;
	private final int cellChance;
	private final long salt;

	private volatile long worldSeed = Long.MIN_VALUE;
	private volatile int spawnX;
	private volatile int spawnZ;
	private volatile RegistryEntry<Biome> biomeEntry;

	public BiomePlateRegions(Identifier biomeId, int cell, int radius, int minSpawnDist, int cellChance, long salt) {
		this.biomeId = biomeId;
		this.biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
		this.cell = cell;
		this.radius = radius;
		this.minSpawnDist = minSpawnDist;
		this.cellChance = Math.max(2, cellChance);
		this.salt = salt;
	}

	public Identifier biomeId() { return biomeId; }
	public RegistryKey<Biome> biomeKey() { return biomeKey; }
	public int radius() { return radius; }

	public void bind(long seed, int worldSpawnX, int worldSpawnZ) {
		worldSeed = seed ^ salt;
		spawnX = worldSpawnX;
		spawnZ = worldSpawnZ;
	}

	public void clear() {
		worldSeed = Long.MIN_VALUE;
		biomeEntry = null;
	}

	public void setBiomeEntry(RegistryEntry<Biome> entry) { biomeEntry = entry; }
	public RegistryEntry<Biome> biomeEntry() { return biomeEntry; }
	public boolean isBound() { return worldSeed != Long.MIN_VALUE; }

	public boolean isInBiome(int blockX, int blockZ) {
		return anchorAt(blockX, blockZ) != null;
	}

	public BlockPos anchorAt(int blockX, int blockZ) {
		if (!isBound()) return null;
		BlockPos a = nearestAnchor(blockX, blockZ);
		if (a == null) return null;
		if (distSq(blockX, blockZ, a) > (long) radius * radius) return null;
		return a;
	}

	public BlockPos findNearest(int fromX, int fromZ) {
		if (!isBound()) return null;
		BlockPos best = null;
		long bestD = Long.MAX_VALUE;
		int cellX = Math.floorDiv(fromX, cell);
		int cellZ = Math.floorDiv(fromZ, cell);
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

	public BlockPos anchorInCell(int cellX, int cellZ) {
		if (!isBound()) return null;
		long h = hash(worldSeed, cellX, cellZ);
		if (Math.floorMod(h, cellChance) != 0) return null;
		int margin = radius + 32;
		int span = Math.max(16, cell - margin * 2);
		int ox = margin + (int) Math.floorMod(h >> 8, span);
		int oz = margin + (int) Math.floorMod(h >> 24, span);
		int ax = cellX * cell + ox;
		int az = cellZ * cell + oz;
		if (distSq(ax, az, spawnX, spawnZ) < (long) minSpawnDist * minSpawnDist) return null;
		if (distSq(ax, az, 0, 0) < (long) minSpawnDist * minSpawnDist / 2) return null;
		return new BlockPos(ax, 0, az);
	}

	public String describeNearest(int fromX, int fromZ) {
		BlockPos a = findNearest(fromX, fromZ);
		if (a == null) return "no " + biomeId.getPath() + " plate in range";
		double d = Math.sqrt(distSq(fromX, fromZ, a));
		return String.format("%s plate @ %d %d (%.0f m) · r=%d",
				biomeId.toString(), a.getX(), a.getZ(), d, radius);
	}

	private BlockPos nearestAnchor(int blockX, int blockZ) {
		int cellX = Math.floorDiv(blockX, cell);
		int cellZ = Math.floorDiv(blockZ, cell);
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
}

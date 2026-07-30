package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seeded macro catalogue for Voxel LLOD — horizon content without waiting for CHUNK_LOAD.
 *
 * <p>Ghost descriptors are deterministic from world seed + cell coords. Real block gen
 * still happens when chunks load; LLOD draws these proxies so high-speed 6DoF flight
 * never faces an empty void.
 */
public final class MacroCatalogue {
	/** Cell size in blocks (XZ). */
	public static final int CELL = 384;
	/** How many cells around the viewer (and look-ahead) to ensure. */
	public static final int RADIUS_CELLS = 5;
	/** Prune ghosts beyond this multiple of CELL from the viewer. */
	public static final double PRUNE_CELLS = RADIUS_CELLS + 2.5;

	private static final MacroEntry.Kind[] KINDS = {
			MacroEntry.Kind.ARCH, MacroEntry.Kind.RING, MacroEntry.Kind.FLOATING_CONTINENT,
			MacroEntry.Kind.SPIRAL_RANGE, MacroEntry.Kind.INVERTED_ISLAND,
			MacroEntry.Kind.RIFT, MacroEntry.Kind.CANYON, MacroEntry.Kind.STATION,
			MacroEntry.Kind.INDUSTRIAL_COMPLEX, MacroEntry.Kind.LUNAR_BASE, MacroEntry.Kind.CRASHED_UFO
	};

	/** Only catalogue-owned ghosts — never prune real MegaStructureGenerator entries. */
	private static final Set<UUID> GHOSTS = ConcurrentHashMap.newKeySet();

	private MacroCatalogue() {}

	/**
	 * Ensure ghost macros exist around the player, biased along velocity for high-speed flight.
	 */
	public static void ensureAround(ServerWorld world, BlockPos viewer, Vec3d velocity) {
		Vec3d lookAhead = velocity.multiply(40.0); // ~2s at high speed
		int ax = viewer.getX() + (int) lookAhead.x;
		int az = viewer.getZ() + (int) lookAhead.z;
		int cx = Math.floorDiv(ax, CELL);
		int cz = Math.floorDiv(az, CELL);
		long seed = world.getSeed();
		int bot = world.getBottomY();
		int top = WorldRules.worldTopInclusive(world);

		for (int dx = -RADIUS_CELLS; dx <= RADIUS_CELLS; dx++) {
			for (int dz = -RADIUS_CELLS; dz <= RADIUS_CELLS; dz++) {
				int gx = cx + dx;
				int gz = cz + dz;
				// Two vertical bands per cell: industrial + sky (inside real world column)
				ensureCell(seed, gx, gz, 0, bot, top);
				ensureCell(seed, gx, gz, 1, bot, top);
			}
		}
		pruneFar(viewer, CELL * PRUNE_CELLS);
	}

	/** Drop catalogue ghosts that left the streaming ring (real macros stay). */
	public static void pruneFar(BlockPos viewer, double maxDist) {
		double maxSq = maxDist * maxDist;
		List<UUID> drop = new ArrayList<>();
		for (UUID id : GHOSTS) {
			MacroEntry e = MacroWorld.get(id);
			if (e == null || e.distanceSq(viewer) > maxSq) drop.add(id);
		}
		for (UUID id : drop) {
			MacroWorld.remove(id);
			GHOSTS.remove(id);
		}
	}

	private static void ensureCell(long worldSeed, int gx, int gz, int band, int bot, int top) {
		UUID id = cellId(worldSeed, gx, gz, band);
		if (MacroWorld.get(id) != null) return;

		long h = mix(worldSeed, gx, gz, band);
		// Sparse: ~45% of cells get a ghost so the sky isn't solid noise
		if ((h & 0xFF) > 115) return;

		MacroEntry.Kind kind = KINDS[(int) Math.floorMod(h >> 8, KINDS.length)];
		int x = gx * CELL + 64 + (int) Math.floorMod(h >> 16, CELL - 128);
		int z = gz * CELL + 64 + (int) Math.floorMod(h >> 24, CELL - 128);
		int y;
		WorldRules.Layer layer;
		if (band == 0) {
			// Industrial / cave band
			y = MathHelper.clamp(WorldRules.INDUSTRIAL_Y_MIN + 20 + (int) Math.floorMod(h >> 32, 28), bot + 8, top - 8);
			layer = WorldRules.Layer.DEPTH_REACTORS;
		} else {
			// High-altitude band inside the real world column (not past topY)
			int skyLo = Math.min(top - 40, Math.max(bot + 80, 180));
			int skyHi = Math.max(skyLo + 8, top - 12);
			y = skyLo + (int) Math.floorMod(h >> 40, Math.max(1, skyHi - skyLo));
			layer = WorldRules.Layer.SKY_ARCHIPELAGO;
		}

		int sx = 48 + (int) Math.floorMod(h >> 4, 80);
		int sy = 28 + (int) Math.floorMod(h >> 12, 60);
		int sz = 48 + (int) Math.floorMod(h >> 20, 80);
		int color = colorFor(kind, h);
		String label = kind.name().charAt(0) + kind.name().substring(1).toLowerCase().replace('_', ' ');

		MacroWorld.put(new MacroEntry(id, kind, layer, new BlockPos(x, y, z), sx, sy, sz, color, label));
		GHOSTS.add(id);
	}

	private static UUID cellId(long seed, int gx, int gz, int band) {
		long lo = mix(seed, gx, gz, band);
		long hi = mix(seed ^ 0x9E3779B97F4A7C15L, gz, gx, band + 17);
		return new UUID(hi, lo);
	}

	private static long mix(long seed, int a, int b, int c) {
		long x = seed ^ ((long) a * 341873128712L) ^ ((long) b * 132897987541L) ^ ((long) c * 0x85EBCA77C2B2AE63L);
		x ^= (x >>> 30);
		x *= 0xBF58476D1CE4E5B9L;
		x ^= (x >>> 27);
		x *= 0x94D049BB133111EBL;
		x ^= (x >>> 31);
		return x;
	}

	private static int colorFor(MacroEntry.Kind kind, long h) {
		return switch (kind) {
			case RIFT, CANYON -> 0x553322;
			case ARCH, RING -> 0x6688AA;
			case FLOATING_CONTINENT, SPIRAL_RANGE -> 0x4A7A4A;
			case INVERTED_ISLAND -> 0x8866AA;
			case INDUSTRIAL_COMPLEX, STATION -> 0x8899AA;
			case LUNAR_BASE -> 0xCCCCDD;
			case CRASHED_UFO, UFO -> 0x44AA66;
			case WORM -> 0xAA5544;
			case SWARM -> 0x66AADD;
			case KEEPER -> 0xDD4444;
		};
	}
}

package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.base.DescentSession;
import com.terminaldetector.drmd.world.dungeon.DungeonTerritory;
import com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MegaLocatorGenerator;
import com.terminaldetector.drmd.world.gen2.MegaStructureGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Random surface events — UFOs and outpost builds are <em>not</em> the spawn hub campaign.
 *
 * <p>Sparse cell grid (≥400 m from spawn). Discovery on chunk load; builds distance-queue
 * via {@link DescentSession#enqueueLandmark}.
 */
public final class SurfaceEventWorldgen {
	private static final int CELL = 768;
	private static final int CELL_CHANCE = 7;
	private static final int MIN_SPAWN_DIST = 400;
	private static final long SALT = 0x5F0A7E17L;

	private static volatile long worldSeed = Long.MIN_VALUE;
	private static volatile int spawnX;
	private static volatile int spawnZ;

	private SurfaceEventWorldgen() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerWorld ow = server.getOverworld();
			if (ow == null) return;
			BlockPos spawn = ow.getSpawnPos();
			worldSeed = ow.getSeed() ^ SALT;
			spawnX = spawn.getX();
			spawnZ = spawn.getZ();
			DescentMod.LOGGER.info("Surface events bound — sparse UFO/outpost rolls (≥{}m from spawn)",
					MIN_SPAWN_DIST);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> worldSeed = Long.MIN_VALUE);
		ServerChunkEvents.CHUNK_LOAD.register(SurfaceEventWorldgen::onChunkLoad);
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!WorldFeatures.SURFACE_DISTRICTS) return;
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (worldSeed == Long.MIN_VALUE) return;

		DescentWorldState state = DescentWorldState.get(world);
		if (state.isPsychedelic() || state.isInfiniteMegacity()) return;

		ChunkPos cp = chunk.getPos();
		BlockPos anchor = anchorAt(cp.getCenterX(), cp.getCenterZ());
		if (anchor == null) return;
		if (state.isSurfaceEventSeeded(anchor.getX(), anchor.getZ())) return;
		state.markSurfaceEventSeeded(anchor.getX(), anchor.getZ());
		enqueueEvent(world, anchor);
	}

	public static void enqueueEvent(ServerWorld world, BlockPos anchorXZ) {
		int ax = anchorXZ.getX();
		int az = anchorXZ.getZ();
		long seed = worldSeed ^ ((long) ax * 31L) ^ az;
		int roll = (int) Math.floorMod(seed >> 5, 5L);

		switch (roll) {
			case 0 -> enqueueCrashedUfo(world, ax, az, seed);
			case 1 -> enqueueSkyUfo(world, ax, az, seed);
			case 2 -> enqueueRuinedComplex(world, ax, az, seed);
			case 3 -> enqueueSmallLocator(world, ax, az, seed);
			default -> enqueueOutpostPair(world, ax, az, seed);
		}
		DescentMod.LOGGER.info("Surface event #{} queued @ {} {}", roll, ax, az);
	}

	private static void enqueueCrashedUfo(ServerWorld world, int ax, int az, long seed) {
		DescentSession.enqueueLandmark(new BlockPos(ax, 0, az), () -> {
			int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ax, az);
			BlockPos at = new BlockPos(ax, Math.max(55, Math.min(110, top)), az);
			MegaStructureGenerator.generate(world, at, MacroEntry.Kind.CRASHED_UFO, Random.create(seed));
		});
		// Nearby ruin build — random 6DoF dungeon under the crash site
		WorldRules.ComplexStyle style = DungeonTerritory.primaryStyle(DungeonTerritory.Kind.SURFACE_EVENT, seed);
		var vit = DungeonTerritory.vitality(DungeonTerritory.Kind.SURFACE_EVENT, seed);
		BlockPos under = new BlockPos(ax + 24, WorldRules.INDUSTRIAL_Y_MIN + 28, az - 18);
		DescentSession.enqueueLandmark(under, () ->
				IndustrialComplexGenerator.generateAt(world, under, style, vit, Random.create(seed ^ 0x11L)));
	}

	private static void enqueueSkyUfo(ServerWorld world, int ax, int az, long seed) {
		int y = WorldRules.SKY_PRACTICAL_MIN + 24 + (int) Math.floorMod(seed, 48L);
		BlockPos at = new BlockPos(ax, y, az);
		DescentSession.enqueueLandmark(at, () -> {
			var ufo = ModEntities.SKY_UFO.create(world);
			if (ufo != null) {
				ufo.refreshPositionAndAngles(ax + 0.5, y, az + 0.5, 0, 0);
				world.spawnEntity(ufo);
			}
		});
	}

	private static void enqueueRuinedComplex(ServerWorld world, int ax, int az, long seed) {
		WorldRules.ComplexStyle style = DungeonTerritory.primaryStyle(DungeonTerritory.Kind.SURFACE_EVENT, seed);
		var vit = DungeonTerritory.vitality(DungeonTerritory.Kind.SURFACE_EVENT, seed ^ 0x33L);
		BlockPos at = new BlockPos(ax, WorldRules.INDUSTRIAL_Y_MIN + 22 + (int) Math.floorMod(seed, 16L), az);
		DescentSession.enqueueLandmark(at, () ->
				IndustrialComplexGenerator.generateAt(world, at, style, vit, Random.create(seed)));
	}

	private static void enqueueSmallLocator(ServerWorld world, int ax, int az, long seed) {
		DescentSession.enqueueLandmark(new BlockPos(ax, 0, az), () ->
				MegaLocatorGenerator.generateSmall(world, new BlockPos(ax, 0, az), Random.create(seed)));
		WorldRules.ComplexStyle style = DungeonTerritory.satelliteStyle(DungeonTerritory.Kind.SURFACE_EVENT, seed);
		var vit = DungeonTerritory.vitality(DungeonTerritory.Kind.SURFACE_EVENT, seed ^ 0x22L);
		BlockPos under = new BlockPos(ax, Math.min(WorldRules.INDUSTRIAL_Y_MAX - 8, 42), az);
		DescentSession.enqueueLandmark(under, () ->
				IndustrialComplexGenerator.generateAt(world, under, style, vit, Random.create(seed ^ 0x22L)));
	}

	private static void enqueueOutpostPair(ServerWorld world, int ax, int az, long seed) {
		enqueueCrashedUfo(world, ax, az, seed);
		int y = WorldRules.SKY_PRACTICAL_MIN + 40;
		DescentSession.enqueueLandmark(new BlockPos(ax + 40, y, az + 30), () -> {
			var ufo = ModEntities.SKY_UFO.create(world);
			if (ufo != null) {
				ufo.refreshPositionAndAngles(ax + 40.5, y, az + 30.5, 0, 0);
				world.spawnEntity(ufo);
			}
		});
	}

	static BlockPos anchorAt(int blockX, int blockZ) {
		if (worldSeed == Long.MIN_VALUE) return null;
		int cellX = Math.floorDiv(blockX, CELL);
		int cellZ = Math.floorDiv(blockZ, CELL);
		BlockPos a = anchorInCell(cellX, cellZ);
		if (a == null) return null;
		long dx = (long) blockX - a.getX();
		long dz = (long) blockZ - a.getZ();
		// Match landmark SEED_RADIUS — 96m was easy to fly past without ever enqueueing.
		if (dx * dx + dz * dz > 256L * 256L) return null;
		return a;
	}

	private static BlockPos anchorInCell(int cellX, int cellZ) {
		long h = hash(worldSeed, cellX, cellZ);
		if (Math.floorMod(h, CELL_CHANCE) != 0) return null;
		int margin = 48;
		int span = Math.max(16, CELL - margin * 2);
		int ox = margin + (int) Math.floorMod(h >> 8, span);
		int oz = margin + (int) Math.floorMod(h >> 24, span);
		int ax = cellX * CELL + ox;
		int az = cellZ * CELL + oz;
		long dsx = (long) ax - spawnX;
		long dsz = (long) az - spawnZ;
		if (dsx * dsx + dsz * dsz < (long) MIN_SPAWN_DIST * MIN_SPAWN_DIST) return null;
		return new BlockPos(ax, 0, az);
	}

	private static long hash(long seed, int cx, int cz) {
		long h = seed ^ ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L);
		h ^= (h >>> 33);
		h *= 0xff51afd7ed558ccdL;
		h ^= (h >>> 33);
		return h;
	}
}

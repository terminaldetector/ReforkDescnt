package com.terminaldetector.drmd.world.store;

import com.terminaldetector.drmd.DescentMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.Path;

/**
 * Turns chunks the server loads into the observed surface the horizon draws.
 *
 * <p>One chunk is one level-0 cell, so a load produces exactly one number and one colour and
 * replaces whatever was there — which is what lets the map follow demolition instead of remembering
 * the tallest thing that ever stood in it.
 *
 * <p>Sampling is an 8×8 grid rather than all 256 columns: the cell keeps a single height in the end,
 * and sixty-four probes already find any tower worth a silhouette. Colour comes from the same column
 * that won the height, through vanilla's own map colours, so the horizon and a filled-in map agree
 * about what the ground is.
 */
public final class SurfaceIngest {
	/** Probes per axis inside a chunk. */
	private static final int SAMPLES = 8;
	/** Coarse sections rebuilt per tick — the climb up the levels is spread over ticks. */
	private static final int REBUILD_PER_TICK = 4;
	/** Ticks between flushes of everything dirty. */
	private static final int FLUSH_INTERVAL = 20 * 60;

	private static SurfaceStore store;

	private SurfaceIngest() {}

	/** The live store, or {@code null} before a world is open. */
	public static SurfaceStore store() {
		return store;
	}

	public static void onServerStarted(MinecraftServer server) {
		close();
		try {
			Path root = server.getSavePath(WorldSavePath.ROOT).resolve("drmd").resolve("surface");
			store = new SurfaceStore(new CompressedSectionStorage(new FileSectionStorage(root)));
			DescentMod.LOGGER.info("Surface store open at {}", root);
		} catch (Exception e) {
			// A world that cannot store its surface still plays; the horizon just stays procedural.
			DescentMod.LOGGER.error("Surface store unavailable — horizon stays procedural", e);
			store = null;
		}
	}

	public static void close() {
		if (store == null) return;
		try {
			store.close();
		} catch (Exception e) {
			DescentMod.LOGGER.error("Surface store did not close cleanly", e);
		}
		store = null;
	}

	/** Chunk load: sample it into level 0. */
	public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		SurfaceStore live = store;
		if (live == null) return;
		if (world.getRegistryKey() != World.OVERWORLD) return;

		int baseX = chunk.getPos().getStartX();
		int baseZ = chunk.getPos().getStartZ();
		int step = 16 / SAMPLES;
		int bestY = Integer.MIN_VALUE;
		int bestX = baseX;
		int bestZ = baseZ;

		for (int sx = 0; sx < SAMPLES; sx++) {
			for (int sz = 0; sz < SAMPLES; sz++) {
				int x = baseX + sx * step;
				int z = baseZ + sz * step;
				int y = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (y > bestY) {
					bestY = y;
					bestX = x;
					bestZ = z;
				}
			}
		}
		if (bestY == Integer.MIN_VALUE) return;

		BlockPos top = new BlockPos(bestX, Math.max(world.getBottomY(), bestY - 1), bestZ);
		int rgb = colourAt(world, chunk, top);
		live.set(baseX, baseZ, bestY, rgb);
	}

	private static int colourAt(ServerWorld world, WorldChunk chunk, BlockPos pos) {
		try {
			var state = chunk.getBlockState(pos);
			var mapColour = state.getMapColor(world, pos);
			return mapColour == null ? 0x4C7638 : mapColour.color;
		} catch (Exception e) {
			// getMapColor can reach for neighbours; a chunk edge during load is not worth a crash.
			return 0x4C7638;
		}
	}

	/** Server tick: climb the levels a little, and write back now and then. */
	public static void tick(MinecraftServer server) {
		SurfaceStore live = store;
		if (live == null) return;
		live.rebuildDirty(REBUILD_PER_TICK);
		if (server.getTicks() % FLUSH_INTERVAL == 0) live.flush();
	}
}

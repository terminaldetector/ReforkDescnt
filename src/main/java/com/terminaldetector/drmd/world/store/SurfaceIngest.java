package com.terminaldetector.drmd.world.store;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.fixture.city.OrbitalFieldBlueprint.ObjectSpec;
import com.terminaldetector.drmd.world.planet.AuthoredOrbitalCatalogue;
import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns chunks the server loads into the observed surface the horizon draws.
 *
 * <p>One chunk is one level-0 cell, so a load produces exactly one number and one colour and
 * replaces whatever was there — which is what lets the map follow demolition instead of remembering
 * the tallest thing that ever stood in it.
 *
 * <p>One pass over the chunk's existing heightmap finds the actual top, including narrow
 * structures between the old sample points. Colour comes from that winning column.
 */
public final class SurfaceIngest {
	/** Coarse sections rebuilt per tick — the climb up the levels is spread over ticks. */
	private static final int REBUILD_PER_TICK = 4;
	/** Ticks between flushes of everything dirty. */
	private static final int FLUSH_INTERVAL = 20 * 60;
	private static final int MAX_NATIVE_PENDING = 4096;
	private static final int NATIVE_PROBES_PER_TICK = 4;

	private static SurfaceStore store;
	private static ServerWorld storeWorld;
	private static final java.util.Set<Long> dirtyChunks = new java.util.LinkedHashSet<>();
	private static final NativeRefreshQueue nativePending = new NativeRefreshQueue();
	private static List<ObjectSpec> orbitalFootprints = List.of();
	private static Map<Long, OrbitalGroundColumns> orbitalColumns = Map.of();

	/** Keys only: a failed/unknown probe does not consume work or retain a chunk/ticket. */
	static final class NativeRefreshQueue {
		private final java.util.LinkedHashSet<Long> keys = new java.util.LinkedHashSet<>();
		private volatile long evictions;
		void offer(long key) {
			if (keys.contains(key)) return;
			if (keys.size() == MAX_NATIVE_PENDING) {
				var first = keys.iterator(); first.next(); first.remove(); evictions++;
			}
			keys.add(key);
		}
		java.util.List<Long> rotate(int budget) {
			var result = new java.util.ArrayList<Long>();
			int count = Math.min(Math.max(0, budget), keys.size());
			for (int n = 0; n < count; n++) {
				var first = keys.iterator(); long key = first.next(); first.remove();
				keys.add(key); result.add(key);
			}
			return java.util.List.copyOf(result);
		}
		void acknowledge(long key) { keys.remove(key); }
		int size() { return keys.size(); }
		long evictions() { return evictions; }
		void clear() { keys.clear(); evictions = 0; }
	}

	private SurfaceIngest() {}

	/** The live store, or {@code null} before a world is open. */
	public static SurfaceStore store() {
		return store;
	}

	/** Inspect actual overload without changing the queue or querying a world. */
	public static long nativeRefreshEvictions() { return nativePending.evictions(); }

	/** Optional native callback; independent of skylight configuration or availability. */
	public static void nativeCubeLoaded(WorldChunk chunk) {
		if (!(chunk.getWorld() instanceof ServerWorld world)
				|| !CubicAvailability.onWorldThread(world) || world != storeWorld || store == null) return;
		nativePending.offer(chunk.getPos().toLong());
	}

	public static void onServerStarted(MinecraftServer server) {
		close();
		try {
			Path root = server.getSavePath(WorldSavePath.ROOT).resolve("drmd").resolve("surface");
			store = new SurfaceStore(new CompressedSectionStorage(new FileSectionStorage(root)));
			storeWorld = server.getOverworld();
			syncOrbitalFootprints(storeWorld, store);
			DescentMod.LOGGER.info("Surface store open at {}", root);
		} catch (Exception e) {
			// A world that cannot store its surface still plays; the horizon just stays procedural.
			DescentMod.LOGGER.error("Surface store unavailable — horizon stays procedural", e);
			store = null;
		}
	}

	public static void close() {
		storeWorld = null;
		nativePending.clear();
		dirtyChunks.clear();
		orbitalFootprints = List.of();
		orbitalColumns = Map.of();
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
		int bestY = Integer.MIN_VALUE;
		int bestX = baseX;
		int bestZ = baseZ;
		var orbital = orbitalColumns.get(chunk.getPos().toLong());
		int[] orbitalPeaks = orbital == null ? null : new int[256];
		if (orbitalPeaks != null) Arrays.fill(orbitalPeaks, Integer.MIN_VALUE);

		for (int sx = 0; sx < 16; sx++) {
			for (int sz = 0; sz < 16; sz++) {
				int x = baseX + sx;
				int z = baseZ + sz;
				int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
				if (orbital != null && orbital.contains(x, y, z)) {
					orbitalPeaks[sx * 16 + sz] = y;
					continue;
				}
				if (y > bestY) {
					bestY = y;
					bestX = x;
					bestZ = z;
				}
			}
		}
		if (orbitalPeaks != null) {
			Map<Integer, Boolean> fullCubes = new HashMap<>();
			for (int sx = 0; sx < 16; sx++) {
				for (int sz = 0; sz < 16; sz++) {
					int peak = orbitalPeaks[sx * 16 + sz];
					if (peak <= bestY) continue;
					int x = baseX + sx, z = baseZ + sz;
					int y = belowOrbitalPeak(world, chunk, orbital, fullCubes, x, peak - 1, z, bestY);
					if (y > bestY) { bestY = y; bestX = x; bestZ = z; }
				}
			}
		}
		if (bestY == Integer.MIN_VALUE) {
			// Try again on the next actual cube arrival or edit, not on every idle budget pass.
			if (world == storeWorld) nativePending.acknowledge(chunk.getPos().toLong());
			return;
		}

		// Chunk.sampleHeightmap names the top block; its physical top face is one above.
		BlockPos top = new BlockPos(bestX, Math.max(world.getBottomY(), bestY), bestZ);
		// A retained native column can have an unavailable winning Y cube. Keep the
		// prior observation until that actual volume is FULL; placeholder AIR is unknown.
		if (!WorldAccess.of(world).isVolumeAvailable(
				D6ChunkPos.ofBlock(top.getX(), top.getY(), top.getZ()))) {
			if (world == storeWorld) nativePending.offer(chunk.getPos().toLong());
			return;
		}
		int rgb = colourAt(world, chunk, top);
		if (live.set(baseX, baseZ, storedSurfaceHeight(bestY, world.getBottomY()), rgb))
			SurfaceStreamer.invalidate(SectionKey.of(0,SectionKey.sectionOf(baseX,0),SectionKey.sectionOf(baseZ,0)));
		if (world == storeWorld) nativePending.acknowledge(chunk.getPos().toLong());
	}

	private static int belowOrbitalPeak(ServerWorld world, WorldChunk chunk, OrbitalGroundColumns orbital,
			Map<Integer, Boolean> fullCubes, int x, int y, int z, int bestY) {
		while (y > bestY && y >= world.getBottomY()) {
			int cubeY = y >> 4;
			if (!fullCubes.computeIfAbsent(cubeY, cy -> CubicAvailability.available(world,
				new D6ChunkPos(chunk.getPos().x, cy, chunk.getPos().z)))) break;
			var section = chunk.getSection(world.getSectionIndex(y));
			if (section.isEmpty()) {
				y = (cubeY << 4) - 1;
				continue;
			}
			if (!orbital.contains(x, y, z) && !section.getBlockState(x & 15, y & 15, z & 15).isAir()) return y;
			y--;
		}
		return bestY;
	}

	private static void syncOrbitalFootprints(ServerWorld world, SurfaceStore live) {
		if (world == null) return;
		var footprints = AuthoredOrbitalCatalogue.surfaceExclusions(world);
		if (footprints.equals(orbitalFootprints)) return;
		var columns = OrbitalGroundColumns.index(footprints, shape ->
			(shape == OrbitalObjectGeometry.Shape.STATION ? Blocks.IRON_BLOCK : Blocks.STONE)
				.getDefaultState().getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).color);
		for (var entry : columns.entrySet()) {
			var chunk = new ChunkPos(entry.getKey());
			int x = chunk.getStartX(), z = chunk.getStartZ();
			if (entry.getValue().couldBeOldRoof(live.heightAt(x, z, 0), live.colourAt(x, z, 0)) && live.forget(x, z)) {
				SurfaceStreamer.invalidate(SectionKey.of(0, SectionKey.sectionOf(x, 0), SectionKey.sectionOf(z, 0)));
			}
			nativePending.offer(entry.getKey());
		}
		orbitalColumns = columns;
		orbitalFootprints = footprints;
	}

	/** Initial load and edits share the same per-tick sample budget. */
	public static void queueChunkLoad(ServerWorld world,WorldChunk chunk) {
		if (world == storeWorld && store != null && CubicAvailability.onWorldThread(world))
			nativePending.offer(chunk.getPos().toLong());
	}

	private static void refreshAvailable(ServerWorld world,int budget) {
		if (world != storeWorld || !CubicAvailability.onWorldThread(world)) return;
		for (long key : nativePending.rotate(Math.min(budget,NATIVE_PROBES_PER_TICK))) {
			var pos = new net.minecraft.util.math.ChunkPos(key);
			var chunk = CubicAvailability.loadedColumn(world, pos.x, pos.z);
			if (chunk == null) { nativePending.acknowledge(key); continue; }
			onChunkLoad(world,chunk);
		}
	}

	/** Retain existing below-bottom empty sentinels; never promote them into a surface. */
	static int storedSurfaceHeight(int sampledY, int bottomY) {
		if (sampledY == Integer.MIN_VALUE || sampledY < bottomY) return sampledY;
		return Math.addExact(sampledY, 1);
	}

	private static int colourAt(ServerWorld world, WorldChunk chunk, BlockPos pos) {
		try {
			var state = chunk.getBlockState(pos);
			if (chunk.getBlockEntity(pos) instanceof com.terminaldetector.drmd.world.micro.CarvedBlockEntity carved)
				state = carved.source();
			var mapColour = state.getMapColor(world, pos);
			return mapColour == null ? 0x4C7638 : mapColour.color;
		} catch (Exception e) {
			// getMapColor can reach for neighbours; a chunk edge during load is not worth a crash.
			return 0x4C7638;
		}
	}

	/**
	 * Something changed the ground here — re-sample the chunk and let it be sent again.
	 *
	 * <p>This is the loop that makes destruction show up on the horizon: a wall coming down changes
	 * the cell, the cell rebuilds the coarse levels above it downward, and the section goes back into
	 * the streamer's queue for everyone who had already been given the old one.
	 */
	public static void onGroundChanged(ServerWorld world, BlockPos pos) {
        com.terminaldetector.drmd.world.planet.AuthoredOrbitalCatalogue.changed(world,pos);
		SurfaceStore live = store;
		if (live == null) return;
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return;
        dirtyChunks.add(net.minecraft.util.math.ChunkPos.toLong(pos.getX() >> 4,pos.getZ() >> 4));
	}

	/** Server tick: climb the levels a little, and write back now and then. */
	public static void tick(MinecraftServer server) {
		SurfaceStore live = store;
		if (live == null) return;
        ServerWorld world=server.getOverworld();
		syncOrbitalFootprints(world, live);
        var dirty=dirtyChunks.iterator();
        int remaining=REBUILD_PER_TICK;
        // Edits under an active player get processed before scanning newly loaded terrain.
        while(world!=null && remaining>0 && dirty.hasNext()) {
            long key=dirty.next();dirty.remove();
            remaining--;
            var pos=new net.minecraft.util.math.ChunkPos(key);
            var loaded=CubicAvailability.loadedColumn(world,pos.x,pos.z);
            if(loaded==null) continue; // Parent reopen/native arrivals can schedule later refreshes.
            onChunkLoad(world,loaded);
        }
		if(world!=null && remaining>0) refreshAvailable(world,remaining);
		live.rebuildDirty(REBUILD_PER_TICK,SurfaceStreamer::invalidate);
		if (server.getTicks() % FLUSH_INTERVAL == 0) live.flush();
	}
}

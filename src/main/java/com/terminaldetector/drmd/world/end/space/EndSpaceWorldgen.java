package com.terminaldetector.drmd.world.end.space;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.end.EndReactorSession;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Tiles {@link EndSpaceTileShape} across {@link EndSpaceRegions}' unbounded grid in the real End,
 * once {@link EndReactorSession#isLayer2Unlocked} — mirrors {@code InfiniteMegacityWorldgen}'s own
 * chunk-load discovery / tick-budget drain shape. Deliberately its own queue and its own
 * {@code ServerChunkEvents.CHUNK_LOAD} listener, not a retrofit of {@code LevelBuilder} (confirmed
 * Overworld-only throughout — every entry point early-returns on {@code World.OVERWORLD}) or of the
 * shared landmark queue {@code DescentSession} drains (sized for occasional jobs, not an
 * every-cell-gets-one grid).
 *
 * <p>Unlike the megacity precedent, "already built" here is a single persisted {@link EndSpaceState}
 * mark that is never cleared — see that class's own doc for why a permanent mark, not a
 * clear-every-boot-plus-physical-recheck pair, is the right shape for a one-shot tile with no
 * generator-level re-entry guard of its own.
 */
public final class EndSpaceWorldgen {
	/** Tiles built per tick — small on purpose, one tile is four platform rings' worth of writes. */
	private static final int BASE_BUDGET = 1;
	/** Tiles built per tick once the backlog is deep enough to be worth catching up faster. */
	private static final int BURST_BUDGET = 2;
	private static final int BURST_THRESHOLD = 8;
	/** How close a player must be before a queued tile is built. */
	private static final int BUILD_RADIUS = 128;
	/** Local-Y-0 for every tile — mid-way up the 0..4096 tall-End column (Phase B1), clear of the
	 * Citadel arena down low (its own topmost deck tops out at 148) and the declared ceiling up high. */
	private static final int TILE_BASE_Y = 512;

	private record Job(ServerWorld world, int cellX, int cellZ) {}

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	private EndSpaceWorldgen() {}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(EndSpaceWorldgen::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(EndSpaceWorldgen::drain);
		DescentMod.LOGGER.info("End Space (Layer 2) worldgen online — grid pitch {}", EndSpaceRegions.PITCH);
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.END) return;
		if (!EndReactorSession.isLayer2Unlocked(world)) return;

		ChunkPos cp = chunk.getPos();
		int cellX = EndSpaceRegions.cellOf(cp.getCenterX());
		int cellZ = EndSpaceRegions.cellOf(cp.getCenterZ());
		if (!EndSpaceRegions.isBeyondInnerRadius(cellX, cellZ)) return;
		if (EndSpaceState.get(world).isCellBuilt(cellX, cellZ)) return;

		long key = pack(cellX, cellZ);
		if (!QUEUED.add(key)) return;
		QUEUE.add(new Job(world, cellX, cellZ));
	}

	/**
	 * Round-robins the whole queue once per tick looking for a tile near a player — same shape as
	 * {@code InfiniteMegacityWorldgen.drain}: a cell nobody has reached yet goes back to the tail rather
	 * than blocking whichever cell a player actually is near.
	 */
	private static void drain(MinecraftServer server) {
		if (QUEUE.isEmpty()) return;
		ServerWorld end = server.getWorld(World.END);
		if (end == null) return;

		int budget = QUEUE.size() > BURST_THRESHOLD ? BURST_BUDGET : BASE_BUDGET;
		int rounds = QUEUE.size();
		for (int i = 0; i < rounds && budget > 0; i++) {
			Job job = QUEUE.poll();
			if (job == null) break;
			BlockPos anchor = EndSpaceRegions.anchorForCell(job.cellX(), job.cellZ());
			if (!playerWithinRange(end, anchor)) {
				QUEUE.add(job);
				continue;
			}
			QUEUED.remove(pack(job.cellX(), job.cellZ()));
			build(job, anchor);
			budget--;
		}
	}

	private static boolean playerWithinRange(ServerWorld world, BlockPos anchor) {
		for (var player : world.getPlayers()) {
			double dx = player.getX() - anchor.getX();
			double dz = player.getZ() - anchor.getZ();
			if (dx * dx + dz * dz <= (double) BUILD_RADIUS * BUILD_RADIUS) return true;
		}
		return false;
	}

	/**
	 * Iterates platform levels directly ({@code p * PLATFORM_SPACING}) rather than scanning every local
	 * Y — unlike {@code CitadelDeckShape}'s contiguous multi-block-tall decks, every platform here is a
	 * single Y thick with wide open gaps between, so scanning every Y would mean walking hundreds of
	 * empty layers just to find the four that aren't.
	 */
	private static void build(Job job, BlockPos anchorXZ) {
		ServerWorld world = job.world();
		if (world.getBottomY() > TILE_BASE_Y
				|| world.getTopY() < TILE_BASE_Y + EndSpaceTileShape.TILE_HEIGHT) {
			// Declared column too short for a full tile — e.g. WorldModLevel switched to VANILLA after
			// this fight was already won under ADVANCED. Mark it built anyway: retrying every time a
			// player passes by would be silent, permanent, per-chunk-load work for nothing.
			EndSpaceState.get(world).markCellBuilt(job.cellX(), job.cellZ());
			return;
		}

		int half = EndSpaceTileShape.HALF_EXTENT;
		for (int p = 0; p < EndSpaceTileShape.PLATFORM_COUNT; p++) {
			int localY = p * EndSpaceTileShape.PLATFORM_SPACING;
			for (int x = -half; x <= half; x++) {
				for (int z = -half; z <= half; z++) {
					EndSpaceTileShape.Cell cell = EndSpaceTileShape.classify(x, localY, z);
					if (cell == EndSpaceTileShape.Cell.NONE) continue;
					BlockPos pos = new BlockPos(anchorXZ.getX() + x, TILE_BASE_Y + localY, anchorXZ.getZ() + z);
					world.setBlockState(pos, cell == EndSpaceTileShape.Cell.BEACON
							? Blocks.SEA_LANTERN.getDefaultState()
							: Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		EndSpaceState.get(world).markCellBuilt(job.cellX(), job.cellZ());
	}

	private static long pack(int cellX, int cellZ) {
		return ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
	}
}

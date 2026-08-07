package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MegaStructureGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Tiles {@code MegacityGenerator} across {@link InfiniteMegacityRegions}' unbounded grid for
 * {@code DrmdServerConfig.WorldKind.INFINITE_MEGACITY} worlds — city everywhere, no exclusion zone,
 * no sparse roll.
 *
 * <p>Deliberately its own queue rather than {@code DescentSession.enqueueLandmark}: that shared queue
 * drains 1–3 jobs a tick across every landmark type a stock world has (biome plates, rifts, lunar
 * bases, UFOs — a couple dozen sources total), sized for how rarely any one of them actually fires. A
 * mode whose entire purpose is a city in <em>every</em> cell would flood that budget the moment a
 * player started flying, starving whatever else shares it. A megacity plate is also one of the
 * heaviest single jobs already accepted in that shared queue, so this one gets a matching, separately
 * accounted budget instead of a fraction of someone else's.
 *
 * <p>Cells are discovered on chunk load but not built there — {@code DescentSession}'s own doc comment
 * already explains why not: a plate's ~150-block footprint reaches chunks well beyond whichever single
 * chunk just triggered the load, and writing into a chunk that is not loaded yet is either a silent
 * no-op or a forced extra load, neither of which is what a background stream should be doing. Building
 * waits for a player to actually be near the plate's anchor, by which point its footprint is inside
 * their own view distance and already loading anyway — same {@code BUILD_RADIUS} as the existing
 * landmark queue's {@code SEED_RADIUS}, tuned there for the same reason.
 */
public final class InfiniteMegacityWorldgen {
	/** Base plates built per tick. */
	private static final int BASE_BUDGET = 1;
	/** Plates built per tick once the backlog is deep enough to be worth catching up faster. */
	private static final int BURST_BUDGET = 2;
	private static final int BURST_THRESHOLD = 8;
	/** How close a player must be before a queued plate is built — mirrors DescentSession.SEED_RADIUS. */
	private static final int BUILD_RADIUS = 256;
	private static final long SALT = 0x1AFEC170_0001L;

	private record Job(ServerWorld world, int cellX, int cellZ) {}

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	private InfiniteMegacityWorldgen() {}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(InfiniteMegacityWorldgen::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(InfiniteMegacityWorldgen::drain);
		DescentMod.LOGGER.info("Infinite megacity worldgen online — grid pitch {}", InfiniteMegacityRegions.PITCH);
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		DescentWorldState state = DescentWorldState.get(world);
		if (!state.isInfiniteMegacity()) return;

		ChunkPos cp = chunk.getPos();
		int cellX = InfiniteMegacityRegions.cellOf(cp.getCenterX());
		int cellZ = InfiniteMegacityRegions.cellOf(cp.getCenterZ());
		if (state.isInfiniteMegacityCellSeeded(cellX, cellZ)) return;

		long key = pack(cellX, cellZ);
		if (!QUEUED.add(key)) return;
		state.markInfiniteMegacityCellSeeded(cellX, cellZ);
		QUEUE.add(new Job(world, cellX, cellZ));
	}

	/**
	 * Round-robins the whole queue once per tick looking for a plate near a player, same shape as
	 * {@code LevelBuilder}'s drain: a cell nobody has reached yet goes back to the tail rather than
	 * blocking whichever cell a player actually is near — see that class for what happens when a
	 * background queue re-prioritizes the wrong thing instead.
	 */
	private static void drain(MinecraftServer server) {
		if (QUEUE.isEmpty()) return;
		ServerWorld ow = server.getOverworld();
		if (ow == null) return;

		int budget = QUEUE.size() > BURST_THRESHOLD ? BURST_BUDGET : BASE_BUDGET;
		int rounds = QUEUE.size();
		for (int i = 0; i < rounds && budget > 0; i++) {
			Job job = QUEUE.poll();
			if (job == null) break;
			BlockPos anchor = InfiniteMegacityRegions.anchorForCell(job.cellX(), job.cellZ());
			if (!playerWithinRange(ow, anchor)) {
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
	 * Routed through {@code MegaStructureGenerator}, not called on {@code MegacityGenerator} directly:
	 * that wrapper's LODESTONE-at-origin check is the only re-entry guard a plate gets.
	 * {@code MegacityGenerator} itself has none — it trusts whichever caller reaches it not to call
	 * twice. {@code DescentWorldState.clearLandmarkSeedMarks} wipes this class's own "already queued"
	 * tracking on every server restart (by design — the queue itself is in-memory only), so a
	 * previously-built cell that a player flies back over gets rediscovered and re-enqueued; without
	 * the wrapper's own check, that would rebuild the entire plate on top of itself.
	 */
	private static void build(Job job, BlockPos anchorXZ) {
		int surfaceY = job.world().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				anchorXZ.getX(), anchorXZ.getZ());
		BlockPos origin = new BlockPos(anchorXZ.getX(),
				Math.max(surfaceY, WorldRules.INDUSTRIAL_Y_MAX + 24), anchorXZ.getZ());
		long seed = job.world().getSeed() ^ SALT
				^ ((long) job.cellX() * 341873128712L) ^ ((long) job.cellZ() * 132897987541L);
		MegaStructureGenerator.generate(job.world(), origin, MacroEntry.Kind.MEGACITY, Random.create(seed));
	}

	private static long pack(int cellX, int cellZ) {
		return ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
	}
}

package com.terminaldetector.drmd.world.level;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Builds the Nether and End <em>levels</em> into the expanded Overworld column.
 *
 * <p>Vanilla worldgen only fills −64 … 320. This fills the rest: a capped basalt cavern down at
 * the bottom of the column, End-stone shards up at the top, and shafts punched through the bedrock
 * so a Pyro can fly between them without a portal.
 *
 * <p>Work is queued on chunk load and drained against a per-tick block budget rather than done
 * inline — a level slab is a few thousand block writes, and doing that during chunk load would
 * stutter every time a player crosses a chunk border.
 */
public final class LevelBuilder {
	/** Block writes per server tick, across all queued chunks. */
	private static final int BUDGET_PER_TICK = 2_400;
	/** Stop queueing entirely if the backlog gets silly (player teleporting around). */
	private static final int MAX_QUEUE = 512;

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	private record Job(ServerWorld world, int chunkX, int chunkZ) {}

	private LevelBuilder() {}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(LevelBuilder::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(LevelBuilder::drain);
		DescentMod.LOGGER.info("Level builder online — Nether and End are bands of the main world");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		// Nothing to do unless the expanded dimension type actually took effect.
		if (world.getBottomY() > WorldLevels.NETHER_FLOOR) return;
		if (QUEUE.size() >= MAX_QUEUE) return;
		ChunkPos cp = chunk.getPos();
		long key = cp.toLong();
		if (QUEUED.contains(key)) return;
		// Cheap already-built probe: the floor marker at the chunk centre.
		//
		// Read it off the chunk being handed to us, never through the world. A chunk is not in the
		// full-status map during its own load event, so world.getBlockState here would ask the chunk
		// manager to load the chunk that is already loading: the future it waits on cannot complete,
		// and the server thread sits in that wait forever. That is a hang with no crash report —
		// world creation stopping a few percent in.
		BlockPos probe = new BlockPos(cp.getStartX() + 8, WorldLevels.NETHER_FLOOR, cp.getStartZ() + 8);
		if (chunk.getBlockState(probe).isOf(Blocks.BEDROCK)) return;
		QUEUED.add(key);
		QUEUE.add(new Job(world, cp.x, cp.z));
	}

	private static void drain(net.minecraft.server.MinecraftServer server) {
		int budget = BUDGET_PER_TICK;
		while (budget > 0 && !QUEUE.isEmpty()) {
			Job job = QUEUE.poll();
			QUEUED.remove(ChunkPos.toLong(job.chunkX, job.chunkZ));
			if (job.world.isChunkLoaded(job.chunkX, job.chunkZ)) {
				budget -= build(job.world, job.chunkX, job.chunkZ);
			}
		}
	}

	/** Build one chunk's share of every level. Returns the number of blocks written. */
	private static int build(ServerWorld world, int chunkX, int chunkZ) {
		long seed = world.getSeed() ^ (((long) chunkX) * 341873128712L) ^ (((long) chunkZ) * 132897987541L);
		Random random = Random.create(seed);
		int written = 0;
		written += buildNetherLevel(world, chunkX, chunkZ, random);
		written += buildEndLevel(world, chunkX, chunkZ, seed, random);
		if (WorldLevels.isShaftChunk(chunkX, chunkZ)) {
			written += cutDescentShaft(world, chunkX, chunkZ);
		}
		return written;
	}

	// ------------------------------------------------------------------ nether level

	private static int buildNetherLevel(ServerWorld world, int chunkX, int chunkZ, Random random) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;

				// Floor slab: bedrock cap under a basalt/netherrack crust.
				for (int i = 0; i < WorldLevels.NETHER_FLOOR_THICKNESS; i++) {
					int y = WorldLevels.NETHER_FLOOR + i;
					BlockState state = i == 0
							? Blocks.BEDROCK.getDefaultState()
							: pickNetherGround(random);
					written += set(world, pos.set(x, y, z), state);
				}
				// Shallow lava seas in the low spots.
				if (random.nextInt(9) == 0) {
					written += set(world, pos.set(x, WorldLevels.NETHER_FLOOR
							+ WorldLevels.NETHER_FLOOR_THICKNESS, z), Blocks.LAVA.getDefaultState());
				}

				// Ceiling slab, with the odd glowstone boil hanging off it.
				for (int i = 0; i < WorldLevels.NETHER_CEILING_THICKNESS; i++) {
					int y = WorldLevels.NETHER_CEILING - i;
					written += set(world, pos.set(x, y, z), i == 0
							? Blocks.BEDROCK.getDefaultState()
							: Blocks.BASALT.getDefaultState());
				}
				if (random.nextInt(48) == 0) {
					written += set(world, pos.set(x, WorldLevels.NETHER_CEILING
							- WorldLevels.NETHER_CEILING_THICKNESS, z), Blocks.GLOWSTONE.getDefaultState());
				}
			}
		}

		// One or two columns tying floor to ceiling, so the cavern reads as a space, not a gap.
		int pillars = random.nextInt(3);
		for (int p = 0; p < pillars; p++) {
			int cx = baseX + 2 + random.nextInt(12);
			int cz = baseZ + 2 + random.nextInt(12);
			int radius = 1 + random.nextInt(2);
			for (int y = WorldLevels.NETHER_FLOOR + WorldLevels.NETHER_FLOOR_THICKNESS;
				 y < WorldLevels.NETHER_CEILING - WorldLevels.NETHER_CEILING_THICKNESS; y += 1) {
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (dx * dx + dz * dz > radius * radius) continue;
						written += set(world, pos.set(cx + dx, y, cz + dz), Blocks.BASALT.getDefaultState());
					}
				}
			}
		}
		return written;
	}

	private static BlockState pickNetherGround(Random random) {
		int roll = random.nextInt(10);
		if (roll < 5) return Blocks.NETHERRACK.getDefaultState();
		if (roll < 8) return Blocks.BLACKSTONE.getDefaultState();
		return Blocks.MAGMA_BLOCK.getDefaultState();
	}

	// ------------------------------------------------------------------ end level

	private static int buildEndLevel(ServerWorld world, int chunkX, int chunkZ, long seed, Random random) {
		// Sparse: the End level is an archipelago, not a floor.
		if (Math.floorMod(seed >> 5, 6L) != 0L) return 0;

		int cx = (chunkX << 4) + 4 + random.nextInt(8);
		int cz = (chunkZ << 4) + 4 + random.nextInt(8);
		int cy = WorldLevels.END_ISLAND_MIN
				+ random.nextInt(WorldLevels.END_ISLAND_MAX - WorldLevels.END_ISLAND_MIN);
		int radius = 5 + random.nextInt(5);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;

		// Lens-shaped shard: wide at the top, tapering underneath.
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double horizontal = Math.sqrt(dx * dx + dz * dz);
				if (horizontal > radius) continue;
				int depth = (int) ((1.0 - horizontal / radius) * radius * 1.4) + 1;
				for (int d = 0; d < depth; d++) {
					BlockState state = d == 0 ? Blocks.END_STONE.getDefaultState()
							: (random.nextInt(12) == 0 ? Blocks.PURPUR_BLOCK.getDefaultState()
							: Blocks.END_STONE.getDefaultState());
					written += set(world, pos.set(cx + dx, cy - d, cz + dz), state);
				}
			}
		}
		// Obsidian spire on the larger shards.
		if (radius >= 8) {
			int height = 8 + random.nextInt(10);
			for (int h = 1; h <= height; h++) {
				written += set(world, pos.set(cx, cy + h, cz), Blocks.OBSIDIAN.getDefaultState());
			}
			written += set(world, pos.set(cx, cy + height + 1, cz), Blocks.END_ROD.getDefaultState());
		}
		return written;
	}

	// ------------------------------------------------------------------ connectors

	/**
	 * Punch through the old world floor so the Abyss is reachable by flying rather than by portal.
	 *
	 * <p>Everything under −64 is already void, so only the bedrock plug and the stone above it
	 * need clearing — plus a matching hole in the Nether ceiling underneath.
	 */
	private static int cutDescentShaft(ServerWorld world, int chunkX, int chunkZ) {
		int cx = (chunkX << 4) + 8;
		int cz = (chunkZ << 4) + 8;
		int r = WorldLevels.SHAFT_RADIUS;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;

		for (int y = -40; y >= world.getBottomY() + 1 && y >= -70; y--) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > r * r) continue;
					written += set(world, pos.set(cx + dx, y, cz + dz), Blocks.AIR.getDefaultState());
				}
			}
		}
		// Rim marker so the shaft is findable from the surface side.
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > (r + 1) * (r + 1) || d2 <= r * r) continue;
				written += set(world, pos.set(cx + dx, -40, cz + dz), Blocks.SEA_LANTERN.getDefaultState());
			}
		}
		// Matching hole in the Nether ceiling.
		for (int i = 0; i <= WorldLevels.NETHER_CEILING_THICKNESS; i++) {
			int y = WorldLevels.NETHER_CEILING - i;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > r * r) continue;
					written += set(world, pos.set(cx + dx, y, cz + dz), Blocks.AIR.getDefaultState());
				}
			}
		}
		return written;
	}

	/** Write without neighbour updates; out-of-range writes are a silent no-op in Minecraft. */
	private static int set(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.isOutOfHeightLimit(pos)) return 0;
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		return 1;
	}
}

package com.terminaldetector.drmd.world.level;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the Nether / mantle / End <em>levels</em> into the expanded Overworld column.
 *
 * <p>Bedrock is never a world border — vanilla bedrock → diggable plasma-resistant granite.
 * Dig path: granite crust → mixed mantle → continuous netherrack = seamless Core (HL2-style).
 *
 * <p>Heavy fills stream via {@link MantleStream} and drain in Y-slices against a tick budget.
 */
public final class LevelBuilder {
	private static final int BUDGET_PER_TICK = 2_800;
	private static final int MAX_QUEUE = 512;
	private static final int MANTLE_PROBE_Y = -120;
	/** Mantle Y-rows per drain step (16×16 each). */
	private static final int MANTLE_ROWS_PER_STEP = 4;
	/** End-band chunks examined per tick, island or not. */
	private static final int END_JOBS_PER_TICK = 64;

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	/**
	 * End-band work on its own queue.
	 *
	 * <p>A pilot climbing into the band needs its islands and nothing else. Putting them on the
	 * column queue would start each chunk at phase 0 — the whole mantle fill, tens of thousands of
	 * writes underneath a player who is nine hundred blocks above it — and the islands would arrive
	 * only after all of that had drained.
	 */
	private static final Deque<EndJob> END_QUEUE = new ArrayDeque<>();
	private static final Set<Long> END_QUEUED = new HashSet<>();

	/** phase 0=mantle (cursorY), 1=nether floor, 2=nether ceiling, 3=shaft/end finish */
	private record Job(ServerWorld world, int chunkX, int chunkZ, int phase, int cursorY) {}

	private record EndJob(ServerWorld world, int chunkX, int chunkZ) {}

	private LevelBuilder() {}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(LevelBuilder::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(LevelBuilder::drain);
		DescentMod.LOGGER.info("Level builder online — diggable mantle / Core band (no bedrock border)");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (world.getBottomY() > WorldLevels.NETHER_FLOOR) return;

		rewriteBedrock(chunk);
		ensureCrustPlug(chunk);

		if (!com.terminaldetector.drmd.world.WorldFeatures.NETHER_BAND) return;
		if (!MantleStream.shouldBuildFull(world, chunk.getPos().x, chunk.getPos().z)) return;
		enqueue(world, chunk.getPos().x, chunk.getPos().z);
	}

	private static void enqueue(ServerWorld world, int chunkX, int chunkZ) {
		if (QUEUE.size() >= MAX_QUEUE) return;
		long key = ChunkPos.toLong(chunkX, chunkZ);
		if (QUEUED.contains(key)) return;
		if (world.isChunkLoaded(chunkX, chunkZ) && mantleBuilt(world.getChunk(chunkX, chunkZ))) return;
		QUEUED.add(key);
		QUEUE.add(new Job(world, chunkX, chunkZ, 0, WorldLevels.ABYSS_TOP - 1));
	}

	/**
	 * Background stream for {@link com.terminaldetector.drmd.world.layer.SeamWarmup}: enqueue the
	 * mantle/Nether build around a chunk without waiting for CHUNK_LOAD.
	 */
	public static void streamAround(ServerWorld world, int chunkX, int chunkZ, int radius) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		int r = Math.max(0, Math.min(radius, 10));
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				enqueue(world, chunkX + dx, chunkZ + dz);
			}
		}
	}

	/**
	 * Same, for the End band alone — used while a pilot is inside it or climbing toward the seam.
	 *
	 * <p>CHUNK_LOAD covers chunks a pilot flies into. It cannot cover the chunks that were already
	 * loaded when they turned upward, and those are the ones under a climb, so without this pass the
	 * band opens as empty sky and fills in only where the pilot has not been yet.
	 */
	public static void streamEndBand(ServerWorld world, int chunkX, int chunkZ, int radius) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (!com.terminaldetector.drmd.world.WorldFeatures.END_BAND) return;
		int r = Math.max(0, Math.min(radius, 10));
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				enqueueEndBand(world, chunkX + dx, chunkZ + dz);
			}
		}
	}

	private static void enqueueEndBand(ServerWorld world, int chunkX, int chunkZ) {
		if (END_QUEUE.size() >= MAX_QUEUE) return;
		long key = ChunkPos.toLong(chunkX, chunkZ);
		if (!END_QUEUED.add(key)) return;
		END_QUEUE.add(new EndJob(world, chunkX, chunkZ));
	}

	private static boolean mantleBuilt(WorldChunk chunk) {
		BlockPos probe = new BlockPos(
				chunk.getPos().getStartX() + 8, MANTLE_PROBE_Y, chunk.getPos().getStartZ() + 8);
		BlockState st = chunk.getBlockState(probe);
		return !st.isAir() && !st.isOf(Blocks.CAVE_AIR) && !st.isOf(Blocks.VOID_AIR);
	}

	private static void drain(net.minecraft.server.MinecraftServer server) {
		ServerWorld ow = server.getOverworld();
		if (ow != null && com.terminaldetector.drmd.world.WorldFeatures.NETHER_BAND) {
			for (Digger d : nearbyDiggers(server)) {
				for (int dx = -MantleStream.STREAM_CHUNKS; dx <= MantleStream.STREAM_CHUNKS; dx++) {
					for (int dz = -MantleStream.STREAM_CHUNKS; dz <= MantleStream.STREAM_CHUNKS; dz++) {
						enqueue(ow, d.cx + dx, d.cz + dz);
					}
				}
			}
		}

		int budget = BUDGET_PER_TICK;
		while (budget > 0 && !QUEUE.isEmpty()) {
			Job job = QUEUE.poll();
			long key = ChunkPos.toLong(job.chunkX, job.chunkZ);
			QUEUED.remove(key);
			if (!job.world.isChunkLoaded(job.chunkX, job.chunkZ)) continue;
			StepResult step = step(job, budget);
			budget -= step.written;
			if (!step.done) {
				QUEUED.add(key);
				QUEUE.addFirst(step.next);
			}
		}

		// End band gets what is left. One island is a single indivisible step, so it runs on the
		// remaining budget rather than reserving its own — the column keeps priority when a pilot is
		// digging and flying at once.
		//
		// Bounded by count as well as by budget: most chunks in the band have no island, cost
		// nothing, and would let one tick walk the entire queue however long the stream made it.
		int endJobs = 0;
		while (budget > 0 && endJobs++ < END_JOBS_PER_TICK && !END_QUEUE.isEmpty()) {
			EndJob job = END_QUEUE.poll();
			END_QUEUED.remove(ChunkPos.toLong(job.chunkX, job.chunkZ));
			if (!job.world.isChunkLoaded(job.chunkX, job.chunkZ)) continue;
			long seed = job.world.getSeed()
					^ (((long) job.chunkX) * 341873128712L)
					^ (((long) job.chunkZ) * 132897987541L);
			budget -= buildEndLevel(job.world, job.chunkX, job.chunkZ, seed, Random.create(seed));
		}
	}

	private record Digger(int cx, int cz) {}
	private record StepResult(int written, boolean done, Job next) {}

	private static List<Digger> nearbyDiggers(net.minecraft.server.MinecraftServer server) {
		List<Digger> list = new ArrayList<>(4);
		for (var p : server.getPlayerManager().getPlayerList()) {
			if (p.getWorld().getRegistryKey() != World.OVERWORLD) continue;
			// Diggers below industrial, or anyone approaching the Nether Core seam (SeamWarmup).
			if (p.getY() > WorldLevels.INDUSTRIAL_TOP + 24
					&& !com.terminaldetector.drmd.world.layer.SeamWarmup.nearNetherSeam(p.getY())) {
				continue;
			}
			list.add(new Digger(p.getBlockX() >> 4, p.getBlockZ() >> 4));
		}
		return list;
	}

	private static StepResult step(Job job, int budget) {
		long seed = job.world.getSeed()
				^ (((long) job.chunkX) * 341873128712L)
				^ (((long) job.chunkZ) * 132897987541L);
		Random random = Random.create(seed);

		if (job.phase == 0) {
			int written = 0;
			int y = job.cursorY;
			int rows = 0;
			while (y >= WorldLevels.NETHER_CEILING && rows < MANTLE_ROWS_PER_STEP && written < budget) {
				written += fillMantleRow(job.world, job.chunkX, job.chunkZ, y, random);
				y--;
				rows++;
			}
			if (y >= WorldLevels.NETHER_CEILING) {
				return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 0, y));
			}
			return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 1, 0));
		}

		// The band is written floor first, then ceiling, then everything else. Relief costs several
		// times what the two flat slabs did, and one chunk that overruns the tick budget by that much
		// is a visible hitch — splitting it lets the drain stop between the two halves.
		//
		// world.getSeed(), not the chunk-mixed `seed` above: that one is right for `random` (block
		// variety should differ chunk to chunk) and wrong for a height field two neighbouring chunks
		// have to agree on. Feeding the mixed seed to NetherRelief was exactly the bug that shipped —
		// see buildNetherFloor's doc.
		if (job.phase == 1) {
			int written = buildNetherFloor(job.world, job.chunkX, job.chunkZ, job.world.getSeed(), random);
			return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 2, 0));
		}

		if (job.phase == 2) {
			int written = buildNetherCeiling(job.world, job.chunkX, job.chunkZ, job.world.getSeed(), random);
			return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 3, 0));
		}

		int written = 0;
		if (com.terminaldetector.drmd.world.WorldFeatures.END_BAND) {
			written += buildEndLevel(job.world, job.chunkX, job.chunkZ, seed, random);
		}
		if (WorldLevels.isShaftChunk(job.chunkX, job.chunkZ)) {
			written += cutDescentShaft(job.world, job.chunkX, job.chunkZ);
		}
		return new StepResult(written, true, job);
	}

	private static int fillMantleRow(ServerWorld world, int chunkX, int chunkZ, int y, Random random) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
		int top = WorldLevels.ABYSS_TOP;
		int bottom = WorldLevels.NETHER_CEILING;
		int span = Math.max(1, top - bottom);
		float t = (float) (top - y) / (float) span;
		boolean shaft = WorldLevels.isShaftChunk(chunkX, chunkZ);
		int scx = baseX + 8;
		int scz = baseZ + 8;
		int r = WorldLevels.SHAFT_RADIUS;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;
				if (shaft) {
					int odx = x - scx;
					int odz = z - scz;
					if (odx * odx + odz * odz <= r * r) continue;
				}
				BlockState state;
				if (t < 0.12f) {
					state = random.nextFloat() < 0.7f
							? ModWorldBlocks.PLASMA_GRANITE.getDefaultState()
							: Blocks.GRANITE.getDefaultState();
				} else if (t < 0.45f) {
					float mix = (t - 0.12f) / 0.33f;
					if (random.nextFloat() < mix * 0.55f) {
						state = pickNetherGround(random);
					} else if (random.nextFloat() < 0.35f) {
						state = ModWorldBlocks.PLASMA_GRANITE.getDefaultState();
					} else {
						state = Blocks.GRANITE.getDefaultState();
					}
				} else if (t < 0.75f) {
					state = random.nextFloat() < 0.65f
							? pickNetherGround(random)
							: Blocks.BLACKSTONE.getDefaultState();
				} else {
					state = pickNetherGround(random);
				}
				if (t > 0.55f && random.nextInt(80) == 0) {
					state = Blocks.LAVA.getDefaultState();
				}
				written += set(world, pos.set(x, y, z), state);
			}
		}
		return written;
	}

	private static void ensureCrustPlug(WorldChunk chunk) {
		int baseX = chunk.getPos().getStartX();
		int baseZ = chunk.getPos().getStartZ();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		// Skip if the crust seam is already solid — avoids rewriting on every reload.
		pos.set(baseX + 8, WorldLevels.ABYSS_TOP - 2, baseZ + 8);
		BlockState probe = chunk.getBlockState(pos);
		if (!probe.isAir() && !probe.isOf(Blocks.CAVE_AIR) && !probe.isOf(Blocks.VOID_AIR)
				&& !probe.isOf(Blocks.BEDROCK)
				&& (probe.isOf(ModWorldBlocks.PLASMA_GRANITE) || probe.isOf(Blocks.GRANITE)
				|| probe.isOf(Blocks.STONE) || probe.isOf(Blocks.DEEPSLATE))) {
			return;
		}
		Random random = Random.create((baseX * 31L) ^ baseZ);
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				for (int y = WorldLevels.ABYSS_TOP - 1; y >= WorldLevels.ABYSS_TOP - 6; y--) {
					pos.set(baseX + dx, y, baseZ + dz);
					BlockState cur = chunk.getBlockState(pos);
					if (!cur.isAir() && !cur.isOf(Blocks.CAVE_AIR) && !cur.isOf(Blocks.BEDROCK)
							&& !cur.isOf(Blocks.VOID_AIR)) {
						continue;
					}
					BlockState st = random.nextFloat() < 0.65f
							? ModWorldBlocks.PLASMA_GRANITE.getDefaultState()
							: Blocks.GRANITE.getDefaultState();
					chunk.setBlockState(pos, st, false);
				}
			}
		}
	}

	/**
	 * Replace unbreakable bedrock with diggable plasma granite.
	 *
	 * <p>Must stay cheap: this runs on every {@code CHUNK_LOAD}, including during
	 * "Preparing spawn area". Scanning the whole −512…−56 band (~450 Y × 256) per
	 * chunk freezes the join at 100% and trips the watchdog. Bedrock only exists in
	 * thin floor/cap bands — rewrite those only.
	 */
	private static int rewriteBedrock(WorldChunk chunk) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int minY = chunk.getBottomY();
		int heightTop = minY + chunk.getHeight();
		int written = 0;
		// Column-floor bedrock.
		written += rewriteBedrockBand(chunk, pos, minY, Math.min(heightTop, minY + 8));
		// Old −64 seam / crust leftovers.
		written += rewriteBedrockBand(chunk, pos,
				Math.max(minY, WorldLevels.ABYSS_TOP - 8),
				Math.min(heightTop, WorldLevels.ABYSS_TOP + 2));
		return written;
	}

	private static int rewriteBedrockBand(WorldChunk chunk, BlockPos.Mutable pos, int y0, int y1) {
		if (y0 >= y1) return 0;
		int written = 0;
		int startX = chunk.getPos().getStartX();
		int startZ = chunk.getPos().getStartZ();
		for (int y = y0; y < y1; y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					pos.set(startX + x, y, startZ + z);
					if (chunk.getBlockState(pos).isOf(Blocks.BEDROCK)) {
						chunk.setBlockState(pos, ModWorldBlocks.PLASMA_GRANITE.getDefaultState(), false);
						written++;
					}
				}
			}
		}
		return written;
	}

	/**
	 * The floor of the Nether band: ground that rises and falls, with lava in what it does not reach.
	 *
	 * <p>Relief is the whole point. A flat slab under a flat ceiling is a room a hundred and eighty
	 * blocks tall, and from a cockpit that reads as nothing at all — there is no scale in it and
	 * nothing to fly around. Heights come from {@link NetherRelief}, which is a pure function of
	 * world position, so the chunk built now and its neighbour built ten seconds later meet without
	 * a step — <strong>provided both are given the same seed</strong>. That is why this takes
	 * {@code worldSeed} rather than the chunk-mixed {@code seed} that {@link #step} already has in
	 * scope for its {@link Random}: passing the mixed one here was the actual shipped bug — every
	 * chunk sampled the height field under a different seed, so neighbours agreed on nothing and
	 * every chunk border rendered as a cliff. Seen from altitude across a wide stream of chunks, a
	 * grid of cliffs one next to the other reads as vertical stripes.
	 */
	private static int buildNetherFloor(ServerWorld world, int chunkX, int chunkZ, long worldSeed, Random random) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
		int lava = NetherRelief.lavaLevel();
		boolean shaft = WorldLevels.isShaftChunk(chunkX, chunkZ);
		int scx = baseX + 8;
		int scz = baseZ + 8;
		int sr = WorldLevels.SHAFT_RADIUS;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;
				// The descent shafts have to survive the fill, or the way down ends in the ceiling.
				boolean inShaft = shaft
						&& (x - scx) * (x - scx) + (z - scz) * (z - scz) <= sr * sr;

				// Sealed base — the column's floor, not a border: still diggable granite.
				written += set(world, pos.set(x, WorldLevels.NETHER_FLOOR, z),
						ModWorldBlocks.PLASMA_GRANITE.getDefaultState());

				int top = NetherRelief.floorTop(worldSeed, x, z);
				if (!inShaft) {
					for (int y = WorldLevels.NETHER_FLOOR + 1; y <= top; y++) {
						written += set(world, pos.set(x, y, z), pickNetherGround(random));
					}
				}
				// Lava fills the low ground. Above the sea the same columns are the coast.
				if (!inShaft) {
					for (int y = Math.max(top + 1, WorldLevels.NETHER_FLOOR + 1); y <= lava; y++) {
						written += set(world, pos.set(x, y, z), Blocks.LAVA.getDefaultState());
					}
				}
				// A crust of magma where ground meets sea reads as heat rather than as a bathtub edge.
				if (!inShaft && top >= lava - 1 && top <= lava + 2 && random.nextInt(4) == 0) {
					written += set(world, pos.set(x, top, z), Blocks.MAGMA_BLOCK.getDefaultState());
				}
			}
		}
		return written;
	}

	/**
	 * The ceiling, hanging down to meet the floor in places.
	 *
	 * <p>Where the two nearly touch the band pinches into a corridor, which is what gives a pilot
	 * something to thread. Glowstone goes on the underside in clusters rather than one block at a
	 * time — scattered singles are invisible from any distance worth flying at.
	 */
	private static int buildNetherCeiling(ServerWorld world, int chunkX, int chunkZ, long worldSeed, Random random) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
		boolean shaft = WorldLevels.isShaftChunk(chunkX, chunkZ);
		int scx = baseX + 8;
		int scz = baseZ + 8;
		int sr = WorldLevels.SHAFT_RADIUS;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;
				boolean inShaft = shaft
						&& (x - scx) * (x - scx) + (z - scz) * (z - scz) <= sr * sr;

				written += set(world, pos.set(x, WorldLevels.NETHER_CEILING, z),
						ModWorldBlocks.PLASMA_GRANITE.getDefaultState());
				if (inShaft) continue;

				int bottom = NetherRelief.ceilingBottom(worldSeed, x, z);
				for (int y = WorldLevels.NETHER_CEILING - 1; y >= bottom; y--) {
					written += set(world, pos.set(x, y, z),
							random.nextInt(5) == 0 ? Blocks.BLACKSTONE.getDefaultState()
									: Blocks.BASALT.getDefaultState());
				}
				// Clustered by position, not per block: one lattice cell in a few lights up whole.
				if (NetherRelief.value(worldSeed ^ 0x91E10DA5L, x, z, 9) > 0.86f) {
					written += set(world, pos.set(x, bottom, z), Blocks.GLOWSTONE.getDefaultState());
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

	/**
	 * One End-band island for this chunk, if the seed puts one here.
	 *
	 * <p>The shape lives in {@link com.terminaldetector.drmd.world.gen2.EndIslandGenerator} and is
	 * shared with the chunk-load pass, so a pilot who flies into the band sees the same archipelago
	 * the background stream would have built for them. The returned figure is the drain budget's
	 * charge for the island — its footprint, which tracks the write count closely enough to keep one
	 * island from eating a whole tick's allowance unnoticed.
	 */
	private static int buildEndLevel(ServerWorld world, int chunkX, int chunkZ, long seed, Random random) {
		BlockPos origin = com.terminaldetector.drmd.world.gen2.EndIslandGenerator
				.originFor(chunkX, chunkZ, seed);
		if (origin == null) return 0;
		if (world.isOutOfHeightLimit(origin)) return 0;
		if (com.terminaldetector.drmd.world.gen2.EndIslandGenerator.built(world, origin)) return 0;
		var island = com.terminaldetector.drmd.world.gen2.EndIslandGenerator
				.generate(world, origin, random);
		return island == null ? 0 : island.sizeX * island.sizeZ;
	}

	private static int cutDescentShaft(ServerWorld world, int chunkX, int chunkZ) {
		int cx = (chunkX << 4) + 8;
		int cz = (chunkZ << 4) + 8;
		int r = WorldLevels.SHAFT_RADIUS;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
		for (int y = -40; y >= WorldLevels.NETHER_FLOOR + WorldLevels.NETHER_FLOOR_THICKNESS + 4; y--) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > r * r) continue;
					written += set(world, pos.set(cx + dx, y, cz + dz), Blocks.AIR.getDefaultState());
				}
			}
		}
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > (r + 1) * (r + 1) || d2 <= r * r) continue;
				written += set(world, pos.set(cx + dx, -40, cz + dz), Blocks.SEA_LANTERN.getDefaultState());
			}
		}
		return written;
	}

	private static int set(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.isOutOfHeightLimit(pos)) return 0;
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		return 1;
	}
}

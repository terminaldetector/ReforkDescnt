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

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	/** phase 0=mantle (cursorY), 1=nether cavern, 2=shaft/end finish */
	private record Job(ServerWorld world, int chunkX, int chunkZ, int phase, int cursorY) {}

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
	}

	private record Digger(int cx, int cz) {}
	private record StepResult(int written, boolean done, Job next) {}

	private static List<Digger> nearbyDiggers(net.minecraft.server.MinecraftServer server) {
		List<Digger> list = new ArrayList<>(4);
		for (var p : server.getPlayerManager().getPlayerList()) {
			if (p.getWorld().getRegistryKey() != World.OVERWORLD) continue;
			if (p.getY() > WorldLevels.INDUSTRIAL_TOP + 24) continue;
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

		if (job.phase == 1) {
			int written = buildNetherLevel(job.world, job.chunkX, job.chunkZ, random);
			return new StepResult(written, false, new Job(job.world, job.chunkX, job.chunkZ, 2, 0));
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

	private static int buildNetherLevel(ServerWorld world, int chunkX, int chunkZ, Random random) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;

				for (int i = 0; i < WorldLevels.NETHER_FLOOR_THICKNESS; i++) {
					int y = WorldLevels.NETHER_FLOOR + i;
					BlockState state = i == 0
							? ModWorldBlocks.PLASMA_GRANITE.getDefaultState()
							: pickNetherGround(random);
					written += set(world, pos.set(x, y, z), state);
				}
				if (random.nextInt(9) == 0) {
					written += set(world, pos.set(x, WorldLevels.NETHER_FLOOR
							+ WorldLevels.NETHER_FLOOR_THICKNESS, z), Blocks.LAVA.getDefaultState());
				}

				for (int i = 0; i < WorldLevels.NETHER_CEILING_THICKNESS; i++) {
					int y = WorldLevels.NETHER_CEILING - i;
					written += set(world, pos.set(x, y, z), i == 0
							? ModWorldBlocks.PLASMA_GRANITE.getDefaultState()
							: Blocks.BASALT.getDefaultState());
				}
				if (random.nextInt(48) == 0) {
					written += set(world, pos.set(x, WorldLevels.NETHER_CEILING
							- WorldLevels.NETHER_CEILING_THICKNESS, z), Blocks.GLOWSTONE.getDefaultState());
				}
			}
		}

		int pillars = random.nextInt(3);
		for (int p = 0; p < pillars; p++) {
			int cx = baseX + 2 + random.nextInt(12);
			int cz = baseZ + 2 + random.nextInt(12);
			int radius = 1 + random.nextInt(2);
			for (int y = WorldLevels.NETHER_FLOOR + WorldLevels.NETHER_FLOOR_THICKNESS;
				 y < WorldLevels.NETHER_CEILING - WorldLevels.NETHER_CEILING_THICKNESS; y++) {
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

	private static int buildEndLevel(ServerWorld world, int chunkX, int chunkZ, long seed, Random random) {
		if (Math.floorMod(seed >> 5, 6L) != 0L) return 0;
		int cx = (chunkX << 4) + 4 + random.nextInt(8);
		int cz = (chunkZ << 4) + 4 + random.nextInt(8);
		int cy = WorldLevels.END_ISLAND_MIN
				+ random.nextInt(WorldLevels.END_ISLAND_MAX - WorldLevels.END_ISLAND_MIN);
		int radius = 5 + random.nextInt(5);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int written = 0;
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
		if (radius >= 8) {
			int height = 8 + random.nextInt(10);
			for (int h = 1; h <= height; h++) {
				written += set(world, pos.set(cx, cy + h, cz), Blocks.OBSIDIAN.getDefaultState());
			}
			written += set(world, pos.set(cx, cy + height + 1, cz), Blocks.END_ROD.getDefaultState());
		}
		return written;
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

package com.terminaldetector.drmd.world.orbit;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.base.DescentSession;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MegaStructureGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Sparse orbit content: junk plates (A/B) and techno-ring satellites.
 * Not the End — End remains a separate max-height task.
 */
public final class OrbitJunkWorldgen {
	private OrbitJunkWorldgen() {}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(OrbitJunkWorldgen::onChunkLoad);
		DescentMod.LOGGER.info("Orbit junk / techno-ring scaffold registered");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!WorldFeatures.SURFACE_DISTRICTS) return;
		if (world.getRegistryKey() != World.OVERWORLD) return;

		ChunkPos cp = chunk.getPos();
		int cx = cp.getCenterX();
		int cz = cp.getCenterZ();
		long seed = world.getSeed() ^ (((long) cp.x) * 0x4F9939L) ^ (((long) cp.z) * 0x1EF37DL);

		// Techno-ring satellite — rare, only on ring cells
		if (OrbitBands.inRing(cx, cz) && Math.floorMod(seed, 11L) == 0L) {
			BlockPos at = new BlockPos(cx, OrbitBands.RING_Y, cz);
			if (!chunk.getBlockState(at).isOf(Blocks.LODESTONE)) {
				world.getServer().execute(() -> {
					if (world.getBlockState(at).isOf(Blocks.LODESTONE)) return;
					DescentSession.enqueueLandmark(at, () -> {
						MegaStructureGenerator.generate(world, at,
								pickRingKind(seed), Random.create(seed));
						world.setBlockState(at, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
					});
				});
			}
			return;
		}

		// Layer A / B junk — sparse debris platforms beside / near ring
		if (OrbitBands.besideRing(cx, cz) && Math.floorMod(seed >> 3, 17L) == 0L) {
			boolean layerB = Math.floorMod(seed >> 7, 2L) == 0L;
			int y = layerB ? OrbitBands.LAYER_B_Y : OrbitBands.LAYER_A_Y;
			BlockPos at = new BlockPos(cx, y, cz);
			world.getServer().execute(() -> placeJunkPlate(world, at, Random.create(seed)));
		}
	}

	private static MacroEntry.Kind pickRingKind(long seed) {
		MacroEntry.Kind[] kinds = {
				MacroEntry.Kind.STATION, MacroEntry.Kind.RING, MacroEntry.Kind.ARCH,
				MacroEntry.Kind.INVERTED_ISLAND, MacroEntry.Kind.FLOATING_CONTINENT
		};
		return kinds[(int) Math.floorMod(seed >> 5, kinds.length)];
	}

	private static void placeJunkPlate(ServerWorld world, BlockPos at, Random random) {
		if (world.getBlockState(at).isOf(Blocks.IRON_BLOCK)) return;
		int r = 4 + random.nextInt(5);
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > r * r) continue;
				m.set(at.getX() + x, at.getY(), at.getZ() + z);
				Block b = switch (random.nextInt(5)) {
					case 0 -> Blocks.IRON_BLOCK;
					case 1 -> Blocks.OXIDIZED_COPPER;
					case 2 -> Blocks.DEEPSLATE_TILES;
					case 3 -> Blocks.GRAY_CONCRETE;
					default -> Blocks.CHAIN;
				};
				world.setBlockState(m, b.getDefaultState(), Block.NOTIFY_LISTENERS);
				if (random.nextFloat() < 0.12f) {
					world.setBlockState(m.up(), Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		// Occasional inverted gravity cue block for high-altitude 6DoF villages later
		if (random.nextFloat() < 0.2f) {
			world.setBlockState(at.up(),
					com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState(),
					Block.NOTIFY_LISTENERS);
		}
	}
}

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
 * Sparse techno-ring satellites. Older worlds can still contain debris plates.
 * Not the End — End remains a separate max-height task.
 */
public final class OrbitJunkWorldgen {
	private OrbitJunkWorldgen() {}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(OrbitJunkWorldgen::onChunkLoad);
		DescentMod.LOGGER.info("Techno-ring satellite worldgen registered");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!WorldFeatures.ORBIT_JUNK) return;
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
		}
	}

	private static MacroEntry.Kind pickRingKind(long seed) {
		MacroEntry.Kind[] kinds = {
				MacroEntry.Kind.STATION, MacroEntry.Kind.RING, MacroEntry.Kind.ARCH,
				MacroEntry.Kind.INVERTED_ISLAND, MacroEntry.Kind.FLOATING_CONTINENT
		};
		return kinds[(int) Math.floorMod(seed >> 5, kinds.length)];
	}
}

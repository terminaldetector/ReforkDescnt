package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.mega.DroneSwarmEntity;
import com.terminaldetector.drmd.world.mega.MegaWormEntity;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;

/**
 * World Generation 2.0 — experimental multi-scale celestial / rift generation
 * plus rare mega-creature anchors. Practical Y mapped into Overworld height.
 */
public final class ModWorldgen2 {
	private ModWorldgen2() {}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(ModWorldgen2::onChunkLoad);
		DescentMod.LOGGER.info("Registered World Generation 2.0 (mega-structures + mega fauna)");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != ServerWorld.OVERWORLD) return;
		ChunkPos cp = chunk.getPos();
		long seed = world.getSeed() ^ (((long) cp.x) * 341873128712L) ^ (((long) cp.z) * 132897987541L);

		// ~1 mega-structure per 18 chunks (stock sky / rift generation)
		if (Math.floorMod(seed, 18L) == 0L) {
			MacroEntry.Kind[] kinds = {
					MacroEntry.Kind.ARCH, MacroEntry.Kind.RING, MacroEntry.Kind.FLOATING_CONTINENT,
					MacroEntry.Kind.SPIRAL_RANGE, MacroEntry.Kind.INVERTED_ISLAND,
					MacroEntry.Kind.RIFT, MacroEntry.Kind.CANYON
			};
			MacroEntry.Kind kind = kinds[(int) Math.floorMod(seed >> 3, kinds.length)];
			int y = skyY(kind, seed);
			BlockPos origin = new BlockPos(cp.getStartX() + 8, y, cp.getStartZ() + 8);
			world.getServer().execute(() -> {
				Random random = Random.create(seed);
				MegaStructureGenerator.generate(world, origin, kind, random);
			});
		}

		// Mega fauna anchors — stock rarity
		if (Math.floorMod(seed ^ 0x9E3779B97F4A7C15L, 40L) == 0L) {
			int roll = (int) Math.floorMod(seed >> 7, 3L);
			int y = WorldRules.SKY_PRACTICAL_MIN + 20 + (int) Math.floorMod(seed, 40L);
			BlockPos at = new BlockPos(cp.getStartX() + 8, y, cp.getStartZ() + 8);
			world.getServer().execute(() -> spawnMega(world, at, roll));
		}
	}

	private static int skyY(MacroEntry.Kind kind, long seed) {
		return switch (kind) {
			case RIFT, CANYON -> WorldRules.INDUSTRIAL_Y_MIN + 30 + (int) Math.floorMod(seed, 20L);
			case ARCH, RING, FLOATING_CONTINENT, SPIRAL_RANGE, INVERTED_ISLAND ->
					WorldRules.SKY_PRACTICAL_MIN + (int) Math.floorMod(seed, 60L);
			default -> WorldRules.SKY_PRACTICAL_MIN + 40;
		};
	}

	private static void spawnMega(ServerWorld world, BlockPos at, int roll) {
		switch (roll) {
			case 0 -> {
				MegaWormEntity worm = ModEntities.MEGA_WORM.create(world);
				if (worm != null) {
					worm.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
					world.spawnEntity(worm);
				}
			}
			case 1 -> {
				DroneSwarmEntity swarm = ModEntities.DRONE_SWARM.create(world);
				if (swarm != null) {
					swarm.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
					world.spawnEntity(swarm);
				}
			}
			default -> {
				ReactorKeeperEntity keeper = ModEntities.REACTOR_KEEPER.create(world);
				if (keeper != null) {
					int ky = WorldRules.INDUSTRIAL_Y_MIN + 28;
					keeper.refreshPositionAndAngles(at.getX() + 0.5, ky, at.getZ() + 0.5, 0, 0);
					world.spawnEntity(keeper);
				}
			}
		}
	}

	/** Force-generate a named mega-structure at a position (commands). */
	public static MacroEntry forceGenerate(ServerWorld world, BlockPos pos, MacroEntry.Kind kind) {
		return MegaStructureGenerator.generate(world, pos, kind, world.getRandom());
	}
}

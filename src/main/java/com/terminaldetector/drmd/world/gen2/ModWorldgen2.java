package com.terminaldetector.drmd.world.gen2;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.mega.DroneSwarmEntity;
import com.terminaldetector.drmd.world.mega.MegaWormEntity;
import com.terminaldetector.drmd.world.mega.ReactorKeeperEntity;
import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

/**
 * World Generation 2.0 — celestial / rift megastructures, lunar base, UFOs, mega fauna.
 */
public final class ModWorldgen2 {
	/** Held off until the spawn seed finishes — see {@link com.terminaldetector.drmd.world.gen.ModWorldgen}. */
	private static volatile boolean worldgenLive = false;

	private ModWorldgen2() {}

	public static void enableLiveGeneration() {
		worldgenLive = true;
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(ModWorldgen2::onChunkLoad);
		DescentMod.LOGGER.info("Registered World Generation 2.0 (mega-structures + lunar/UFO + fauna)");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!worldgenLive) return;
		if (world.getRegistryKey() != ServerWorld.OVERWORLD) return;
		ChunkPos cp = chunk.getPos();
		long seed = world.getSeed() ^ (((long) cp.x) * 341873128712L) ^ (((long) cp.z) * 132897987541L);

		// ~1 mega-structure per 18 chunks
		if (Math.floorMod(seed, 18L) == 0L) {
			MacroEntry.Kind[] kinds = {
					MacroEntry.Kind.ARCH, MacroEntry.Kind.RING, MacroEntry.Kind.FLOATING_CONTINENT,
					MacroEntry.Kind.SPIRAL_RANGE, MacroEntry.Kind.INVERTED_ISLAND,
					MacroEntry.Kind.RIFT, MacroEntry.Kind.CANYON,
					MacroEntry.Kind.LUNAR_BASE, MacroEntry.Kind.CRASHED_UFO
			};
			MacroEntry.Kind kind = kinds[(int) Math.floorMod(seed >> 3, kinds.length)];
			int y = skyY(kind, seed, world, cp);
			BlockPos origin = new BlockPos(cp.getStartX() + 8, y, cp.getStartZ() + 8);
			// Probe the loading chunk directly; the deferred check below can safely go through the
			// world, because by then the chunk is fully loaded.
			if (!chunk.getBlockState(origin).isOf(net.minecraft.block.Blocks.LODESTONE)) {
				world.getServer().execute(() -> {
					if (world.getBlockState(origin).isOf(net.minecraft.block.Blocks.LODESTONE)) return;
					Random random = Random.create(seed);
					MegaStructureGenerator.generate(world, origin, kind, random);
				});
			}
		}

		// Mega fauna + sky UFO anchors
		if (Math.floorMod(seed ^ 0x9E3779B97F4A7C15L, 40L) == 0L) {
			int roll = (int) Math.floorMod(seed >> 7, 4L);
			int y = WorldRules.SKY_PRACTICAL_MIN + 20 + (int) Math.floorMod(seed, 40L);
			BlockPos at = new BlockPos(cp.getStartX() + 8, y, cp.getStartZ() + 8);
			world.getServer().execute(() -> spawnMega(world, at, roll));
		}
	}

	private static int skyY(MacroEntry.Kind kind, long seed, ServerWorld world, ChunkPos cp) {
		return switch (kind) {
			case RIFT, CANYON -> WorldRules.INDUSTRIAL_Y_MIN + 30 + (int) Math.floorMod(seed, 20L);
			case ARCH, RING, FLOATING_CONTINENT, SPIRAL_RANGE, INVERTED_ISLAND, LUNAR_BASE ->
					WorldRules.SKY_PRACTICAL_MIN + (int) Math.floorMod(seed, 60L);
			case CRASHED_UFO -> {
				int x = cp.getStartX() + 8;
				int z = cp.getStartZ() + 8;
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				yield MathHelperClamp(top, 55, 110);
			}
			default -> WorldRules.SKY_PRACTICAL_MIN + 40;
		};
	}

	private static int MathHelperClamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
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
			case 2 -> {
				SkyUfoEntity ufo = ModEntities.SKY_UFO.create(world);
				if (ufo != null) {
					ufo.refreshPositionAndAngles(at.getX() + 0.5, at.getY() + 12, at.getZ() + 0.5, 0, 0);
					world.spawnEntity(ufo);
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
		if (kind == MacroEntry.Kind.UFO) {
			SkyUfoEntity ufo = ModEntities.SKY_UFO.create(world);
			if (ufo != null) {
				ufo.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
				world.spawnEntity(ufo);
				return new MacroEntry(ufo.getUuid(), MacroEntry.Kind.UFO, WorldRules.Layer.SKY_ARCHIPELAGO,
						pos.toImmutable(), 36, 10, 36, 0x55FFAA, "Sky UFO");
			}
		}
		return MegaStructureGenerator.generate(world, pos, kind, world.getRandom());
	}
}

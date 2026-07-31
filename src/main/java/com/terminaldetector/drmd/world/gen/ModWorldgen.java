package com.terminaldetector.drmd.world.gen;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

import java.util.UUID;

/**
 * Industrial Underground — stock worldgen (~1 complex / 12 chunks) + garrison drones.
 */
public final class ModWorldgen {
	public static final Feature<DefaultFeatureConfig> INDUSTRIAL =
			new IndustrialUndergroundFeature(DefaultFeatureConfig.CODEC);

	private static final AiRole[] GARRISON = {
			AiRole.ASSAULT, AiRole.INTERCEPTOR, AiRole.MG, AiRole.LASER, AiRole.RPG,
			AiRole.ARTILLERY, AiRole.SUPPORT, AiRole.HEAVY, AiRole.SEEKER, AiRole.HEAVY_ELITE
	};

	/**
	 * False until the spawn seed has finished.
	 *
	 * <p>"Preparing spawn" loads hundreds of chunks back to back on the server thread. Generating a
	 * complex from inside that is what pushed the tick past the 60 s watchdog and read as a crash on
	 * join, so nothing is built until the world is up and the seed is done.
	 */
	private static volatile boolean worldgenLive = false;

	private ModWorldgen() {}

	public static void enableLiveGeneration() {
		worldgenLive = true;
	}

	public static void register() {
		Registry.register(Registries.FEATURE, Identifier.of(DescentMod.MOD_ID, "industrial_underground"), INDUSTRIAL);
		ServerChunkEvents.CHUNK_LOAD.register(ModWorldgen::onChunkLoad);
		DescentMod.LOGGER.info("Registered Industrial Underground worldgen (stock density)");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!worldgenLive) return;
		if (world.getRegistryKey() != ServerWorld.OVERWORLD) return;
		ChunkPos cp = chunk.getPos();
		long seed = world.getSeed() ^ (((long) cp.x) << 32) ^ cp.z;
		// Stock density: ~1 complex per 12 chunks
		if (Math.floorMod(seed * 31L, 12L) != 0L) return;
		int y = WorldRules.INDUSTRIAL_Y_MIN + 24 + (int) Math.floorMod(seed, 16L);
		BlockPos center = new BlockPos(cp.getStartX() + 8, y, cp.getStartZ() + 8);
		// Read from the chunk that is being loaded, not through the world. The chunk is not in the
		// full-status map yet, so world.getBlockState would force-load it and re-enter CHUNK_LOAD
		// while a generator is still writing.
		var local = chunk.getBlockState(center);
		if (local.isOf(net.minecraft.block.Blocks.BEACON)) return;
		if (!local.isAir() && local.isSolidBlock(world, center)) {
			WorldRules.ComplexStyle[] styles = WorldRules.ComplexStyle.values();
			WorldRules.ComplexStyle style = styles[(int) Math.floorMod(seed, styles.length)];
			world.getServer().execute(() -> forceGenerate(world, center, style));
		}
	}

	public static void forceGenerate(ServerWorld world, BlockPos pos, WorldRules.ComplexStyle style) {
		IndustrialComplexGenerator.generateAt(world, pos, style, world.getRandom());
		MacroWorld.put(new MacroEntry(UUID.randomUUID(), MacroEntry.Kind.INDUSTRIAL_COMPLEX,
				WorldRules.Layer.DEPTH_REACTORS, pos.toImmutable(), 56, 48, 56, 0x6688AA, "Industrial " + style));
		spawnGarrison(world, pos);
	}

	private static void spawnGarrison(ServerWorld world, BlockPos center) {
		int n = 3 + world.getRandom().nextInt(3);
		for (int i = 0; i < n; i++) {
			DroneEntity drone = ModEntities.DRONE.create(world);
			if (drone == null) continue;
			AiRole role = GARRISON[world.getRandom().nextInt(GARRISON.length)];
			double ang = world.getRandom().nextDouble() * Math.PI * 2;
			drone.refreshPositionAndAngles(
					center.getX() + Math.cos(ang) * 10,
					center.getY() + (world.getRandom().nextDouble() - 0.5) * 8,
					center.getZ() + Math.sin(ang) * 10,
					0, 0);
			drone.applyRole(role);
			world.spawnEntity(drone);
		}
	}
}

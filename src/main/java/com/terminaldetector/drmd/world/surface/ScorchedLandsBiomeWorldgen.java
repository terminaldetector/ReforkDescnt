package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.base.DescentSession;
import com.terminaldetector.drmd.world.dungeon.DungeonTerritory;
import com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator;
import com.terminaldetector.drmd.world.gen2.ScorchedSurfaceGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** Scorched lands plates — bombed town core + ruined villages + burned tree groves. */
public final class ScorchedLandsBiomeWorldgen {
	private ScorchedLandsBiomeWorldgen() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerWorld ow = server.getOverworld();
			if (ow == null) return;
			BlockPos spawn = ow.getSpawnPos();
			ScorchedLandsRegions.bind(ow.getSeed(), spawn.getX(), spawn.getZ());
			ow.getRegistryManager().getOptional(RegistryKeys.BIOME).ifPresent(reg ->
					reg.getEntry(ScorchedLandsRegions.PLATE.biomeKey())
							.ifPresent(ScorchedLandsRegions::setBiomeEntry));
			DescentMod.LOGGER.info("Scorched lands regions — {}",
					ScorchedLandsRegions.describeNearest(spawn.getX(), spawn.getZ()));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> ScorchedLandsRegions.clear());
		ServerChunkEvents.CHUNK_LOAD.register(ScorchedLandsBiomeWorldgen::onChunkLoad);
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!WorldFeatures.SURFACE_DISTRICTS) return;
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (!ScorchedLandsRegions.isBound()) return;
		DescentWorldState state = DescentWorldState.get(world);
		if (state.isPsychedelic()) return;

		ChunkPos cp = chunk.getPos();
		BlockPos anchor = ScorchedLandsRegions.anchorAt(cp.getCenterX(), cp.getCenterZ());
		if (anchor == null) return;
		if (state.isScorchedSeeded(anchor.getX(), anchor.getZ())) return;
		state.markScorchedSeeded(anchor.getX(), anchor.getZ());
		enqueuePlate(world, anchor);
		DescentMod.LOGGER.info("Scorched lands plate queued @ {} {}", anchor.getX(), anchor.getZ());
	}

	public static void enqueuePlate(ServerWorld world, BlockPos anchor) {
		int ax = anchor.getX();
		int az = anchor.getZ();
		long seed = world.getSeed() ^ 0x5C04C4EDL ^ ax * 17L ^ az;

		DescentSession.enqueueLandmark(new BlockPos(ax, 0, az), () ->
				ScorchedSurfaceGenerator.generateTown(world, new BlockPos(ax, 0, az), Random.create(seed)));

		for (int i = 0; i < 4; i++) {
			double ang = i * (Math.PI / 2) + 0.5;
			int dist = 55 + i * 12;
			int gx = ax + (int) (Math.cos(ang) * dist);
			int gz = az + (int) (Math.sin(ang) * dist);
			long salt = seed ^ (i * 97L);
			DescentSession.enqueueLandmark(new BlockPos(gx, 0, gz), () ->
					ScorchedSurfaceGenerator.generateGrove(world, new BlockPos(gx, 0, gz), Random.create(salt)));
		}

		WorldRules.ComplexStyle under = DungeonTerritory.primaryStyle(DungeonTerritory.Kind.SCORCHED_LANDS, seed);
		var vit = DungeonTerritory.vitality(DungeonTerritory.Kind.SCORCHED_LANDS, seed);
		BlockPos dungeon = new BlockPos(ax, WorldRules.INDUSTRIAL_Y_MIN + 26, az);
		DescentSession.enqueueLandmark(dungeon, () -> IndustrialComplexGenerator.generateAt(
				world, dungeon, under, vit, Random.create(seed ^ 0x5C04L)));
	}
}

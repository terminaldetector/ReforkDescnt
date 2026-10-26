package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.base.DescentSession;
import com.terminaldetector.drmd.world.dungeon.DungeonTerritory;
import com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator;
import com.terminaldetector.drmd.world.gen2.IronGuildSurfaceGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** Iron guild plates — Strafe techno-medieval halls + caste enclaves. */
public final class IronGuildBiomeWorldgen {
	private IronGuildBiomeWorldgen() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerWorld ow = server.getOverworld();
			if (ow == null) return;
			BlockPos spawn = ow.getSpawnPos();
			IronGuildRegions.bind(ow.getSeed(), spawn.getX(), spawn.getZ());
			ow.getRegistryManager().getOptional(RegistryKeys.BIOME).ifPresent(reg ->
					reg.getEntry(IronGuildRegions.PLATE.biomeKey())
							.ifPresent(IronGuildRegions::setBiomeEntry));
			DescentMod.LOGGER.info("Iron guild regions — {}",
					IronGuildRegions.describeNearest(spawn.getX(), spawn.getZ()));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> IronGuildRegions.clear());
		ServerChunkEvents.CHUNK_LOAD.register(IronGuildBiomeWorldgen::onChunkLoad);
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!WorldFeatures.SURFACE_DISTRICTS) return;
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (!IronGuildRegions.isBound()) return;
		DescentWorldState state = DescentWorldState.get(world);
		if (state.isPsychedelic()) return;

		ChunkPos cp = chunk.getPos();
		BlockPos anchor = IronGuildRegions.anchorAt(cp.getCenterX(), cp.getCenterZ());
		if (anchor == null) return;
		if (state.isIronGuildSeeded(anchor.getX(), anchor.getZ())) return;
		state.markIronGuildSeeded(anchor.getX(), anchor.getZ());
		enqueuePlate(world, anchor);
		DescentMod.LOGGER.info("Iron guild plate queued @ {} {}", anchor.getX(), anchor.getZ());
	}

	public static void enqueuePlate(ServerWorld world, BlockPos anchor) {
		int ax = anchor.getX();
		int az = anchor.getZ();
		long seed = world.getSeed() ^ 0x1840A71DL ^ ax * 19L ^ az;

		DescentSession.enqueueLandmark(new BlockPos(ax, 0, az), () ->
				IronGuildSurfaceGenerator.generateHall(world, new BlockPos(ax, 0, az), Random.create(seed)));

		WorldRules.ComplexStyle under = DungeonTerritory.primaryStyle(DungeonTerritory.Kind.IRON_GUILD, seed);
		var vit = DungeonTerritory.vitality(DungeonTerritory.Kind.IRON_GUILD, seed);
		BlockPos dungeon = new BlockPos(ax, WorldRules.INDUSTRIAL_Y_MIN + 22, az);
		DescentSession.enqueueLandmark(dungeon, () -> IndustrialComplexGenerator.generateAt(
				world, dungeon, under, vit, Random.create(seed ^ 0x1840L)));
	}
}

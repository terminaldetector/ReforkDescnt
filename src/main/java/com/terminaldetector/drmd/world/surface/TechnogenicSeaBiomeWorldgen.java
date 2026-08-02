package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.base.DescentSession;
import com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator;
import com.terminaldetector.drmd.world.gen2.MegaLocatorGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Technogenic sea plates: Mega Locator (LLOD) + small locator network + underwater 6DoF dungeon.
 */
public final class TechnogenicSeaBiomeWorldgen {
	private TechnogenicSeaBiomeWorldgen() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerWorld ow = server.getOverworld();
			if (ow == null) return;
			BlockPos spawn = ow.getSpawnPos();
			TechnogenicSeaRegions.bind(ow.getSeed(), spawn.getX(), spawn.getZ());
			ow.getRegistryManager().getOptional(RegistryKeys.BIOME).ifPresent(reg ->
					reg.getEntry(TechnogenicSeaRegions.PLATE.biomeKey())
							.ifPresent(TechnogenicSeaRegions::setBiomeEntry));
			DescentMod.LOGGER.info("Technogenic sea regions — {}",
					TechnogenicSeaRegions.describeNearest(spawn.getX(), spawn.getZ()));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> TechnogenicSeaRegions.clear());
		ServerChunkEvents.CHUNK_LOAD.register(TechnogenicSeaBiomeWorldgen::onChunkLoad);
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!WorldFeatures.SURFACE_DISTRICTS) return;
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (!TechnogenicSeaRegions.isBound()) return;
		DescentWorldState state = DescentWorldState.get(world);
		if (state.isPsychedelic()) return;

		ChunkPos cp = chunk.getPos();
		BlockPos anchor = TechnogenicSeaRegions.anchorAt(cp.getCenterX(), cp.getCenterZ());
		if (anchor == null) return;
		if (state.isTechnogenicSeeded(anchor.getX(), anchor.getZ())) return;
		state.markTechnogenicSeeded(anchor.getX(), anchor.getZ());
		enqueuePlate(world, anchor);
		DescentMod.LOGGER.info("Technogenic sea plate queued @ {} {}", anchor.getX(), anchor.getZ());
	}

	public static void enqueuePlate(ServerWorld world, BlockPos anchor) {
		int ax = anchor.getX();
		int az = anchor.getZ();
		long seed = world.getSeed() ^ ((long) ax * 31L) ^ az;

		// Mega Locator first — MacroWorld.put gives horizon LLOD before the player arrives.
		DescentSession.enqueueLandmark(new BlockPos(ax, 0, az), () ->
				MegaLocatorGenerator.generateMega(world, new BlockPos(ax, 0, az), Random.create(seed)));

		// Smaller locator network around the mega
		for (int i = 0; i < 5; i++) {
			double ang = i * (Math.PI * 2 / 5) + 0.3;
			int dist = 70 + i * 18;
			int lx = ax + (int) (Math.cos(ang) * dist);
			int lz = az + (int) (Math.sin(ang) * dist);
			long salt = seed ^ (i * 0x9E3779B97F4A7C15L);
			DescentSession.enqueueLandmark(new BlockPos(lx, 0, lz), () ->
					MegaLocatorGenerator.generateSmall(world, new BlockPos(lx, 0, lz), Random.create(salt)));
		}

		// Underwater 6DoF dungeon under the mega
		BlockPos under = new BlockPos(ax, Math.min(WorldRules.INDUSTRIAL_Y_MAX - 8, 40), az);
		DescentSession.enqueueLandmark(under, () -> IndustrialComplexGenerator.generateAt(
				world, under, WorldRules.ComplexStyle.SUBSEA_LOCATOR, world.getRandom()));
	}
}

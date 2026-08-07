package com.terminaldetector.drmd.world.surface;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.base.DescentSession;
import com.terminaldetector.drmd.world.dungeon.DungeonTerritory;
import com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MegaStructureGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Discovers megacity biome plates on chunk load and distance-queues the city complex.
 * Spawn no longer seeds a megacity — plates exist only as {@code drmd:megacity} regions.
 */
public final class MegacityBiomeWorldgen {
	private MegacityBiomeWorldgen() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerWorld ow = server.getOverworld();
			if (ow == null) return;
			BlockPos spawn = ow.getSpawnPos();
			MegacityRegions.bind(ow.getSeed(), spawn.getX(), spawn.getZ());
			ow.getRegistryManager().getOptional(RegistryKeys.BIOME).ifPresent(reg -> {
				reg.getEntry(MegacityRegions.BIOME_KEY).ifPresent(MegacityRegions::setBiomeEntry);
			});
			DescentMod.LOGGER.info("Megacity biome regions bound — {}",
					MegacityRegions.describeNearest(spawn.getX(), spawn.getZ()));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MegacityRegions.clear());
		ServerChunkEvents.CHUNK_LOAD.register(MegacityBiomeWorldgen::onChunkLoad);
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (!WorldFeatures.SURFACE_DISTRICTS) return;
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (!MegacityRegions.isBound()) return;

		DescentWorldState state = DescentWorldState.get(world);
		if (state.isPsychedelic() || state.isInfiniteMegacity()) return;

		ChunkPos cp = chunk.getPos();
		int cx = cp.getCenterX();
		int cz = cp.getCenterZ();
		BlockPos anchor = MegacityRegions.anchorAt(cx, cz);
		if (anchor == null) return;
		if (state.isMegacitySeeded(anchor.getX(), anchor.getZ())) return;

		state.markMegacitySeeded(anchor.getX(), anchor.getZ());
		enqueuePlate(world, anchor);
		DescentMod.LOGGER.info("Megacity biome plate queued at {} {}", anchor.getX(), anchor.getZ());
	}

	/** Distance-queue the city dungeon + under/orbit satellites for one plate anchor. */
	public static void enqueuePlate(ServerWorld world, BlockPos anchorXZ) {
		int cityX = anchorXZ.getX();
		int cityZ = anchorXZ.getZ();
		BlockPos cityNear = new BlockPos(cityX, 0, cityZ);

		DescentSession.enqueueLandmark(cityNear, () -> {
			int citySurface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cityX, cityZ);
			BlockPos cityAt = new BlockPos(cityX, Math.max(citySurface, WorldRules.INDUSTRIAL_Y_MAX + 24), cityZ);
			MegaStructureGenerator.generate(world, cityAt,
					MacroEntry.Kind.MEGACITY, Random.create(world.getSeed() ^ 0xC1740001L ^ cityX * 31L ^ cityZ));
		});

		long plateSalt = world.getSeed() ^ 0xC1740001L ^ cityX * 31L ^ cityZ;
		WorldRules.ComplexStyle primary = DungeonTerritory.primaryStyle(DungeonTerritory.Kind.MEGACITY, plateSalt);
		WorldRules.ComplexStyle secondary = DungeonTerritory.satelliteStyle(DungeonTerritory.Kind.MEGACITY, plateSalt);
		var vitPrimary = DungeonTerritory.vitality(DungeonTerritory.Kind.MEGACITY, plateSalt);
		var vitSat = DungeonTerritory.vitality(DungeonTerritory.Kind.MEGACITY, plateSalt ^ 0xBEEFL);

		BlockPos cityDungeon = new BlockPos(cityX, WorldRules.INDUSTRIAL_Y_MIN + 24, cityZ);
		DescentSession.enqueueLandmark(cityDungeon, () -> IndustrialComplexGenerator.generateAt(
				world, cityDungeon, primary, vitPrimary, Random.create(plateSalt)));

		BlockPos satDungeon = new BlockPos(cityX - 55, WorldRules.INDUSTRIAL_Y_MIN + 32, cityZ + 48);
		DescentSession.enqueueLandmark(satDungeon, () -> IndustrialComplexGenerator.generateAt(
				world, satDungeon, secondary, vitSat, Random.create(plateSalt ^ 0xBEEFL)));

		DescentSession.enqueueLandmark(new BlockPos(cityX + 40, WorldRules.INDUSTRIAL_Y_MIN + 36, cityZ - 30),
				() -> MegaStructureGenerator.generate(world,
						new BlockPos(cityX + 40, WorldRules.INDUSTRIAL_Y_MIN + 36, cityZ - 30),
						MacroEntry.Kind.RIFT, Random.create(world.getSeed() ^ 0xD00D0001L)));

		DescentSession.enqueueLandmark(new BlockPos(cityX - 40, WorldRules.SKY_PRACTICAL_MIN + 60, cityZ + 40),
				() -> MegaStructureGenerator.generate(world,
						new BlockPos(cityX - 40, WorldRules.SKY_PRACTICAL_MIN + 60, cityZ + 40),
						MacroEntry.Kind.RING, Random.create(world.getSeed() ^ 0x0B81C171L)));

		DescentSession.enqueueLandmark(new BlockPos(cityX + 70, WorldRules.SKY_PRACTICAL_MIN + 40, cityZ - 50),
				() -> MegaStructureGenerator.generate(world,
						new BlockPos(cityX + 70, WorldRules.SKY_PRACTICAL_MIN + 40, cityZ - 50),
						MacroEntry.Kind.ARCH, Random.create(world.getSeed() ^ 0xA12C0001L)));
	}
}

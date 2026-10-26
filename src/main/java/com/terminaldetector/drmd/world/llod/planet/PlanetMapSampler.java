package com.terminaldetector.drmd.world.llod.planet;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.World;

/**
 * Samples loaded Overworld chunks into {@link PlanetMapState} as players explore.
 * Weather flags follow the live biome precipitation / thunder state.
 */
public final class PlanetMapSampler {
	private PlanetMapSampler() {}

	public static void tickPlayer(ServerPlayerEntity player) {
		if (player.getWorld().getRegistryKey() != World.OVERWORLD) return;
		ServerWorld world = player.getServerWorld();
		PlanetMapState map = PlanetMapState.get(world);

		int pcx = PlanetCell.cellOf(player.getBlockX());
		int pcz = PlanetCell.cellOf(player.getBlockZ());
		// Sample a ring around the pilot — cheap, only loaded areas.
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				sampleCell(world, map, pcx + dx, pcz + dz);
			}
		}
	}

	public static void sampleCell(ServerWorld world, PlanetMapState map, int cx, int cz) {
		int x = PlanetCell.blockCenter(cx);
		int z = PlanetCell.blockCenter(cz);
		if (!world.isChunkLoaded(x >> 4, z >> 4)) return;

		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		RegistryEntry<Biome> biome = world.getBiome(new BlockPos(x, y, z));
		int tint = biomeTint(biome);
		int weather = 0;
		Biome b = biome.value();
		BlockPos at = new BlockPos(x, y, z);
		if (world.isRaining() && b.hasPrecipitation()) {
			weather |= PlanetCell.F_RAIN;
			if (world.isThundering()) weather |= PlanetCell.F_STORM;
		}
		if (b.getPrecipitation(at) == Biome.Precipitation.NONE) {
			weather &= ~PlanetCell.F_RAIN;
		}
		map.explore(cx, cz, y, tint, weather);
	}

	private static int biomeTint(RegistryEntry<Biome> entry) {
		// Lightweight palette by biome path — no client colormap dependency on server.
		String path = entry.getKey().map(k -> k.getValue().getPath()).orElse("plains");
		if (path.contains("ocean") || path.contains("river")) return 0x1E4D7B;
		if (path.contains("desert") || path.contains("badlands")) return 0xC2A35A;
		if (path.contains("snow") || path.contains("ice") || path.contains("frozen")) return 0xD8E6F0;
		if (path.contains("forest") || path.contains("taiga")) return 0x2F6B34;
		if (path.contains("jungle")) return 0x1F8A3A;
		if (path.contains("swamp")) return 0x4A5C2E;
		if (path.contains("mushroom")) return 0x7A4A7A;
		if (path.contains("nether") || path.contains("basalt")) return 0x5A2020;
		if (path.contains("end")) return 0xC9B3D9;
		return 0x5A8C4A;
	}
}

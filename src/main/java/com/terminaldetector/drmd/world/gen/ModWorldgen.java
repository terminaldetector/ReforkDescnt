package com.terminaldetector.drmd.world.gen;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.world.WorldRules;
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

/**
 * Registers industrial feature + chunk-load generation fallback.
 * Chunk fallback guarantees complexes appear even without datapack placed-features.
 */
public final class ModWorldgen {
	public static final Feature<DefaultFeatureConfig> INDUSTRIAL =
			new IndustrialUndergroundFeature(DefaultFeatureConfig.CODEC);

	private ModWorldgen() {}

	public static void register() {
		Registry.register(Registries.FEATURE, Identifier.of(DescentMod.MOD_ID, "industrial_underground"), INDUSTRIAL);

		ServerChunkEvents.CHUNK_LOAD.register(ModWorldgen::onChunkLoad);
		DescentMod.LOGGER.info("Registered Industrial Underground worldgen (chunk + feature)");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != ServerWorld.OVERWORLD) return;
		ChunkPos cp = chunk.getPos();
		long seed = world.getSeed() ^ (((long) cp.x) << 32) ^ cp.z;
		// ~1 complex per 28 chunks
		if (Math.floorMod(seed * 31L, 28L) != 0L) return;
		// Only generate once: marker in chunk NBT would be better; use block probe
		int y = WorldRules.INDUSTRIAL_Y_MIN + 24 + (int) Math.floorMod(seed, 16L);
		BlockPos center = new BlockPos(cp.getStartX() + 8, y, cp.getStartZ() + 8);
		// Skip if already carved (sea lantern shell from prior gen)
		if (world.getBlockState(center).isOf(net.minecraft.block.Blocks.BEACON)) return;
		if (!world.getBlockState(center).isAir() && world.getBlockState(center).isSolidBlock(world, center)) {
			WorldRules.ComplexStyle[] styles = WorldRules.ComplexStyle.values();
			WorldRules.ComplexStyle style = styles[(int) Math.floorMod(seed, styles.length)];
			world.getServer().execute(() ->
					IndustrialComplexGenerator.generateAt(world, center, style, world.getRandom()));
		}
	}

	/** Force-generate at a position (command / creative). */
	public static void forceGenerate(ServerWorld world, BlockPos pos, WorldRules.ComplexStyle style) {
		IndustrialComplexGenerator.generateAt(world, pos, style, world.getRandom());
	}
}

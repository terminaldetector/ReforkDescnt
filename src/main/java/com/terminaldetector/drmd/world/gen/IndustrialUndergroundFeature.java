package com.terminaldetector.drmd.world.gen;

import com.mojang.serialization.Codec;
import com.terminaldetector.drmd.world.WorldRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Chunk feature: rare Industrial Underground complexes.
 * Volume-first cavities + artificial modules — no privileged floor.
 */
public class IndustrialUndergroundFeature extends Feature<DefaultFeatureConfig> {
	public IndustrialUndergroundFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		ChunkPos chunk = new ChunkPos(origin);

		if ((Math.floorMod(chunk.x * 734287 + chunk.z * 912391, 24)) != 0) return false;

		int y = WorldRules.INDUSTRIAL_Y_MIN + 20 + world.getRandom().nextInt(24);
		BlockPos center = new BlockPos(chunk.getStartX() + 8, y, chunk.getStartZ() + 8);

		WorldRules.ComplexStyle[] styles = WorldRules.ComplexStyle.values();
		WorldRules.ComplexStyle style = styles[world.getRandom().nextInt(styles.length)];

		IndustrialComplexGenerator.generateAt(world, center, style, world.getRandom());
		return true;
	}
}

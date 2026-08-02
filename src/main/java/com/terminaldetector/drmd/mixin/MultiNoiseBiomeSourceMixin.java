package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.surface.MegacityRegions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects {@code drmd:megacity} plates into Overworld multi-noise sampling.
 * Coordinates are quart-space (block / 4), same as vanilla biome source.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class MultiNoiseBiomeSourceMixin {
	@Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
	private void drmd$megacityBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise,
									CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		if (!WorldFeatures.SURFACE_DISTRICTS) return;
		if (!MegacityRegions.isBound()) return;
		RegistryEntry<Biome> entry = MegacityRegions.biomeEntry();
		if (entry == null) return;
		int bx = x << 2;
		int bz = z << 2;
		if (!MegacityRegions.isInBiome(bx, bz)) return;
		cir.setReturnValue(entry);
	}
}

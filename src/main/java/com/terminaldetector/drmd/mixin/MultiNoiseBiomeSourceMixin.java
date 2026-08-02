package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.WorldFeatures;
import com.terminaldetector.drmd.world.surface.MegacityRegions;
import com.terminaldetector.drmd.world.surface.ScorchedLandsRegions;
import com.terminaldetector.drmd.world.surface.TechnogenicSeaRegions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects DRMD plate biomes into Overworld multi-noise sampling.
 * Quart-space coords (block / 4). Order: megacity → technogenic sea → scorched lands.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class MultiNoiseBiomeSourceMixin {
	@Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
	private void drmd$plateBiomes(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise,
								  CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		if (!WorldFeatures.SURFACE_DISTRICTS) return;
		int bx = x << 2;
		int bz = z << 2;

		if (MegacityRegions.isBound() && MegacityRegions.isInBiome(bx, bz)) {
			RegistryEntry<Biome> e = MegacityRegions.biomeEntry();
			if (e != null) {
				cir.setReturnValue(e);
				return;
			}
		}
		if (TechnogenicSeaRegions.isBound() && TechnogenicSeaRegions.isInBiome(bx, bz)) {
			RegistryEntry<Biome> e = TechnogenicSeaRegions.biomeEntry();
			if (e != null) {
				cir.setReturnValue(e);
				return;
			}
		}
		if (ScorchedLandsRegions.isBound() && ScorchedLandsRegions.isInBiome(bx, bz)) {
			RegistryEntry<Biome> e = ScorchedLandsRegions.biomeEntry();
			if (e != null) {
				cir.setReturnValue(e);
			}
		}
	}
}

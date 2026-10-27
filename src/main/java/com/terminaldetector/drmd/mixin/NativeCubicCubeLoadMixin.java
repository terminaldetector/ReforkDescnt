package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.compat.NativeCubicSkylight;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional public applyLoaded callback; no upstream code or implementation fields are copied. */
@Pseudo
@Mixin(targets = "dev.fallingcloud.bettercubicchunks.server.CubeApplier", remap = false)
public abstract class NativeCubicCubeLoadMixin {
    @Inject(method = "applyLoaded", at = @At("RETURN"), remap = false)
    private void drmd$nativeCubeArrived(Chunk chunk, int cubeY, @Coerce Object data, CallbackInfo ci) {
        if (chunk instanceof WorldChunk full) NativeCubicSkylight.loaded(full);
    }
}

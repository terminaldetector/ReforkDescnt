package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeBenchmarkRecorder;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One completed outer client frame; no world-render or recursive portal/mirror hooks. */
@Mixin(MinecraftClient.class)
public class BenchmarkFrameProbe {
    @Inject(method="render(Z)V",at=@At("RETURN"))
    private void benchmark(boolean tick,CallbackInfo ci){
        if(NativeBenchmarkRecorder.ENABLED)NativeBenchmarkRecorder.frameFinished((MinecraftClient)(Object)this,System.nanoTime());
    }
}

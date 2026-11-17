package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSkyLayersCheck;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BufferRenderer.class)
public class SkyBufferDrawProbe {
    @Inject(method="drawWithGlobalProgram",at=@At("HEAD"))
    private static void before(BuiltBuffer buffer,CallbackInfo ci){if(NativeSkyLayersCheck.orbitalDrawing())NativeSkyLayersCheck.envelope(true);}
    @Inject(method="drawWithGlobalProgram",at=@At("RETURN"))
    private static void after(BuiltBuffer buffer,CallbackInfo ci){if(NativeSkyLayersCheck.orbitalDrawing())NativeSkyLayersCheck.envelope(false);}
}

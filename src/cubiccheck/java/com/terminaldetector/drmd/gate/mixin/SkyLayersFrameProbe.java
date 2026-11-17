package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSkyLayersCheck;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class SkyLayersFrameProbe {
    @Inject(method="render",at=@At("HEAD"))
    private void before(RenderTickCounter counter,boolean tick,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.beforeFrame();}
    @Inject(method="render",at=@At("RETURN"))
    private void after(RenderTickCounter counter,boolean tick,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.rendered();}
}

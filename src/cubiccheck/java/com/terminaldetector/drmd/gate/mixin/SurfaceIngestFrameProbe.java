package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSurfaceIngestCheck;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class SurfaceIngestFrameProbe {
    @Inject(method="render",at=@At("RETURN"))
    private void rendered(RenderTickCounter counter,boolean tick,CallbackInfo ci){if(NativeSurfaceIngestCheck.ENABLED)NativeSurfaceIngestCheck.rendered();}
}

package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSkyLayersCheck;
import com.terminaldetector.drmd.client.render.CockpitRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CockpitRenderer.class,remap=false)
public class SkyCockpitDrawProbe {
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private static void before(WorldRenderContext context,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.cockpit(true);}
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private static void after(WorldRenderContext context,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.cockpit(false);}
}

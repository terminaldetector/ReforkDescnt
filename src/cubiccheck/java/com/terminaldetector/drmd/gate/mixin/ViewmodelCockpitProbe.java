package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeViewmodelCheck;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.terminaldetector.drmd.client.render.CockpitRenderer",remap=false)
public class ViewmodelCockpitProbe {
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private static void before(WorldRenderContext context,CallbackInfo ci){if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.cockpit(true);}
    @Inject(method="render",at=@At("RETURN"),remap=false)
    private static void after(WorldRenderContext context,CallbackInfo ci){if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.cockpit(false);}
}

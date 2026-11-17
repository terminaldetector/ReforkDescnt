package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeViewmodelCheck;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full framebuffer/depth observations around each actual deferred callback and its buffer flush. */
@Mixin(targets="com.terminaldetector.drmd.client.presentation.ScenePresentation$Overlay",remap=false)
public class ViewmodelOverlayProbe {
    @Shadow @Final private Consumer<?> render;
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private void before(CallbackInfo ci){if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.beforeOverlay(render.getClass().getName());}
    @Inject(method="draw",at=@At("RETURN"),remap=false)
    private void after(CallbackInfo ci){if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.afterOverlay(render.getClass().getName());}
}

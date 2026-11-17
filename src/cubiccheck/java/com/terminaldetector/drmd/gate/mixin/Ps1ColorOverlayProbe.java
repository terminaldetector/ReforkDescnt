package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativePs1ColorCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe once after real world upscale, before the first intentionally native overlay. */
@Mixin(targets="com.terminaldetector.drmd.client.presentation.ScenePresentation$Overlay",remap=false)
public class Ps1ColorOverlayProbe {
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private void before(CallbackInfo ci){if(NativePs1ColorCheck.ENABLED)NativePs1ColorCheck.beforeOverlay();}
}

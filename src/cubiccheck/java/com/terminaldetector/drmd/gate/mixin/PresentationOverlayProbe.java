package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativePresentationCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the real composite before any intentionally full-resolution first-person overlay. */
@Mixin(targets="com.terminaldetector.drmd.client.presentation.ScenePresentation$Overlay",remap=false)
public abstract class PresentationOverlayProbe {
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private void before(CallbackInfo ci){if(NativePresentationCheck.ENABLED)NativePresentationCheck.beforeOverlay();}
}

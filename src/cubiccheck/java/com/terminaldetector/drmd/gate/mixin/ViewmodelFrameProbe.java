package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeViewmodelCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuse the existing test observer's frame boundaries; no game/FrameProbe callback changes. */
@Mixin(targets="com.terminaldetector.drmd.gate.NativePresentationCheck",remap=false)
public class ViewmodelFrameProbe {
    @Inject(method="beforeFrame",at=@At("HEAD"),remap=false)
    private static void before(CallbackInfo ci){if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.beforeFrame();}
    @Inject(method="rendered",at=@At("HEAD"),remap=false)
    private static void rendered(CallbackInfo ci){if(NativeViewmodelCheck.ENABLED)NativeViewmodelCheck.rendered();}
}

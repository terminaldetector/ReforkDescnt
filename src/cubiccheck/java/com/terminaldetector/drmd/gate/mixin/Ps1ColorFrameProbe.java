package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativePs1ColorCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pause only the existing disposable test's phase advancement, never the game render. */
@Mixin(targets="com.terminaldetector.drmd.gate.NativePresentationCheck",remap=false)
public class Ps1ColorFrameProbe {
    @Inject(method="beforeFrame",at=@At("TAIL"),remap=false)
    private static void before(CallbackInfo ci){if(NativePs1ColorCheck.ENABLED)NativePs1ColorCheck.beforeFrame();}
    @Inject(method="rendered",at=@At("HEAD"),cancellable=true,remap=false)
    private static void rendered(CallbackInfo ci){if(NativePs1ColorCheck.ENABLED&&NativePs1ColorCheck.rendered())ci.cancel();}
}

package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativePs1ColorCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Existing exact-geometry gun gate must select PS1 colour-on frames, not an earlier raw frame. */
@Mixin(targets="com.terminaldetector.drmd.gate.NativeViewmodelCheck",remap=false)
public class Ps1ColorViewmodelProbe {
    @Inject(method="rendered",at=@At("HEAD"),cancellable=true,remap=false)
    private static void select(CallbackInfo ci){if(NativePs1ColorCheck.ENABLED&&!NativePs1ColorCheck.shouldRecordViewmodel())ci.cancel();}
}

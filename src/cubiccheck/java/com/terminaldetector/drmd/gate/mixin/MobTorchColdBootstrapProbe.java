package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.CubicClientGate;
import com.terminaldetector.drmd.gate.NativeMobTorchReloadCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses the existing opt-in native-client bootstrap without changing its shared source. */
@Mixin(value=CubicClientGate.class,remap=false)
public abstract class MobTorchColdBootstrapProbe {
    @Inject(method="onInitializeClient",at=@At("HEAD"),cancellable=true,remap=false)
    private void drmd$coldBoot(CallbackInfo ci){
        if(NativeMobTorchReloadCheck.ENABLED){NativeMobTorchReloadCheck.register();ci.cancel();}
    }
}

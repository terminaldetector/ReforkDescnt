package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.CubicClientGate;
import com.terminaldetector.drmd.gate.NativeSurfaceArrivalCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CubicClientGate.class,remap=false)
public abstract class SurfaceArrivalBootstrapProbe {
    @Inject(method="onInitializeClient",at=@At("HEAD"),cancellable=true,remap=false)
    private void arrival(CallbackInfo ci){if(NativeSurfaceArrivalCheck.ENABLED){NativeSurfaceArrivalCheck.register();ci.cancel();}}
}

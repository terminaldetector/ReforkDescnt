package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.CubicClientGate;
import com.terminaldetector.drmd.gate.NativePortalTransitCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Exclusive opt-in native transit; the historical gun-placement observer remains unchanged. */
@Mixin(value=CubicClientGate.class,remap=false)
public class PortalTransitBootstrapProbe {
    @Inject(method="onInitializeClient",at=@At("HEAD"),cancellable=true,remap=false)
    private void boot(CallbackInfo ci){if(com.terminaldetector.drmd.gate.NativeConnectedPortalCheck.ENABLED){com.terminaldetector.drmd.gate.NativeConnectedPortalCheck.register();ci.cancel();}else if(NativePortalTransitCheck.ENABLED){NativePortalTransitCheck.register();ci.cancel();}}
}

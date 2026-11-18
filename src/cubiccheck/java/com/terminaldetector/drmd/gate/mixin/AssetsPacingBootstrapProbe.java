package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CubicClientGate.class,remap=false)
public class AssetsPacingBootstrapProbe {
    @Inject(method="onInitializeClient",at=@At("HEAD"),cancellable=true,remap=false)
    private void boot(CallbackInfo ci){if(NativeAssetsPacingCheck.ENABLED){NativeAssetsPacingCheck.register();ci.cancel();}}
}

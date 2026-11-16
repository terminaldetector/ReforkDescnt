package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.CubicClientGate;
import com.terminaldetector.drmd.gate.NativeRagdollVolumeCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CubicClientGate.class, remap = false)
public class RagdollVolumeBootstrapProbe {
    @Inject(method = "onInitializeClient", at = @At("HEAD"), cancellable = true, remap = false)
    private void boot(CallbackInfo callback) {
        if (NativeRagdollVolumeCheck.ENABLED) {
            NativeRagdollVolumeCheck.register();
            callback.cancel();
        }
    }
}

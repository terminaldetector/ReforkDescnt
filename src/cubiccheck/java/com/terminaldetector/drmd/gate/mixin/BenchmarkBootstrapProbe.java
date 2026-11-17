package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.CubicClientGate;
import com.terminaldetector.drmd.gate.NativeBenchmarkRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Own bootstrap: register before another isolated gate cancels the ordinary initializer. */
@Mixin(value=CubicClientGate.class,priority=2000,remap=false)
public class BenchmarkBootstrapProbe {
    @Inject(method="onInitializeClient",at=@At("HEAD"),cancellable=true,remap=false)
    private void benchmark(CallbackInfo ci){
        if(!NativeBenchmarkRecorder.ENABLED)return;
        NativeBenchmarkRecorder.register();
        if(NativeBenchmarkRecorder.standalone())ci.cancel();
    }
}

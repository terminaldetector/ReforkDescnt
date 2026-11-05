package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.gate.NativeScheduleBudgetCheck;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="dev.fallingcloud.bettercubicchunks.server.CubicServerLevel",remap=false)
public abstract class CubicScheduleBudgetProbe {
    @Inject(method="tick()V",at=@At("RETURN"),remap=false)
    private void drmd$realSchedulerState(CallbackInfo ci){NativeScheduleBudgetCheck.observed(this);}
}

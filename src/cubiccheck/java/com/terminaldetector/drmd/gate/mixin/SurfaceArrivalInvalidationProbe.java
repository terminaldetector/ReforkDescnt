package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSurfaceArrivalCheck;
import com.terminaldetector.drmd.world.store.SurfaceStreamer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SurfaceStreamer.class,remap=false)
public abstract class SurfaceArrivalInvalidationProbe {
    @Inject(method="invalidate",at=@At("HEAD"),remap=false)
    private static void invalidated(long key,CallbackInfo ci){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.invalidated(key);}
}

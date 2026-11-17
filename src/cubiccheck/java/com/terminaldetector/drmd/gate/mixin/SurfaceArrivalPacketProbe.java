package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.planet.PlanetClientState;
import com.terminaldetector.drmd.gate.NativeSurfaceArrivalCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlanetClientState.class,remap=false)
public abstract class SurfaceArrivalPacketProbe {
    @Inject(method="acceptSection",at=@At("RETURN"),remap=false)
    private void arrival(long key,byte[] bytes,CallbackInfo ci){if(NativeSurfaceArrivalCheck.ENABLED)NativeSurfaceArrivalCheck.accepted(key,bytes);}
}

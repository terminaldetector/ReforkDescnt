package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.client.planet.PlanetClientState;
import com.terminaldetector.drmd.gate.NativeSurfaceIngestCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlanetClientState.class,remap=false)
public abstract class SurfaceIngestPacketProbe {
    @Inject(method="acceptSection",at=@At("RETURN"),remap=false)
    private void accepted(long key,byte[] bytes,CallbackInfo ci){if(NativeSurfaceIngestCheck.ENABLED)NativeSurfaceIngestCheck.acceptedSection(key,bytes);}
}

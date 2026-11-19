package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSkyLayersCheck;
import com.terminaldetector.drmd.client.planet.HorizonShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=HorizonShader.class,remap=false)
public class SkyHorizonUniformProbe {
    @Inject(method="position",at=@At("HEAD"),remap=false)
    private static void position(float x,float y,float z,float inner,float reach,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.horizonUniform(x,y,z,inner,reach);com.terminaldetector.drmd.gate.NativePs1AtmosphereCheck.input(inner,reach);}
}

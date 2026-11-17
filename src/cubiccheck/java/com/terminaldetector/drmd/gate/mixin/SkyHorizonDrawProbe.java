package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSkyLayersCheck;
import com.terminaldetector.drmd.client.planet.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=HorizonVertexBuffer.class,remap=false)
public class SkyHorizonDrawProbe {
    @Inject(method="prepare",at=@At("HEAD"),remap=false)
    private static void prepare(PlanetSurfaceMesh mesh,CallbackInfoReturnable<Boolean> ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.prepare(mesh);}
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private static void before(Matrix4f view,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.horizon(true,view);}
    @Inject(method="draw",at=@At("RETURN"),remap=false)
    private static void after(Matrix4f view,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.horizon(false,view);}
}

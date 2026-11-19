package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSkyLayersCheck;
import com.terminaldetector.drmd.client.sky.OrbitalBeltSkyRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=OrbitalBeltSkyRenderer.class,remap=false)
public class SkyEnvelopeDrawProbe {
    @Inject(method="paintOblivionEnvelope",at=@At("HEAD"),remap=false)
    private static void before(Camera camera,Matrix4f mat,double y,float tick,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.orbitalScope(true);}
    @Inject(method="paintOblivionEnvelope",at=@At("RETURN"),remap=false)
    private static void after(Camera camera,Matrix4f mat,double y,float tick,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.orbitalScope(false);}
    @Inject(method="quad",at=@At("HEAD"),remap=false)
    private static void quad(BufferBuilder buffer,Matrix4f mat,float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float r,float g,float b,float a,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.envelopeQuad(mat,new float[]{x0,y0,z0,x1,y1,z1,x2,y2,z2,x3,y3,z3},r,g,b,a);}
}

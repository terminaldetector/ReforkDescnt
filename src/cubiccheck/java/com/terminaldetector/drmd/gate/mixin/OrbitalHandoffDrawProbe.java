package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeOrbitalMaskCheck;
import com.terminaldetector.drmd.client.planet.*;
import net.minecraft.client.gl.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=HorizonVertexBuffer.class,remap=false)
public class OrbitalHandoffDrawProbe {
    @Shadow private static VertexBuffer buffer;
    @Shadow private static PlanetSurfaceMesh uploaded;
    @Inject(method="draw",at=@At("RETURN"),remap=false)
    private static void colour(Matrix4f view,CallbackInfo ci){if(NativeOrbitalMaskCheck.ENABLED)NativeOrbitalMaskCheck.draw(uploaded,buffer.getVertexFormat(),HorizonShader.program(),false);}
    @Inject(method="drawSemantic",at=@At("RETURN"),remap=false)
    private static void semantic(Matrix4f view,Matrix4f projection,ShaderProgram program,CallbackInfo ci){if(NativeOrbitalMaskCheck.ENABLED)NativeOrbitalMaskCheck.draw(uploaded,buffer.getVertexFormat(),program,true);}
}

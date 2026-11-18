package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeMobGeometryCheck;
import net.minecraft.client.gl.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(VertexBuffer.class)
public class MobGeometryBufferProbe {
    @Inject(method="drawInternal(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V",at=@At("HEAD"))
    private void before(Matrix4f view,Matrix4f projection,ShaderProgram shader,CallbackInfo ci){if(NativeMobGeometryCheck.ENABLED)NativeMobGeometryCheck.draw((VertexBuffer)(Object)this,shader,true);}
    @Inject(method="drawInternal(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V",at=@At("RETURN"))
    private void after(Matrix4f view,Matrix4f projection,ShaderProgram shader,CallbackInfo ci){if(NativeMobGeometryCheck.ENABLED)NativeMobGeometryCheck.draw((VertexBuffer)(Object)this,shader,false);}
}

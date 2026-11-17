package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeSkyLayersCheck;
import net.minecraft.client.render.*;
import net.minecraft.client.gl.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WorldRenderer.class)
public class SkyVanillaDrawProbe {
    @Inject(method="renderSky",at=@At("HEAD"))
    private void before(Matrix4f view,Matrix4f projection,float tick,Camera camera,boolean fog,Runnable callback,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.sky(true,view,projection);}
    @Inject(method="renderSky",at=@At("RETURN"))
    private void after(Matrix4f view,Matrix4f projection,float tick,Camera camera,boolean fog,Runnable callback,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.sky(false,view,projection);}
    @Redirect(method="renderSky",at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/VertexBuffer;draw(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V",ordinal=0))
    private void bright(VertexBuffer buffer,Matrix4f view,Matrix4f projection,ShaderProgram program){
        if(NativeSkyLayersCheck.ENABLED){NativeSkyLayersCheck.brightMatrices(view,projection,program);NativeSkyLayersCheck.bright(true);}
        buffer.draw(view,projection,program);
        if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.bright(false);
    }
    @Inject(method="renderSky",at={
        @At(value="INVOKE",target="Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",ordinal=1,shift=At.Shift.BEFORE),
        @At(value="INVOKE",target="Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",ordinal=2,shift=At.Shift.BEFORE),
        @At(value="INVOKE",target="Lnet/minecraft/client/gl/VertexBuffer;draw(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V",ordinal=1,shift=At.Shift.BEFORE)},require=3)
    private void celestialBefore(Matrix4f view,Matrix4f projection,float tick,Camera camera,boolean fog,Runnable callback,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.celestial(true);}
    @Inject(method="renderSky",at={
        @At(value="INVOKE",target="Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",ordinal=1,shift=At.Shift.AFTER),
        @At(value="INVOKE",target="Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",ordinal=2,shift=At.Shift.AFTER),
        @At(value="INVOKE",target="Lnet/minecraft/client/gl/VertexBuffer;draw(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V",ordinal=1,shift=At.Shift.AFTER)},require=3)
    private void celestialAfter(Matrix4f view,Matrix4f projection,float tick,Camera camera,boolean fog,Runnable callback,CallbackInfo ci){if(NativeSkyLayersCheck.ENABLED)NativeSkyLayersCheck.celestial(false);}
}

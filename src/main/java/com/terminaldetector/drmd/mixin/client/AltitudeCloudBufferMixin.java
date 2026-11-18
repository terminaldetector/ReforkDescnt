package com.terminaldetector.drmd.mixin.client;
import com.terminaldetector.drmd.client.cloud.AltitudeCloudRenderer;
import net.minecraft.client.gl.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Replace only a known main-view cloud draw; delegated draws run the original VertexBuffer path. */
@Mixin(value=VertexBuffer.class,priority=2000)
public class AltitudeCloudBufferMixin {
    @Inject(method="draw(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V",at=@At("HEAD"),cancellable=true)
    private void altitude(Matrix4f view,Matrix4f projection,ShaderProgram shader,CallbackInfo ci){if(AltitudeCloudRenderer.drawCloud((VertexBuffer)(Object)this,view,projection,shader))ci.cancel();}
}

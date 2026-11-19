package com.terminaldetector.drmd.mixin.client;
import com.terminaldetector.drmd.client.sky.Ps1SkyBackground;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WorldRenderer.class)
public class Ps1SkyBackgroundMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V",shift=At.Shift.BEFORE))
    private void beforeExternalLods(RenderTickCounter counter,boolean outlines,Camera camera,GameRenderer renderer,LightmapTextureManager lightmap,Matrix4f position,Matrix4f projection,CallbackInfo ci){
        com.terminaldetector.drmd.client.sky.OrbitalBeltSkyRenderer.beforeExternalLods(counter,camera,position);
    }
    @Inject(method="renderSky",at=@At("HEAD"))
    private void before(Matrix4f view,Matrix4f projection,float delta,Camera camera,boolean foggy,Runnable setupFog,CallbackInfo ci){Ps1SkyBackground.draw(view,projection,delta,camera,foggy);}
}

package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.presentation.ScenePresentation;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wrap only the world call: finally restores original targets before vanilla hand/HUD drawing. */
@Mixin(GameRenderer.class)
public class PresentationRendererMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void start(RenderTickCounter counter,boolean tick,CallbackInfo ci){ScenePresentation.frameStarted();}
    @Redirect(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void world(WorldRenderer renderer,RenderTickCounter counter,boolean outlines,Camera camera,GameRenderer game,LightmapTextureManager lightmap,Matrix4f position,Matrix4f projection){
        try(var frame=ScenePresentation.begin(renderer)){
            renderer.render(counter,outlines,camera,game,lightmap,position,projection);
            if(frame!=null)frame.complete();
        }
    }
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V"))
    private void outlines(WorldRenderer renderer){if(!ScenePresentation.outlinesPresented())renderer.drawEntityOutlinesFramebuffer();}
}

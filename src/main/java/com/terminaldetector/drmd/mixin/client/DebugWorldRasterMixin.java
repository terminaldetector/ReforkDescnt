package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.presentation.ScenePresentation;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Retain wireframe 3D geometry while filling the outline/Fabulous fullscreen composites. */
@Mixin(WorldRenderer.class)
public class DebugWorldRasterMixin {
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/PostEffectProcessor;render(F)V"),require=2)
    private void filledComposite(PostEffectProcessor processor,float tickDelta){
        try(var raster=ScenePresentation.fillDebugPostprocess()){processor.render(tickDelta);}
    }
}

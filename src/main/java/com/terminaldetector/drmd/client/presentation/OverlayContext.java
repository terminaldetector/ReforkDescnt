package com.terminaldetector.drmd.client.presentation;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;

/** Fabric reuses its context for nested mirror/portal renders. Snapshot every frame value before
 * deferring, including matrices whose original stack will be popped by the world renderer. The
 * camera is restored by the nested view's finally block before this same-frame overlay flush. */
record OverlayContext(WorldRenderer worldRenderer,MatrixStack matrixStack,RenderTickCounter tickCounter,
                      boolean blockOutlines,Camera camera,GameRenderer gameRenderer,LightmapTextureManager lightmapTextureManager,
                      Matrix4f projectionMatrix,Matrix4f positionMatrix,ClientWorld world,Profiler profiler,
                      boolean advancedTranslucency,VertexConsumerProvider consumers,Frustum frustum) implements WorldRenderContext {
    static OverlayContext copy(WorldRenderContext source){
        MatrixStack matrices=null;
        if(source.matrixStack()!=null){
            matrices=new MatrixStack();matrices.peek().getPositionMatrix().set(source.matrixStack().peek().getPositionMatrix());
            matrices.peek().getNormalMatrix().set(source.matrixStack().peek().getNormalMatrix());
        }
        return new OverlayContext(source.worldRenderer(),matrices,source.tickCounter(),source.blockOutlines(),source.camera(),
                source.gameRenderer(),source.lightmapTextureManager(),new Matrix4f(source.projectionMatrix()),new Matrix4f(source.positionMatrix()),
                source.world(),source.profiler(),source.advancedTranslucency(),source.consumers(),source.frustum());
    }
}

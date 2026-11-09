package com.terminaldetector.drmd.client.presentation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.mixin.client.WorldViewTargetsAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import java.io.IOException;

/** Private low-resolution equivalents; never resize or close the world's original targets. */
final class SceneTargets implements AutoCloseable {
    final PresentationSize size;
    final boolean fabulous, outlined;
    SimpleFramebuffer scene;
    PostEffectProcessor transparency, outlines;

    SceneTargets(MinecraftClient client, PresentationSize size, boolean fabulous, boolean outlined) throws IOException {
        this.size=size;this.fabulous=fabulous;this.outlined=outlined;
        try {
            scene=new SimpleFramebuffer(size.width(),size.height(),true,MinecraftClient.IS_SYSTEM_MAC);
            scene.setTexFilter(org.lwjgl.opengl.GL11.GL_NEAREST);
            if(fabulous)transparency=processor(client,"transparency");
            if(outlined)outlines=processor(client,"entity_outline");
        } catch(IOException|RuntimeException failure){close();throw failure;}
    }
    private PostEffectProcessor processor(MinecraftClient client,String name)throws IOException {
        var p=new PostEffectProcessor(client.getTextureManager(),client.getResourceManager(),scene,Identifier.ofVanilla("shaders/post/"+name+".json"));
        try{p.setupDimensions(size.width(),size.height());return p;}catch(RuntimeException failure){p.close();throw failure;}
    }
    private Framebuffer target(String name){return transparency==null?null:transparency.getSecondaryTarget(name);}
    Borrow attach(WorldRenderer renderer){
        var a=(WorldViewTargetsAccess)renderer;var saved=new Borrow(a);
        a.drmd$transparency(transparency);a.drmd$outlines(outlines);
        a.drmd$translucent(target("translucent"));a.drmd$entities(target("itemEntity"));a.drmd$particles(target("particles"));
        a.drmd$weather(target("weather"));a.drmd$clouds(target("clouds"));a.drmd$outlineTarget(outlines==null?null:outlines.getSecondaryTarget("final"));
        return saved;
    }
    void compositeOutlines(MinecraftClient client){
        if(outlines==null||client.player==null||client.gameRenderer.isRenderingPanorama())return;
        scene.beginWrite(true);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        outlines.getSecondaryTarget("final").draw(size.width(),size.height(),false);
        RenderSystem.disableBlend();RenderSystem.enableDepthTest();
    }
    @Override public void close(){
        if(transparency!=null){transparency.close();transparency=null;}
        if(outlines!=null){outlines.close();outlines=null;}
        if(scene!=null){scene.delete();scene=null;}
    }
    static final class Borrow implements AutoCloseable {
        private final WorldViewTargetsAccess a;
        private final PostEffectProcessor transparency,outlines;
        private final Framebuffer translucent,entities,particles,weather,clouds,outline;
        Borrow(WorldViewTargetsAccess a){
            this.a=a;transparency=a.drmd$transparency();outlines=a.drmd$outlines();translucent=a.drmd$translucent();entities=a.drmd$entities();
            particles=a.drmd$particles();weather=a.drmd$weather();clouds=a.drmd$clouds();outline=a.drmd$outlineTarget();
        }
        @Override public void close(){
            a.drmd$transparency(transparency);a.drmd$outlines(outlines);a.drmd$translucent(translucent);a.drmd$entities(entities);
            a.drmd$particles(particles);a.drmd$weather(weather);a.drmd$clouds(clouds);a.drmd$outlineTarget(outline);
        }
    }
}

package com.terminaldetector.drmd.client.presentation;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.mixin.client.ViewFramebufferAccess;
import com.terminaldetector.drmd.mixin.client.WorldViewTargetsAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.*;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** One scoped world pass. Native never borrows a target; hand, viewmodels and HUD remain full-res. */
public final class ScenePresentation {
    public record Stats(PresentationProfile profile,int sceneWidth,int sceneHeight,int outputWidth,int outputHeight,
                        int overlayPasses,long frames,long fallbackFrames,boolean inWorldPass){}
    private static SceneTargets targets;
    private static Frame active;
    private static boolean releaseRequested,failed,outlinesPresented;
    private static Object lastWorld;
    private static long frames,fallbackFrames;
    private static Stats last=new Stats(PresentationProfile.HYBRID_NATIVE,0,0,0,0,0,0,0,false);
    private ScenePresentation(){}
    public static Stats stats(){return new Stats(last.profile,last.sceneWidth,last.sceneHeight,last.outputWidth,last.outputHeight,last.overlayPasses,frames,fallbackFrames,active!=null);}
    public static void frameStarted(){outlinesPresented=false;}
    public static boolean outlinesPresented(){return outlinesPresented;}
    /** Actual borrowed low-resolution world only; cleared before any deferred first-person overlays. */
    public static boolean inLowResolutionWorld(){
        return active!=null&&active.profile==PresentationProfile.PS1_HYBRID&&active.scene!=null&&!OffscreenWorldView.busy();
    }
    public static void register(){
        Ps1ColorPass.register();
        Ps1BodyGeometryLayer.register();
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(lastWorld!=c.world||DescentConfig.presentationProfile!=PresentationProfile.PS1_HYBRID){release();lastWorld=c.world;}
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c->release());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            public Identifier getFabricId(){return Identifier.of("drmd","presentation_targets");}
            public void reload(ResourceManager resources){MinecraftClient.getInstance().execute(ScenePresentation::release);}
        });
    }
    private static void release(){
        if(active!=null){releaseRequested=true;return;}
        if(targets!=null){targets.close();targets=null;}failed=false;releaseRequested=false;
    }
    public static Frame begin(WorldRenderer renderer){
        var client=MinecraftClient.getInstance();var profile=DescentConfig.presentationProfile;var output=client.getFramebuffer();
        if(profile==PresentationProfile.HYBRID_NATIVE){
            last=new Stats(profile,output.textureWidth,output.textureHeight,output.textureWidth,output.textureHeight,0,frames,fallbackFrames,false);return null;
        }
        if(client.world==null||client.gameRenderer.isRenderingPanorama()||active!=null||OffscreenWorldView.busy())return null;
        if(client.getWindow().getFramebufferWidth()<=0||client.getWindow().getFramebufferHeight()<=0)return null;
        if(profile==PresentationProfile.RENDER_DEBUG){active=new Frame(client,output,null,null,profile);return active;}
        var size=PresentationSize.fit(output.textureWidth,output.textureHeight,DescentConfig.ps1SceneHeight,RenderSystem.maxSupportedTextureSize());
        if(!size.valid()||failed){fallbackFrames++;return null;}
        var access=(WorldViewTargetsAccess)renderer;boolean fabulous=access.drmd$transparency()!=null,outlined=access.drmd$outlines()!=null;
        SceneTargets.Borrow borrowed=null;
        try{
            if(targets==null||!targets.size.equals(size)||targets.fabulous!=fabulous||targets.outlined!=outlined){release();targets=new SceneTargets(client,size,fabulous,outlined);}
            borrowed=targets.attach(renderer);
            ((ViewFramebufferAccess)client).drmd$framebuffer(targets.scene);
            targets.scene.setClearColor(0,0,0,1);targets.scene.clear(MinecraftClient.IS_SYSTEM_MAC);targets.scene.beginWrite(true);
            active=new Frame(client,output,targets,borrowed,profile);return active;
        }catch(IOException|RuntimeException failure){
            if(borrowed!=null)borrowed.close();
            ((ViewFramebufferAccess)client).drmd$framebuffer(output);output.beginWrite(true);release();failed=true;fallbackFrames++;
            DescentMod.LOGGER.error("PS1 scene target unavailable; rendering this profile at native resolution until toggle/reload",failure);return null;
        }
    }
    /** Queue only first-person callbacks. World objects, mirrors and portals remain in the scene. */
    public static boolean deferOverlay(WorldRenderContext context,Consumer<WorldRenderContext> render){
        if(active==null||OffscreenWorldView.busy())return false;
        active.overlays.add(new Overlay(OverlayContext.copy(context),render,new Matrix4f(RenderSystem.getProjectionMatrix()),
                new Matrix4f(RenderSystem.getModelViewMatrix()),RenderSystem.getVertexSorting(),GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),GL11.glIsEnabled(GL11.GL_CULL_FACE),GL11.glIsEnabled(GL11.GL_BLEND)));return true;
    }
    private static void depth(boolean enabled){if(enabled)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();}
    private static void cull(boolean enabled){if(enabled)RenderSystem.enableCull();else RenderSystem.disableCull();}
    private static void blend(boolean enabled){if(enabled)RenderSystem.enableBlend();else RenderSystem.disableBlend();}
    /** Fullscreen postprocessing composites pixels, not world polygons. Native/PS1 do not enter. */
    public static DebugPostprocess fillDebugPostprocess(){
        return active!=null&&active.profile==PresentationProfile.RENDER_DEBUG?new DebugPostprocess():null;
    }
    public static final class DebugPostprocess implements AutoCloseable{
        private final int[] modes=new int[2];
        private DebugPostprocess(){GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,modes);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);}
        @Override public void close(){
            if(modes[0]==modes[1])GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,modes[0]);
            else{GL11.glPolygonMode(GL11.GL_FRONT,modes[0]);GL11.glPolygonMode(GL11.GL_BACK,modes[1]);}
        }
    }
    private record Overlay(OverlayContext context,Consumer<WorldRenderContext> render,Matrix4f projection,Matrix4f modelView,VertexSorter sorting,
                           boolean depthTest,boolean depthWrite,boolean culling,boolean blending){
        void draw(){
            withState(()->render.accept(context));
        }
        void flush(VertexConsumerProvider.Immediate immediate){
            withState(immediate::draw);
        }
        private void withState(Runnable action){
            var before=new Matrix4f(RenderSystem.getProjectionMatrix());var sortingBefore=RenderSystem.getVertexSorting();var shader=RenderSystem.getShader();
            boolean oldDepth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),oldWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),oldCull=GL11.glIsEnabled(GL11.GL_CULL_FACE),oldBlend=GL11.glIsEnabled(GL11.GL_BLEND);
            var stack=RenderSystem.getModelViewStack();stack.pushMatrix();
            try{
                stack.set(modelView);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
                depth(depthTest);RenderSystem.depthMask(depthWrite);cull(culling);blend(blending);
                action.run();
            }finally{stack.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(before,sortingBefore);RenderSystem.setShader(()->shader);
                depth(oldDepth);RenderSystem.depthMask(oldWrite);cull(oldCull);blend(oldBlend);}
        }
    }
    public static final class Frame implements AutoCloseable{
        private final MinecraftClient client;
        private final Framebuffer output;
        private final SceneTargets scene;
        private final SceneTargets.Borrow borrowed;
        private final PresentationProfile profile;
        private final int[] polygonModes=new int[2];
        private final List<Overlay> overlays=new ArrayList<>();
        private boolean complete;
        private Frame(MinecraftClient client,Framebuffer output,SceneTargets scene,SceneTargets.Borrow borrowed,PresentationProfile profile){
            this.client=client;this.output=output;this.scene=scene;this.borrowed=borrowed;this.profile=profile;
            GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,polygonModes);
            if(profile==PresentationProfile.RENDER_DEBUG)GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_LINE);
        }
        public void complete(){complete=true;}
        @Override public void close(){
            try{
                if(polygonModes[0]==polygonModes[1])GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,polygonModes[0]);
                else{GL11.glPolygonMode(GL11.GL_FRONT,polygonModes[0]);GL11.glPolygonMode(GL11.GL_BACK,polygonModes[1]);}
                try{if(complete&&scene!=null)scene.compositeOutlines(client);}
                finally{
                    if(borrowed!=null)borrowed.close();((ViewFramebufferAccess)client).drmd$framebuffer(output);output.beginWrite(true);active=null;
                }
                if(!complete)return;
                if(scene!=null){
                    boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);if(scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    try{
                        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,scene.scene.fbo);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,output.fbo);
                        var colour=new Ps1ColorOptions(DescentConfig.ps1ColorLevels,DescentConfig.ps1DitherStrength);
                        GlStateManager._glBlitFrameBuffer(0,0,scene.size.width(),scene.size.height(),0,0,output.textureWidth,output.textureHeight,
                                GL11.GL_DEPTH_BUFFER_BIT|(colour.enabled()?0:GL11.GL_COLOR_BUFFER_BIT),GL11.GL_NEAREST);
                        if(colour.enabled()&&!Ps1ColorPass.draw(scene.scene,output,colour)){
                            fallbackFrames++;
                            GlStateManager._glBlitFrameBuffer(0,0,scene.size.width(),scene.size.height(),0,0,output.textureWidth,output.textureHeight,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
                        }
                    }finally{if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);output.beginWrite(true);}
                    outlinesPresented=true;
                }
                for(var overlay:overlays)overlay.draw();
                // Native world callbacks queue weapon vertices, draw the immediate cockpit,
                // then flush the shared provider. Preserve that ordering after the upscale too.
                var flushed=Collections.newSetFromMap(new IdentityHashMap<VertexConsumerProvider.Immediate,Boolean>());
                for(var overlay:overlays)if(overlay.context.consumers() instanceof VertexConsumerProvider.Immediate immediate&&flushed.add(immediate))overlay.flush(immediate);
                frames++;last=new Stats(profile,scene==null?output.textureWidth:scene.size.width(),scene==null?output.textureHeight:scene.size.height(),
                        output.textureWidth,output.textureHeight,overlays.size(),frames,fallbackFrames,false);
            }finally{active=null;overlays.clear();if(releaseRequested)release();}
        }
    }
}

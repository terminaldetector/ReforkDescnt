package com.terminaldetector.drmd.client.cloud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;

/** Private RG32F true ray/opacity target. Never attaches to or clears a borrowed world target. */
final class FarFieldOcclusionTarget implements AutoCloseable {
    final int width,height;
    int framebuffer,texture,depth;
    FarFieldOcclusionTarget(int width,int height){
        if(!AltitudeCloudPolicy.acceptsTarget(width,height,RenderSystem.maxSupportedTextureSize()))throw new IllegalArgumentException("Cloud witness target exceeds96MiB/texture budget");
        this.width=width;this.height=height;
        try(var state=new State()){
            framebuffer=GL30.glGenFramebuffers();texture=GL11.glGenTextures();depth=GL30.glGenRenderbuffers();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);GlStateManager._bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RG32F,width,height,0,GL30.GL_RG,GL11.GL_FLOAT,(ByteBuffer)null);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL14.GL_DEPTH_COMPONENT24,width,height);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int status=GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);if(status!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Cloud witness FBO incomplete: "+status);
        }catch(RuntimeException failure){close();throw failure;}
    }
    void begin(){
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);RenderSystem.viewport(0,0,width,height);RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LESS);RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.disableCull();GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
        GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{AltitudeCloudPolicy.MAX_RAY_DISTANCE,0,0,0});GL30.glClearBufferfv(GL11.GL_DEPTH,0,new float[]{1});
    }
    @Override public void close(){if(framebuffer!=0){GL30.glDeleteFramebuffers(framebuffer);framebuffer=0;}if(texture!=0){GL11.glDeleteTextures(texture);texture=0;}if(depth!=0){GL30.glDeleteRenderbuffers(depth);depth=0;}}

    /** All state touched by allocation, semantic draw or the guarded cloud draw. */
    static final class State implements AutoCloseable {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),renderbuffer=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),activeBinding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final int vertexArray=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int[] viewport=new int[4],scissorBox=new int[4],polygon=new int[2],colours=new int[4];
        final boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),depthTest=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND);
        final CloudRenderCompatibility.SamplerApi samplerApi=CloudRenderCompatibility.samplerApi(GL.getCapabilities().OpenGL33,GL.getCapabilities().GL_ARB_sampler_objects);
        final ShaderProgram shader=RenderSystem.getShader();final float[] modulator=RenderSystem.getShaderColor().clone();
        final int[] bindings=new int[2],samplerBindings=new int[2];
        State(){
            GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,polygon);GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK,colours);
            if(polygon[0]!=polygon[1]&&(GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)&GL32.GL_CONTEXT_CORE_PROFILE_BIT)!=0)throw new IllegalStateException("Unsupported split core polygon mode");
            for(int unit=0;unit<2;unit++){GlStateManager._activeTexture(GL13.GL_TEXTURE0+unit);bindings[unit]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);samplerBindings[unit]=samplerApi==CloudRenderCompatibility.SamplerApi.NONE?0:GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);}GlStateManager._activeTexture(active);
        }
        private void bindSampler(int unit,int value){if(samplerApi==CloudRenderCompatibility.SamplerApi.CORE_33)GL33.glBindSampler(unit,value);else if(samplerApi==CloudRenderCompatibility.SamplerApi.ARB)ARBSamplerObjects.glBindSampler(unit,value);}
        void plainSampler(int unit){bindSampler(unit,0);}
        @Override public void close(){
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,renderbuffer);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            GlStateManager._scissorBox(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);if(scissor)RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);else RenderSystem.disableScissor();
            RenderSystem.depthFunc(depthFunc);RenderSystem.depthMask(depthWrite);if(depthTest)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);RenderSystem.colorMask(colours[0]!=0,colours[1]!=0,colours[2]!=0,colours[3]!=0);
            if(polygon[0]==polygon[1])GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,polygon[0]);else{GL11.glPolygonMode(GL11.GL_FRONT,polygon[0]);GL11.glPolygonMode(GL11.GL_BACK,polygon[1]);}
            for(int unit=0;unit<2;unit++){GlStateManager._activeTexture(GL13.GL_TEXTURE0+unit);GlStateManager._bindTexture(bindings[unit]);bindSampler(unit,samplerBindings[unit]);}GlStateManager._activeTexture(active);GlStateManager._bindTexture(activeBinding);
            GlStateManager._glBindVertexArray(vertexArray);
            RenderSystem.setShaderColor(modulator[0],modulator[1],modulator[2],modulator[3]);RenderSystem.setShader(()->shader);GlStateManager._glUseProgram(program);
        }
    }
}

package com.terminaldetector.drmd.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** Sample the reflected image at the current screen pixel, masked by real depth-tested faces. */
public final class SurfaceComposite {
    private static ShaderProgram shader;
    private SurfaceComposite() {}
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(c->c.register(Identifier.of("drmd","surface_composite"),
                VertexFormats.POSITION,p->shader=p));
    }
    public static void draw(Framebuffer target,Matrix4f projection,Matrix4f view,Vec3d camera,
            List<PortalTransform.Vec3[]> apertures) {
        if(shader==null)return;
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),
                blend=GL11.glIsEnabled(GL11.GL_BLEND),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int function=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int texture=RenderSystem.getShaderTexture(0);
        var previous=RenderSystem.getShader();
        try {
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);RenderSystem.disableCull();RenderSystem.disableBlend();
            RenderSystem.setShader(()->shader);
            shader.getUniform("SurfaceMatrix").set(new Matrix4f(projection).mul(view));
            shader.getUniform("SurfaceSize").set((float)target.textureWidth,(float)target.textureHeight);
            RenderSystem.setShaderTexture(0,target.getColorAttachment());
            var buffer=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION);
            for(var polygon:apertures)for(var p:polygon)
                buffer.vertex((float)(p.x()-camera.x),(float)(p.y()-camera.y),(float)(p.z()-camera.z));
            var built=buffer.endNullable();if(built!=null)BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.depthFunc(function);RenderSystem.depthMask(write);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0,texture);
            RenderSystem.setShader(()->previous);
        }
    }
}
